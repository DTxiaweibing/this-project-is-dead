package com.example.jsongenerator;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.jsongenerator.adapter.GitHubFileListAdapter;
import com.example.jsongenerator.manager.UpdateLogTableManager;
import com.example.jsongenerator.model.GitHubFile;
import com.example.jsongenerator.utils.ApkVersionHelper;
import com.example.jsongenerator.utils.FileHelper;
import com.example.jsongenerator.utils.GitHubUploader;
import com.example.jsongenerator.utils.JsonGenerator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSIONS = 1001;
    private static final int REQUEST_SELECT_BASE_FILE = 1002;
    private static final int REQUEST_SELECT_TOKEN_FILE = 1003;
    private static final int REQUEST_SAVE_JSON = 1004;
    private static final int REQUEST_UPLOAD_FILE = 2001;
    private static final int REQUEST_UPLOAD_FOLDER = 2002;

    private static final String PREFS_NAME = "WebUrlPrefs";
    private static final String KEY_WEB_BASE_URL = "web_base_url";

    private Button btnBrowseFile, btnBrowseToken, btnQuery, btnGenerate, btnSave, btnUpload;
    private TextView tvFileName, tvTokenStatus;
    private EditText etGithubUrl;
    private ListView lvGithubFiles;
    private EditText etJsonPreview;
    private ScrollView scrollJson, scrollUpdate;
    private Button btnTabRepo, btnTabJson, btnTabUpdate;

    private LinearLayout floatingButtonLayout;
    private LinearLayout floatingGenerateButtons, floatingUploadButtons, floatingSaveButtons;
    private View rootLayout;

    private Uri baseFileUri;
    private String baseFileName;
    private String baseFileInternalPath;
    private String token;
    private List<GitHubFile> githubFileList = new ArrayList<>();
    private List<GitHubFile> allGithubFiles = new ArrayList<>();
    private GitHubFileListAdapter adapter;
    private UpdateLogTableManager updateLogManager;
    private String generatedJson;
    private OkHttpClient client = new OkHttpClient();
    private GitHubUploader githubUploader = new GitHubUploader();
    private DataPersistenceHelper dataHelper;
    private TextWatcher jsonTextWatcher;

    private SharedPreferences webUrlPrefs;
    private String savedWebBaseUrl = "";

    private Handler autoHideHandler = new Handler();
    private Runnable autoHideRunnable = new Runnable() {
        @Override
        public void run() {
            hideFloatingButtons();
        }
    };

    private String appVersion;

    private long lastClickTime = 0;
    private int lastClickPosition = -1;
    private static final long DOUBLE_CLICK_INTERVAL = 500;

    private LinearLayout fileEditorPanel, fileListPanel;
    private EditText etFileEditor, etFileSearch;
    private Button btnEditorSave, btnEditorCancel;
    private GitHubFile editingFile;
    private String editingOwner, editingRepo;

    // ==================== 新增：文件夹导航相关字段 ====================
    /** 当前浏览的仓库路径（空字符串表示仓库根目录） */
    private String currentPath = "";
    /** 面包屑导航栏容器 */
    private LinearLayout breadcrumbBar;
    /** 面包屑文本 */
    private TextView tvBreadcrumb;
    /** 返回上级按钮 */
    private Button btnBackFolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dataHelper = new DataPersistenceHelper(this);
        initSharedPreferences();
        loadSavedWebUrl();
        initView();
        requestPermission();
        setupTabs();
        adapter = new GitHubFileListAdapter(this, githubFileList);
        lvGithubFiles.setAdapter(adapter);
        lvGithubFiles.setDivider(null);
        lvGithubFiles.setDividerHeight(0);
        setListener();
        restoreData();
        selectTab(btnTabRepo);

        appVersion = getAppVersion();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveData();
    }

    private String getAppVersion() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "1.0.0";
        }
    }

    private void initSharedPreferences() {
        webUrlPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    private void loadSavedWebUrl() {
        savedWebBaseUrl = webUrlPrefs.getString(KEY_WEB_BASE_URL, "");
    }

    private void saveWebUrl(String webUrl) {
        savedWebBaseUrl = webUrl;
        webUrlPrefs.edit().putString(KEY_WEB_BASE_URL, webUrl).apply();
    }

    private String ensureUrlFormat(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        return url;
    }

    private String buildDownloadUrl(String baseUrl, String apkVersion) {
        String fileNamePart = "养虾助手" + apkVersion + ".apk";
        String encodedFileName = Uri.encode(fileNamePart);
        return baseUrl + encodedFileName;
    }

    private void initView() {
        rootLayout = findViewById(R.id.root_layout);
        btnBrowseFile = findViewById(R.id.btn_browse_file);
        btnBrowseToken = findViewById(R.id.btn_browse_token);
        btnQuery = findViewById(R.id.btn_query);
        btnGenerate = findViewById(R.id.btn_generate);
        btnSave = findViewById(R.id.btn_save);
        btnUpload = findViewById(R.id.btn_upload);
        tvFileName = findViewById(R.id.tv_file_name);
        tvTokenStatus = findViewById(R.id.tv_token_status);
        etGithubUrl = findViewById(R.id.et_github_url);
        lvGithubFiles = findViewById(R.id.lv_github_files);
        etJsonPreview = findViewById(R.id.et_json_preview);
        scrollJson = findViewById(R.id.scroll_json);
        scrollUpdate = findViewById(R.id.scroll_update);
        btnTabRepo = findViewById(R.id.btn_tab_repo);
        btnTabJson = findViewById(R.id.btn_tab_json);
        btnTabUpdate = findViewById(R.id.btn_tab_update);

        floatingButtonLayout = findViewById(R.id.floating_button_layout);
        floatingGenerateButtons = findViewById(R.id.floating_generate_buttons);
        floatingUploadButtons = findViewById(R.id.floating_upload_buttons);
        floatingSaveButtons = findViewById(R.id.floating_save_buttons);

        LinearLayout container = (LinearLayout) findViewById(R.id.ll_update_container);
        updateLogManager = new UpdateLogTableManager(this, container);

        // ==================== 新增：绑定面包屑导航视图 ====================
        breadcrumbBar = findViewById(R.id.breadcrumb_bar);
        tvBreadcrumb = findViewById(R.id.tv_breadcrumb);
        btnBackFolder = findViewById(R.id.btn_back_folder);

        jsonTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                generatedJson = s.toString();
            }
        };
        etJsonPreview.addTextChangedListener(jsonTextWatcher);

        fileEditorPanel = findViewById(R.id.file_editor_panel);
        etFileEditor = findViewById(R.id.et_file_editor);
        btnEditorSave = findViewById(R.id.btn_editor_save);
        btnEditorCancel = findViewById(R.id.btn_editor_cancel);
        fileListPanel = findViewById(R.id.file_list_panel);
        etFileSearch = findViewById(R.id.et_file_search);
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.INTERNET
            }, REQUEST_PERMISSIONS);
        }
    }

    private void setupTabs() {
        btnTabRepo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(btnTabRepo);
            }
        });

        btnTabJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(btnTabJson);
            }
        });

        btnTabUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(btnTabUpdate);
            }
        });

        btnTabJson.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                clearGeneratedJson();
                return true;
            }
        });

        btnTabRepo.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                fileEditorPanel.setVisibility(View.GONE);
                allGithubFiles.clear();
                githubFileList.clear();
                adapter.notifyDataSetChanged();
                currentPath = "";
                fileListPanel.setVisibility(View.GONE);
                etFileSearch.setVisibility(View.GONE);
                updateBreadcrumb();
                Toast.makeText(MainActivity.this, "已清除仓库文件，回到根目录", Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        btnTabUpdate.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                clearUpdateContent();
                return true;
            }
        });
    }

    private void selectTab(Button selected) {
        btnTabRepo.setSelected(false);
        btnTabJson.setSelected(false);
        btnTabUpdate.setSelected(false);
        fileListPanel.setVisibility(View.GONE);
        scrollJson.setVisibility(View.GONE);
        scrollUpdate.setVisibility(View.GONE);
        fileEditorPanel.setVisibility(View.GONE);

        selected.setSelected(true);

        if (selected == btnTabRepo) {
            fileListPanel.setVisibility(View.VISIBLE);
            updateBreadcrumb();
        } else {
            // 切换到其他Tab时隐藏面包屑
            breadcrumbBar.setVisibility(View.GONE);
        }

        if (selected == btnTabJson) {
            scrollJson.setVisibility(View.VISIBLE);
        } else if (selected == btnTabUpdate) {
            scrollUpdate.setVisibility(View.VISIBLE);
        }
    }

    private void setListener() {
        btnBrowseFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_SELECT_BASE_FILE);
            }
        });

        btnBrowseToken.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("text/plain");
                startActivityForResult(intent, REQUEST_SELECT_TOKEN_FILE);
            }
        });

        btnQuery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFileEditor();
                currentPath = "";
                queryGithub("");
            }
        });

        // ==================== 修改：文件夹点击进入子目录 ====================
        lvGithubFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                GitHubFile file = githubFileList.get(position);
                if ("dir".equals(file.getType())) {
                    navigateToFolder(file.getPath());
                    return;
                }

                long now = System.currentTimeMillis();
                if (position == lastClickPosition && now - lastClickTime < DOUBLE_CLICK_INTERVAL) {
                    // 双击文件 → 打开编辑
                    openFileEditor(file);
                } else {
                    // 单击 → 选中/取消
                    lastClickTime = now;
                    lastClickPosition = position;
                    if (adapter.getSelectedPos() == position) {
                        adapter.setSelectedPos(-1);
                    } else {
                        adapter.setSelectedPos(position);
                    }
                }
            }
        });

        lvGithubFiles.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                GitHubFile file = githubFileList.get(position);
                if ("file".equals(file.getType())) {
                    confirmDeleteFile(file);
                } else {
                    Toast.makeText(MainActivity.this, "文件夹不支持删除", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        // ==================== 新增：返回上级目录按钮 ====================
        btnBackFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateUp();
            }
        });

        btnGenerate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (adapter == null || adapter.getSelectedPos() < 0) {
                    generateCdnRelease();
                    return;
                }
                showFloatingButtons("generate");
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (generatedJson == null || generatedJson.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请先生成JSON后再保存", Toast.LENGTH_SHORT).show();
                    return;
                }
                showFloatingButtons("save");
            }
        });

        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (generatedJson != null && !generatedJson.isEmpty()) {
                    uploadGeneratedJson();
                } else {
                    showFloatingButtons("upload");
                }
            }
        });

        findViewById(R.id.btn_floating_github_link).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFloatingButtons();
                makeJsonWithLinkType(0);
            }
        });

        findViewById(R.id.btn_floating_web_link).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFloatingButtons();
                makeJsonWithLinkType(1);
            }
        });

        findViewById(R.id.btn_floating_upload_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFloatingButtons();
                if (!checkTokenAndUrl()) {
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, REQUEST_UPLOAD_FILE);
            }
        });

        findViewById(R.id.btn_floating_upload_folder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFloatingButtons();
                if (!checkTokenAndUrl()) {
                    return;
                }
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, REQUEST_UPLOAD_FOLDER);
            }
        });

        findViewById(R.id.btn_floating_update_existing).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFloatingButtons();
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/json");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, REQUEST_SAVE_JSON);
            }
        });

        findViewById(R.id.btn_floating_new_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFloatingButtons();
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "update.json");
                startActivityForResult(intent, REQUEST_SAVE_JSON);
            }
        });

        rootLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFloatingButtons();
            }
        });

        btnEditorSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editingFile == null) return;
                final String newContent = etFileEditor.getText().toString();
                Toast.makeText(MainActivity.this, "正在保存...", Toast.LENGTH_SHORT).show();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        githubUploader.uploadFileSync2(token, editingOwner, editingRepo,
                                editingFile.getPath(), newContent.getBytes(),
                                "编辑文件: " + editingFile.getName(),
                                new GitHubUploader.UploadCallback() {
                            @Override
                            public void onSuccess(String message) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                                        closeFileEditor();
                                    }
                                });
                            }

                            @Override
                            public void onFailure(final String error) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }

                            @Override
                            public void onProgress(String status) {}
                        });
                    }
                }).start();
            }
        });

        btnEditorCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFileEditor();
            }
        });

        etFileSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterFiles(s.toString());
            }
        });

        etGithubUrl.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    saveData();
                }
            }
        });
    }

    private void showFloatingButtons(String type) {
        hideFloatingButtons();

        floatingGenerateButtons.setVisibility(View.GONE);
        floatingUploadButtons.setVisibility(View.GONE);
        floatingSaveButtons.setVisibility(View.GONE);

        if ("generate".equals(type)) {
            floatingGenerateButtons.setVisibility(View.VISIBLE);
        } else if ("upload".equals(type)) {
            floatingUploadButtons.setVisibility(View.VISIBLE);
        } else if ("save".equals(type)) {
            floatingSaveButtons.setVisibility(View.VISIBLE);
        }

        floatingButtonLayout.setVisibility(View.VISIBLE);
        autoHideHandler.removeCallbacks(autoHideRunnable);
        autoHideHandler.postDelayed(autoHideRunnable, 3000);
    }

    private void hideFloatingButtons() {
        floatingButtonLayout.setVisibility(View.GONE);
        floatingGenerateButtons.setVisibility(View.GONE);
        floatingUploadButtons.setVisibility(View.GONE);
        floatingSaveButtons.setVisibility(View.GONE);
        autoHideHandler.removeCallbacks(autoHideRunnable);
    }

    // ==================== 新增：解析 owner 和 repo 的辅助方法 ====================
    private String[] resolveOwnerRepo() {
        String url = etGithubUrl.getText().toString().trim();
        if (url.isEmpty()) {
            return null;
        }
        String[] parts = url.split("/");
        if (parts.length < 5) {
            return null;
        }
        return new String[]{parts[3], parts[4]};
    }

    // ==================== 新增：文件夹导航方法 ====================
    private void navigateToFolder(String folderPath) {
        currentPath = folderPath;
        adapter.setSelectedPos(-1);
        closeFileEditor();
        queryGithub(folderPath);
    }

    private void navigateUp() {
        if (currentPath.isEmpty()) {
            Toast.makeText(this, "已在根目录", Toast.LENGTH_SHORT).show();
            return;
        }
        int lastSlash = currentPath.lastIndexOf('/');
        if (lastSlash <= 0) {
            currentPath = "";
        } else {
            currentPath = currentPath.substring(0, lastSlash);
        }
        adapter.setSelectedPos(-1);
        closeFileEditor();
        queryGithub(currentPath);
    }

    private void updateBreadcrumb() {
        if (currentPath.isEmpty()) {
            breadcrumbBar.setVisibility(View.GONE);
        } else {
            breadcrumbBar.setVisibility(View.VISIBLE);
            tvBreadcrumb.setText("/" + currentPath);
        }
    }

    // ==================== 修改：查询 GitHub 支持指定路径 ====================
    private void queryGithub(final String path) {
        final String url = etGithubUrl.getText().toString().trim();
        if (url.isEmpty() || token == null) {
            Toast.makeText(this, "请输入地址和Token", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] ownerRepo = resolveOwnerRepo();
        if (ownerRepo == null) {
            Toast.makeText(this, "地址格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        final String owner = ownerRepo[0];
        final String repo = ownerRepo[1];

        String apiUrl;
        if (path.isEmpty()) {
            apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/contents";
        } else {
            apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + path;
        }

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "token " + token)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, final IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "请求失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    final String body = response.body().string();

                    // 如果返回的是单个对象（非数组），说明 path 指向的是文件而非目录
                    if (body.trim().startsWith("{")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "该路径是文件，不是文件夹", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }

                    JSONArray array = new JSONArray(body);
                    final List<GitHubFile> tempList = new ArrayList<>();

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        String name = o.getString("name");
                        String itemPath = o.getString("path");
                        String type = o.getString("type");
                        String dUrl = o.optString("download_url", "");
                        String sha = o.optString("sha", "");

                        if ("dir".equals(type)) {
                            tempList.add(new GitHubFile(name, itemPath, type, "", sha));
                        } else if ("file".equals(type) && !dUrl.isEmpty()) {
                            tempList.add(new GitHubFile(name, itemPath, type, dUrl, sha));
                        }
                    }

                    java.util.Collections.sort(tempList, new java.util.Comparator<GitHubFile>() {
                        @Override
                        public int compare(GitHubFile a, GitHubFile b) {
                            boolean aIsDir = "dir".equals(a.getType());
                            boolean bIsDir = "dir".equals(b.getType());
                            if (aIsDir && !bIsDir) return -1;
                            if (!aIsDir && bIsDir) return 1;
                            return a.getName().compareToIgnoreCase(b.getName());
                        }
                    });

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            allGithubFiles.clear();
                            allGithubFiles.addAll(tempList);
                            githubFileList.clear();
                            githubFileList.addAll(tempList);
                            adapter.setSelectedPos(-1);
                            adapter.notifyDataSetChanged();
                            etFileSearch.setText("");
                            etFileSearch.setVisibility(tempList.isEmpty() ? View.GONE : View.VISIBLE);
                            fileListPanel.setVisibility(View.VISIBLE);
                            updateBreadcrumb();
                            String pathInfo = currentPath.isEmpty() ? "根目录" : currentPath;
                            Toast.makeText(MainActivity.this, "加载完成: " + tempList.size() + " 个条目 (" + pathInfo + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "解析错误: " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void openFileEditor(final GitHubFile file) {
        final String[] ownerRepo = resolveOwnerRepo();
        if (ownerRepo == null) return;
        editingOwner = ownerRepo[0];
        editingRepo = ownerRepo[1];
        editingFile = file;

        Toast.makeText(this, "正在加载文件...", Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String content = githubUploader.getFileContent(token, editingOwner, editingRepo, file.getPath());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (content == null) {
                            Toast.makeText(MainActivity.this, "加载文件失败", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        etFileEditor.setText(content);
                        etFileEditor.setSelection(content.length());
                        fileListPanel.setVisibility(View.GONE);
                        fileEditorPanel.setVisibility(View.VISIBLE);
                        breadcrumbBar.setVisibility(View.GONE);
                    }
                });
            }
        }).start();
    }

    private void closeFileEditor() {
        fileEditorPanel.setVisibility(View.GONE);
        fileListPanel.setVisibility(View.VISIBLE);
        editingFile = null;
        updateBreadcrumb();
    }

    private void filterFiles(String query) {
        githubFileList.clear();
        if (query.isEmpty()) {
            githubFileList.addAll(allGithubFiles);
        } else {
            for (GitHubFile f : allGithubFiles) {
                if (f.getName().toLowerCase().contains(query.toLowerCase())) {
                    githubFileList.add(f);
                }
            }
        }
        adapter.setSelectedPos(-1);
        adapter.notifyDataSetChanged();
    }

    private void makeJson() {
        makeJsonWithLinkType(-1);
    }

    private void makeJsonWithLinkType(final int linkType) {
        try {
            if (adapter.getSelectedPos() < 0) {
                Toast.makeText(this, "请先选择仓库文件", Toast.LENGTH_SHORT).show();
                return;
            }
            if (baseFileUri == null && (baseFileInternalPath == null || !new File(baseFileInternalPath).exists())) {
                Toast.makeText(this, "请先选择基准文件", Toast.LENGTH_SHORT).show();
                return;
            }

            final GitHubFile selectedFile = githubFileList.get(adapter.getSelectedPos());

            String fileName;
            long fileSize;
            String md5;
            String sha256;
            String apkVersion;

            if (baseFileUri != null) {
                fileName = FileHelper.getFileNameFromUri(getContentResolver(), baseFileUri);
                if (fileName == null) fileName = "未知文件";
                fileSize = FileHelper.getFileSizeFromUri(getContentResolver(), baseFileUri);
                InputStream is = getContentResolver().openInputStream(baseFileUri);
                md5 = FileHelper.calculateHash(is, "MD5");
                is.close();
                is = getContentResolver().openInputStream(baseFileUri);
                sha256 = FileHelper.calculateHash(is, "SHA-256");
                is.close();
                apkVersion = ApkVersionHelper.getApkVersionFromUri(getContentResolver(), getPackageManager(), baseFileUri);
            } else {
                File baseFile = new File(baseFileInternalPath);
                fileName = baseFile.getName();
                fileSize = baseFile.length();
                md5 = calculateFileHashFromFile(baseFile, "MD5");
                sha256 = calculateFileHashFromFile(baseFile, "SHA-256");
                apkVersion = ApkVersionHelper.getApkVersionFromFile(baseFile, getPackageManager());
            }

            if (md5 == null || sha256 == null) {
                Toast.makeText(this, "文件哈希计算失败，请检查文件是否可读", Toast.LENGTH_LONG).show();
                return;
            }

            if (apkVersion == null) {
                apkVersion = ApkVersionHelper.extractVersionFromFileName(fileName);
                Toast.makeText(this, "无法读取APK内部版本，使用文件名提取: " + apkVersion, Toast.LENGTH_LONG).show();
            }

            String updateLog = updateLogManager.collectLog();
            if (updateLog.isEmpty()) {
                updateLog = "无更新说明";
            }

            final String finalFileName = fileName;
            final long finalFileSize = fileSize;
            final String finalMd5 = md5;
            final String finalSha256 = sha256;
            final String finalApkVersion = apkVersion;
            final String finalUpdateLog = updateLog;
            final String finalGithubUrl = etGithubUrl.getText().toString();

            if (linkType == 0) {
                String downloadUrl = selectedFile.getDownloadUrl();

                String json = JsonGenerator.generateJson(
                        downloadUrl,
                        finalFileName,
                        finalFileSize,
                        finalMd5,
                        finalSha256,
                        finalApkVersion,
                        finalUpdateLog,
                        finalGithubUrl,
                        appVersion
                );

                etJsonPreview.removeTextChangedListener(jsonTextWatcher);
                etJsonPreview.setText(json);
                etJsonPreview.addTextChangedListener(jsonTextWatcher);
                generatedJson = json;

                selectTab(btnTabJson);
                Toast.makeText(this, "JSON生成成功，版本: " + finalApkVersion, Toast.LENGTH_SHORT).show();
            } else if (linkType == 1) {
                showWebUrlInputDialog(
                        finalFileName,
                        finalFileSize,
                        finalMd5,
                        finalSha256,
                        finalApkVersion,
                        finalUpdateLog,
                        finalGithubUrl,
                        selectedFile
                );
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("选择下载链接类型");
                final String[] options = {"使用原始GitHub链接", "使用网页版自定义链接", "取消"};
                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 2) {
                            dialog.dismiss();
                            return;
                        }

                        if (which == 0) {
                            String downloadUrl = selectedFile.getDownloadUrl();

                            String json = JsonGenerator.generateJson(
                                    downloadUrl,
                                    finalFileName,
                                    finalFileSize,
                                    finalMd5,
                                    finalSha256,
                                    finalApkVersion,
                                    finalUpdateLog,
                                    finalGithubUrl,
                                    appVersion
                            );

                            etJsonPreview.removeTextChangedListener(jsonTextWatcher);
                            etJsonPreview.setText(json);
                            etJsonPreview.addTextChangedListener(jsonTextWatcher);
                            generatedJson = json;

                            selectTab(btnTabJson);
                            Toast.makeText(MainActivity.this, "JSON生成成功，版本: " + finalApkVersion, Toast.LENGTH_SHORT).show();
                        } else {
                            showWebUrlInputDialog(
                                    finalFileName,
                                    finalFileSize,
                                    finalMd5,
                                    finalSha256,
                                    finalApkVersion,
                                    finalUpdateLog,
                                    finalGithubUrl,
                                    selectedFile
                            );
                        }
                    }
                });
                builder.setCancelable(true);
                builder.show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "生成失败: " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private void generateCdnRelease() {
        try {
            if (baseFileUri == null && (baseFileInternalPath == null || !new File(baseFileInternalPath).exists())) {
                Toast.makeText(this, "请先选择基准文件", Toast.LENGTH_SHORT).show();
                return;
            }
            String githubUrl = etGithubUrl.getText().toString().trim();
            if (githubUrl.isEmpty()) {
                Toast.makeText(this, "请输入GitHub仓库地址", Toast.LENGTH_SHORT).show();
                return;
            }

            String fileName;
            long fileSize;
            String md5;
            String sha256;
            String apkVersion;

            if (baseFileUri != null) {
                fileName = FileHelper.getFileNameFromUri(getContentResolver(), baseFileUri);
                if (fileName == null) fileName = "未知文件";
                fileSize = FileHelper.getFileSizeFromUri(getContentResolver(), baseFileUri);
                InputStream is = getContentResolver().openInputStream(baseFileUri);
                md5 = FileHelper.calculateHash(is, "MD5");
                is.close();
                is = getContentResolver().openInputStream(baseFileUri);
                sha256 = FileHelper.calculateHash(is, "SHA-256");
                is.close();
                apkVersion = ApkVersionHelper.getApkVersionFromUri(getContentResolver(), getPackageManager(), baseFileUri);
            } else {
                File baseFile = new File(baseFileInternalPath);
                fileName = baseFile.getName();
                fileSize = baseFile.length();
                md5 = calculateFileHashFromFile(baseFile, "MD5");
                sha256 = calculateFileHashFromFile(baseFile, "SHA-256");
                apkVersion = ApkVersionHelper.getApkVersionFromFile(baseFile, getPackageManager());
            }

            if (md5 == null || sha256 == null) {
                Toast.makeText(this, "文件哈希计算失败，请检查文件是否可读", Toast.LENGTH_LONG).show();
                return;
            }
            if (apkVersion == null) {
                apkVersion = ApkVersionHelper.extractVersionFromFileName(fileName);
                Toast.makeText(this, "无法读取APK内部版本，使用文件名提取: " + apkVersion, Toast.LENGTH_LONG).show();
            }

            String updateLog = updateLogManager.collectLog();
            if (updateLog.isEmpty()) {
                updateLog = "无更新说明";
            }

            String repoUrl = githubUrl.replaceAll("/$", "");
            String downloadUrl = "https://gh-proxy.com/" + repoUrl + "/releases/latest/download/" + fileName;

            String json = JsonGenerator.generateJson(
                    downloadUrl, fileName, fileSize, md5, sha256,
                    apkVersion, updateLog, githubUrl, appVersion
            );

            etJsonPreview.removeTextChangedListener(jsonTextWatcher);
            etJsonPreview.setText(json);
            etJsonPreview.addTextChangedListener(jsonTextWatcher);
            generatedJson = json;

            selectTab(btnTabJson);
            Toast.makeText(this, "CDN发布JSON生成成功，版本: " + apkVersion, Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "生成失败: " + e.toString(), Toast.LENGTH_LONG).show();
        }
    }

    private String calculateFileHashFromFile(File file, String algorithm) {
        InputStream is = null;
        try {
            is = new java.io.FileInputStream(file);
            return FileHelper.calculateHash(is, algorithm);
        } catch (Exception e) {
            Log.e("MainActivity", "计算哈希失败", e);
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void saveJson() {
        if (generatedJson == null || generatedJson.isEmpty()) {
            Toast.makeText(this, "请先生成JSON", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("保存JSON");
        builder.setMessage("请选择保存方式：");
        builder.setPositiveButton("更新已有文件", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("application/json");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, REQUEST_SAVE_JSON);
            }
        });
        builder.setNegativeButton("新建文件", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "update.json");
                startActivityForResult(intent, REQUEST_SAVE_JSON);
            }
        });
        builder.setNeutralButton("取消", null);
        builder.show();
    }

    private void clearGeneratedJson() {
        if (generatedJson != null && !generatedJson.isEmpty()) {
            etJsonPreview.removeTextChangedListener(jsonTextWatcher);
            etJsonPreview.setText("");
            etJsonPreview.addTextChangedListener(jsonTextWatcher);
            generatedJson = null;
            Toast.makeText(this, "已清除生成的JSON", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "没有可清除的JSON", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearUpdateContent() {
        updateLogManager.initTable(new ArrayList<String>());
        for (int i = 0; i < 10; i++) {
            updateLogManager.addRow("");
        }
        Toast.makeText(this, "已清除更新内容", Toast.LENGTH_SHORT).show();
    }

    private void showWebUrlInputDialog(final String fileName, final long fileSize,
                                       final String md5, final String sha256,
                                       final String apkVersion, final String updateLog,
                                       final String githubUrl, final GitHubFile selectedFile) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("输入网页版本基地址");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        final EditText input = new EditText(this);
        input.setHint("例如: https://example.com/path/");
        input.setText(savedWebBaseUrl);
        input.setSelectAllOnFocus(true);
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String baseUrl = input.getText().toString().trim();
                if (baseUrl.isEmpty()) {
                    Toast.makeText(MainActivity.this, "地址不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                baseUrl = ensureUrlFormat(baseUrl);
                saveWebUrl(baseUrl);

                String downloadUrl = buildDownloadUrl(baseUrl, apkVersion);

                String json = JsonGenerator.generateJson(
                        downloadUrl,
                        fileName,
                        fileSize,
                        md5,
                        sha256,
                        apkVersion,
                        updateLog,
                        githubUrl,
                        appVersion
                );

                etJsonPreview.removeTextChangedListener(jsonTextWatcher);
                etJsonPreview.setText(json);
                etJsonPreview.addTextChangedListener(jsonTextWatcher);
                generatedJson = json;

                selectTab(btnTabJson);
                Toast.makeText(MainActivity.this, "JSON生成成功，版本: " + apkVersion, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.show();
    }

    private void showOverwriteDialog(final Uri targetUri, final String targetFileName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("文件已存在");
        builder.setMessage("文件 \"" + targetFileName + "\" 已存在。是否覆盖？");
        builder.setPositiveButton("覆盖", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                saveJsonToUri(targetUri, true);
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Toast.makeText(MainActivity.this, "已取消保存", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNeutralButton("另存为", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                saveJson();
            }
        });
        builder.show();
    }

    private void saveJsonToUri(Uri uri, boolean overwrite) {
        OutputStream os = null;
        try {
            os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                Toast.makeText(this, "无法打开输出流", Toast.LENGTH_SHORT).show();
                return;
            }
            os.write(generatedJson.getBytes("UTF-8"));
            os.flush();
            String fileName = FileHelper.getFileNameFromUri(getContentResolver(), uri);
            Toast.makeText(this, "JSON已保存: " + fileName, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        } finally {
            if (os != null) {
                try { os.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void checkAndSaveJson(Uri uri) {
        final String fileName = FileHelper.getFileNameFromUri(getContentResolver(), uri);
        InputStream testIs = null;
        try {
            testIs = getContentResolver().openInputStream(uri);
            if (testIs != null && testIs.available() > 0) {
                testIs.close();
                showOverwriteDialog(uri, fileName);
                return;
            }
        } catch (Exception ignored) {
        } finally {
            if (testIs != null) {
                try { testIs.close(); } catch (IOException ignored) {}
            }
        }
        saveJsonToUri(uri, false);
    }

    private boolean checkTokenAndUrl() {
        boolean tokenMissing = (token == null || token.isEmpty());
        String githubUrl = etGithubUrl.getText().toString().trim();
        boolean urlMissing = githubUrl.isEmpty();

        if (tokenMissing && urlMissing) {
            Toast.makeText(this, "请先选择Token文件并填写GitHub仓库地址", Toast.LENGTH_SHORT).show();
            return false;
        } else if (tokenMissing) {
            Toast.makeText(this, "请先选择Token文件", Toast.LENGTH_SHORT).show();
            return false;
        } else if (urlMissing) {
            Toast.makeText(this, "请输入GitHub仓库地址", Toast.LENGTH_SHORT).show();
            return false;
        }

        String[] parts = githubUrl.split("/");
        if (parts.length < 5) {
            Toast.makeText(this, "GitHub地址格式错误", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void uploadGeneratedJson() {
        if (!checkTokenAndUrl()) return;

        String[] ownerRepo = resolveOwnerRepo();
        if (ownerRepo == null) return;
        final String owner = ownerRepo[0];
        final String repo = ownerRepo[1];

        // ==================== 修改：上传到当前浏览的目录 ====================
        final String filePath;
        if (currentPath.isEmpty()) {
            filePath = "update.json";
        } else {
            filePath = currentPath + "/update.json";
        }
        final String commitMessage = "通过JSON生成器更新配置文件";

        Toast.makeText(this, "正在上传JSON到GitHub..." + (currentPath.isEmpty() ? "" : " (目录: " + currentPath + ")"), Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String base64Content = Base64.encodeToString(generatedJson.getBytes("UTF-8"), Base64.NO_WRAP);
                    String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/contents/" + filePath;

                    String sha = null;
                    Request getRequest = new Request.Builder().url(apiUrl).header("Authorization", "token " + token).get().build();
                    Response getResponse = client.newCall(getRequest).execute();
                    if (getResponse.isSuccessful()) {
                        String getBody = getResponse.body().string();
                        JSONObject fileInfo = new JSONObject(getBody);
                        sha = fileInfo.getString("sha");
                    }

                    JSONObject requestBody = new JSONObject();
                    requestBody.put("message", commitMessage);
                    requestBody.put("content", base64Content);
                    if (sha != null) requestBody.put("sha", sha);

                    Request putRequest = new Request.Builder()
                            .url(apiUrl)
                            .header("Authorization", "token " + token)
                            .header("Content-Type", "application/json")
                            .put(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), requestBody.toString()))
                            .build();

                    Response putResponse = client.newCall(putRequest).execute();
                    final boolean success = putResponse.isSuccessful();
                    final String message;
                    if (success) {
                        message = "JSON上传成功！";
                    } else {
                        String errorBody = putResponse.body().string();
                        message = "JSON上传失败: " + putResponse.code() + " - " + errorBody;
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    final String errorMsg = e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "上传异常: " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    private void performFileUpload(final Uri fileUri) {
        if (!checkTokenAndUrl()) return;

        String[] ownerRepo = resolveOwnerRepo();
        if (ownerRepo == null) {
            Toast.makeText(this, "GitHub地址格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        final String owner = ownerRepo[0];
        final String repo = ownerRepo[1];
        final String fileName = FileHelper.getFileNameFromUri(getContentResolver(), fileUri);

        // ==================== 修改：上传到当前浏览的目录 ====================
        final String remotePath;
        if (currentPath.isEmpty()) {
            remotePath = fileName;
        } else {
            remotePath = currentPath + "/" + fileName;
        }

        Toast.makeText(this, "开始上传文件: " + fileName + (currentPath.isEmpty() ? "" : " → " + currentPath), Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream is = null;
                try {
                    is = getContentResolver().openInputStream(fileUri);
                    if (is == null) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "无法打开文件", Toast.LENGTH_SHORT).show();
                            }
                        });
                        return;
                    }
                    final byte[] content = FileHelper.readAllBytes(is);
                    is.close();

                    GitHubUploader uploader = new GitHubUploader();
                    uploader.uploadFile(token, owner, repo, remotePath, content, "通过JSON生成器上传文件", new GitHubUploader.UploadCallback() {
                        @Override
                        public void onSuccess(final String message) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        @Override
                        public void onFailure(final String error) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                                }
                            });
                        }

                        @Override
                        public void onProgress(final String status) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, status, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                } finally {
                    if (is != null) {
                        try { is.close(); } catch (IOException ignored) {}
                    }
                }
            }
        }).start();
    }

    private void performFolderUpload(final Uri folderUri) {
        if (!checkTokenAndUrl()) return;

        String[] ownerRepo = resolveOwnerRepo();
        if (ownerRepo == null) {
            Toast.makeText(this, "GitHub地址格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        final String owner = ownerRepo[0];
        final String repo = ownerRepo[1];

        // ==================== 修改：上传到当前浏览的目录 ====================
        final String remoteBasePath = currentPath;

        Toast.makeText(this, "开始上传文件夹..." + (currentPath.isEmpty() ? "" : " → " + currentPath), Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                GitHubUploader uploader = new GitHubUploader();
                uploader.uploadFolder(MainActivity.this, token, owner, repo, folderUri, remoteBasePath, "通过JSON生成器上传文件夹", new GitHubUploader.UploadCallback() {
                    @Override
                    public void onSuccess(final String message) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(final String error) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    @Override
                    public void onProgress(final String status) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, status, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void confirmDeleteFile(final GitHubFile file) {
        new AlertDialog.Builder(this)
            .setTitle("删除文件")
            .setMessage("确定要删除 " + file.getName() + " 吗？\n此操作会直接提交到 GitHub。")
            .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    deleteFileFromGithub(file);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void deleteFileFromGithub(final GitHubFile file) {
        String url = etGithubUrl.getText().toString().trim();
        if (url.isEmpty() || token == null) {
            Toast.makeText(this, "请先输入仓库地址和Token", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] parts = resolveOwnerRepo();
        if (parts == null) {
            Toast.makeText(this, "仓库地址格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        final String owner = parts[0];
        final String repo = parts[1];

        Toast.makeText(this, "正在删除: " + file.getName(), Toast.LENGTH_SHORT).show();

        GitHubUploader uploader = new GitHubUploader();
        uploader.deleteFile(token, owner, repo, file.getPath(), file.getSha(),
            "删除文件: " + file.getName(),
            new GitHubUploader.UploadCallback() {
                @Override
                public void onSuccess(String message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                            queryGithub(currentPath);
                        }
                    });
                }

                @Override
                public void onFailure(final String error) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                        }
                    });
                }

                @Override
                public void onProgress(String status) {}
            });
    }

    private void saveData() {
        String githubUrl = etGithubUrl.getText().toString();
        dataHelper.saveData(githubUrl, token, updateLogManager.getAllRows());
    }

    private void restoreData() {
        DataPersistenceHelper.RestoredData restored = dataHelper.restoreData();
        etGithubUrl.setText(restored.githubUrl);
        token = restored.token;
        tvTokenStatus.setText(token != null && !token.isEmpty() ? "Token已加载" : "未读取");
        updateLogManager.initTable(restored.updateRows);
        if (restored.updateRows.size() < 10) {
            for (int i = restored.updateRows.size(); i < 10; i++) {
                updateLogManager.addRow("");
            }
        }
        baseFileInternalPath = restored.baseFilePath;
        baseFileName = restored.baseFileName;
        if (baseFileInternalPath != null && baseFileName != null) {
            File baseFile = new File(baseFileInternalPath);
            if (baseFile.exists()) {
                tvFileName.setText(baseFileName);
            } else {
                baseFileInternalPath = null;
                baseFileName = null;
                dataHelper.clearBaseFilePath();
                Toast.makeText(this, "基准文件已失效，请重新选择", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_SELECT_BASE_FILE) {
            baseFileUri = data.getData();
            if (baseFileUri != null) {
                baseFileName = FileHelper.getFileNameFromUri(getContentResolver(), baseFileUri);
                tvFileName.setText(baseFileName);
                try {
                    InputStream is = getContentResolver().openInputStream(baseFileUri);
                    if (is != null) {
                        File internalDir = new File(getFilesDir(), "base_apk");
                        if (!internalDir.exists()) internalDir.mkdirs();
                        File internalFile = new File(internalDir, baseFileName);
                        FileOutputStream fos = new FileOutputStream(internalFile);
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                        fos.close();
                        is.close();
                        baseFileInternalPath = internalFile.getAbsolutePath();
                        dataHelper.saveBaseFilePath(baseFileInternalPath, baseFileName);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (requestCode == REQUEST_SELECT_TOKEN_FILE) {
            Uri tokenUri = data.getData();
            if (tokenUri != null) {
                try {
                    InputStream is = getContentResolver().openInputStream(tokenUri);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    token = sb.toString().trim();
                    tvTokenStatus.setText("Token已加载");
                    saveData();
                    Toast.makeText(this, "Token读取成功", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Token读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        } else if (requestCode == REQUEST_SAVE_JSON) {
            Uri uri = data.getData();
            if (uri != null) {
                checkAndSaveJson(uri);
            }
        } else if (requestCode == REQUEST_UPLOAD_FILE) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri fileUri = data.getClipData().getItemAt(i).getUri();
                    if (fileUri != null) {
                        performFileUpload(fileUri);
                    }
                }
                Toast.makeText(this, "开始上传 " + count + " 个文件", Toast.LENGTH_SHORT).show();
            } else if (data.getData() != null) {
                performFileUpload(data.getData());
            }
        } else if (requestCode == REQUEST_UPLOAD_FOLDER) {
            Uri folderUri = data.getData();
            if (folderUri != null) {
                performFolderUpload(folderUri);
            }
        }
    }
}