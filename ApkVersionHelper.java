package com.example.jsongenerator.utils;

import android.content.ContentResolver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ApkVersionHelper {

    public static String getApkVersionFromUri(ContentResolver resolver, PackageManager pm, Uri uri) {
        InputStream is = null;
        File tempFile = null;
        try {
            is = resolver.openInputStream(uri);
            if (is == null) return null;
            String fileName = FileHelper.getFileNameFromUri(resolver, uri);
            tempFile = File.createTempFile("temp_" + System.currentTimeMillis(), "_" + fileName);
            OutputStream os = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.close();
            is.close();

            PackageInfo info = pm.getPackageArchiveInfo(tempFile.getAbsolutePath(), 0);
            if (info != null) {
                String versionName = info.versionName;
                if (versionName != null && !versionName.isEmpty()) {
                    if (versionName.startsWith("v") || versionName.startsWith("V")) {
                        versionName = versionName.substring(1);
                    }
                    return versionName;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
        return null;
    }

    // 修复：添加 PackageManager 参数
    public static String getApkVersionFromFile(File file, PackageManager pm) {
        try {
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (info != null) {
                String versionName = info.versionName;
                if (versionName != null && !versionName.isEmpty()) {
                    if (versionName.startsWith("v") || versionName.startsWith("V")) {
                        versionName = versionName.substring(1);
                    }
                    return versionName;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String extractVersionFromFileName(String fileName) {
        java.util.regex.Pattern patternV = java.util.regex.Pattern.compile("v\\d+\\.\\d+\\.\\d+");
        java.util.regex.Matcher matcherV = patternV.matcher(fileName);
        if (matcherV.find()) {
            return matcherV.group().substring(1);
        }
        java.util.regex.Pattern patternNum = java.util.regex.Pattern.compile("\\d+\\.\\d+\\.\\d+");
        java.util.regex.Matcher matcherNum = patternNum.matcher(fileName);
        if (matcherNum.find()) {
            return matcherNum.group();
        }
        return "1.0.0";
    }
}
