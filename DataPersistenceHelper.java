package com.example.jsongenerator;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class DataPersistenceHelper {
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_GITHUB_URL = "github_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_UPDATE_ROWS = "update_rows";
    private static final String KEY_BASE_FILE_PATH = "base_file_path";
    private static final String KEY_BASE_FILE_NAME = "base_file_name";
    private static final String ROW_SEPARATOR = "\u241F";

    private SharedPreferences sharedPreferences;

    public DataPersistenceHelper(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 保存所有数据
     */
    public void saveData(String githubUrl, String token, List<String> updateRows) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_GITHUB_URL, githubUrl);
        if (token != null) {
            editor.putString(KEY_TOKEN, token);
        }

        // 将更新日志列表转换为字符串存储
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < updateRows.size(); i++) {
            if (i > 0) sb.append(ROW_SEPARATOR);
            sb.append(updateRows.get(i));
        }
        editor.putString(KEY_UPDATE_ROWS, sb.toString());
        editor.apply();
    }

    /**
     * 保存基准文件路径和文件名
     */
    public void saveBaseFilePath(String filePath, String fileName) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_BASE_FILE_PATH, filePath);
        editor.putString(KEY_BASE_FILE_NAME, fileName);
        editor.apply();
    }

    /**
     * 清除基准文件路径
     */
    public void clearBaseFilePath() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_BASE_FILE_PATH);
        editor.remove(KEY_BASE_FILE_NAME);
        editor.apply();
    }

    /**
     * 获取基准文件路径
     */
    public String getBaseFilePath() {
        return sharedPreferences.getString(KEY_BASE_FILE_PATH, null);
    }

    /**
     * 获取基准文件名
     */
    public String getBaseFileName() {
        return sharedPreferences.getString(KEY_BASE_FILE_NAME, null);
    }

    /**
     * 恢复所有保存的数据
     */
    public RestoredData restoreData() {
        String githubUrl = sharedPreferences.getString(KEY_GITHUB_URL, "");
        String token = sharedPreferences.getString(KEY_TOKEN, null);
        String updateRowsStr = sharedPreferences.getString(KEY_UPDATE_ROWS, "");
        String baseFilePath = sharedPreferences.getString(KEY_BASE_FILE_PATH, null);
        String baseFileName = sharedPreferences.getString(KEY_BASE_FILE_NAME, null);

        // 解析更新日志字符串为列表
        List<String> updateRows = new ArrayList<>();
        if (!updateRowsStr.isEmpty()) {
            String[] rows = updateRowsStr.split(ROW_SEPARATOR);
            for (String row : rows) {
                updateRows.add(row);
            }
        } else {
            // 如果没有保存的数据，初始化10个空行
            for (int i = 0; i < 10; i++) {
                updateRows.add("");
            }
        }

        return new RestoredData(githubUrl, token, updateRows, baseFilePath, baseFileName);
    }

    /**
     * 恢复的数据容器类
     */
    public static class RestoredData {
        public final String githubUrl;
        public final String token;
        public final List<String> updateRows;
        public final String baseFilePath;
        public final String baseFileName;

        public RestoredData(String githubUrl, String token, List<String> updateRows, String baseFilePath, String baseFileName) {
            this.githubUrl = githubUrl;
            this.token = token;
            this.updateRows = updateRows;
            this.baseFilePath = baseFilePath;
            this.baseFileName = baseFileName;
        }
    }
}
