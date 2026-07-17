package com.example.jsongenerator.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GitHubUploader {
    private static final String TAG = "GitHubUploader";
    private static final int MAX_RETRIES = 3;
    private static final int CONCURRENT_UPLOADS = 3; // 同时上传的文件数

    private OkHttpClient client = new OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build();

    private ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_UPLOADS);

    public interface UploadCallback {
        void onSuccess(String message);
        void onFailure(String error);
        void onProgress(String status);
    }

    // ==================== 公共异步方法（供单个文件上传调用）====================
    /**
     * 异步上传单个文件（兼容原有接口）
     */
    public void uploadFile(final String token, final String owner, final String repo,
                           final String remotePath, final byte[] content,
                           final String commitMessage, final UploadCallback callback) {
        executor.execute(new Runnable() {
                @Override
                public void run() {
                    String result = uploadFileSync(token, owner, repo, remotePath, content, commitMessage);
                    if (result == null) {
                        callback.onSuccess("上传成功: " + remotePath);
                    } else {
                        callback.onFailure("上传失败 " + remotePath + ": " + result);
                    }
                }
            });
    }

    // ==================== 内部同步上传方法（带重试）====================
    private String uploadFileSync(String token, String owner, String repo,
                                  String remotePath, byte[] content,
                                  String commitMessage) {
        int retry = 0;
        while (retry < MAX_RETRIES) {
            try {
                String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + remotePath;
                String base64Content = Base64.encodeToString(content, Base64.NO_WRAP);

                // 获取最新 SHA
                String sha = getFileSha(token, owner, repo, remotePath);

                JSONObject requestBody = new JSONObject();
                requestBody.put("message", commitMessage);
                requestBody.put("content", base64Content);
                if (sha != null) {
                    requestBody.put("sha", sha);
                }

                Request request = new Request.Builder()
                    .url(apiUrl)
                    .header("Authorization", "token " + token)
                    .header("Content-Type", "application/json")
                    .put(RequestBody.create(
                             MediaType.parse("application/json; charset=utf-8"),
                             requestBody.toString()))
                    .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    return null; // 成功
                }

                int code = response.code();
                String errorBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "HTTP " + code + " for " + remotePath + ": " + errorBody);

                // 判断是否可重试
                boolean shouldRetry = (code == 409 || code >= 500 || code == 403);
                if (shouldRetry && retry < MAX_RETRIES - 1) {
                    retry++;
                    if (code == 403) Thread.sleep(5000); // 限流等待
                    else Thread.sleep(2000);
                    continue;
                }
                return "HTTP " + code + (errorBody.isEmpty() ? "" : ": " + errorBody);
            } catch (Exception e) {
                Log.e(TAG, "上传异常: " + remotePath, e);
                if (retry < MAX_RETRIES - 1) {
                    retry++;
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    continue;
                }
                return e.getMessage();
            }
        }
        return "未知错误";
    }

    // ==================== 文件夹上传（最终版，无死锁）====================
    public void uploadFolder(final Context context, final String token, final String owner,
                             final String repo, final Uri folderUri, final String remoteBasePath,
                             final String commitMessage, final UploadCallback callback) {
        DocumentFile folder = DocumentFile.fromTreeUri(context, folderUri);
        if (folder == null || !folder.isDirectory()) {
            callback.onFailure("无效的文件夹");
            return;
        }

        // 收集所有文件
        final List<DocumentFile> allFiles = new ArrayList<>();
        collectAllFiles(folder, allFiles);
        final int total = allFiles.size();
        if (total == 0) {
            callback.onFailure("文件夹为空");
            return;
        }

        callback.onProgress("开始上传，共 " + total + " 个文件");

        final CountDownLatch latch = new CountDownLatch(total);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger failCount = new AtomicInteger(0);
        final List<String> failedDetails = Collections.synchronizedList(new ArrayList<>());

        for (final DocumentFile file : allFiles) {
            executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        InputStream is = null;
                        String remotePath = (remoteBasePath.isEmpty() ? "" : remoteBasePath + "/") + file.getName();
                        String result = null;
                        try {
                            is = context.getContentResolver().openInputStream(file.getUri());
                            if (is == null) throw new IOException("无法打开文件");
                            byte[] content = readAllBytes(is);
                            result = uploadFileSync(token, owner, repo, remotePath, content, commitMessage);
                        } catch (Exception e) {
                            result = e.getMessage();
                        } finally {
                            if (is != null) try { is.close(); } catch (IOException ignored) {}
                        }

                        if (result == null) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                            failedDetails.add(file.getName() + ": " + result);
                        }

                        int done = total - (int) latch.getCount();
                        callback.onProgress(String.format("进度: %d/%d (成功: %d, 失败: %d)",
                                                          done, total, successCount.get(), failCount.get()));

                        latch.countDown();
                    }
                });
        }

        // 等待所有任务完成（在独立线程中等待，避免阻塞UI）
        new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    final int success = successCount.get();
                    final int fail = failCount.get();

                    if (fail == 0) {
                        callback.onSuccess(String.format("恭喜！所有 %d 个文件上传成功！", total));
                    } else {
                        StringBuilder msg = new StringBuilder();
                        msg.append(String.format("上传完成：成功 %d 个，失败 %d 个。", success, fail));
                        if (!failedDetails.isEmpty()) {
                            msg.append("\n失败文件：");
                            for (int i = 0; i < Math.min(failedDetails.size(), 5); i++) {
                                msg.append("\n- ").append(failedDetails.get(i));
                            }
                            if (failedDetails.size() > 5) {
                                msg.append("\n... 等共 ").append(failedDetails.size()).append(" 个");
                            }
                        }
                        callback.onFailure(msg.toString());
                    }
                }
            }).start();
    }

    // ==================== 辅助方法 ====================
    private void collectAllFiles(DocumentFile folder, List<DocumentFile> list) {
        for (DocumentFile file : folder.listFiles()) {
            if (file.isDirectory()) {
                collectAllFiles(file, list);
            } else {
                list.add(file);
            }
        }
    }

    private String getFileSha(String token, String owner, String repo, String path) {
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path;
        Request request = new Request.Builder()
            .url(apiUrl)
            .header("Authorization", "token " + token)
            .get()
            .build();
        int retry = 0;
        while (retry < 2) {
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    return json.getString("sha");
                } else if (response.code() == 404) {
                    return null; // 文件不存在
                }
            } catch (Exception e) {
                Log.w(TAG, "getFileSha失败", e);
            }
            retry++;
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        return null;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = is.read(data)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
