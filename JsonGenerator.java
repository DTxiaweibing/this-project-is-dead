package com.example.jsongenerator.utils;

import android.util.Log;

import com.example.jsongenerator.model.GitHubFile;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class JsonGenerator {
    private static final String TAG = "JsonGenerator";

    // 使用 GitHubFile 对象（保留兼容）
    public static String generateJson(GitHubFile selectedFile, String fileName, long fileSize,
                                      String md5, String sha256, String apkVersion,
                                      String updateLog, String githubRepo) {
        return generateJson(selectedFile.getDownloadUrl(), fileName, fileSize, md5, sha256, apkVersion, updateLog, githubRepo);
    }

    // 直接传入 downloadUrl（核心方法）
    public static String generateJson(String downloadUrl, String fileName, long fileSize,
                                      String md5, String sha256, String apkVersion,
                                      String updateLog, String githubRepo) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA);
            String timeWithoutZone = sdf.format(new Date());
            String time = timeWithoutZone + "+08:00";

            JSONObject root = new JSONObject();
            root.put("config_version", apkVersion);

            JSONObject fileInfo = new JSONObject();
            fileInfo.put("version", apkVersion);
            fileInfo.put("name", fileName);
            fileInfo.put("size", fileSize);
            fileInfo.put("md5", md5);
            fileInfo.put("sha256", sha256);
            fileInfo.put("download_url", downloadUrl);  // 唯一下载链接
            fileInfo.put("github_repo", githubRepo);
            root.put("file_info", fileInfo);

            root.put("update_log", updateLog);

            JSONObject metadata = new JSONObject();
            metadata.put("generated_at", time);
            metadata.put("generator_version", "1.0.0");
            root.put("metadata", metadata);

            return root.toString(4).replace("\\/", "/");
        } catch (Exception e) {
            Log.e(TAG, "生成JSON失败", e);
            return null;
        }
    }
}
