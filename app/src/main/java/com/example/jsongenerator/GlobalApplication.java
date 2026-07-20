package com.example.jsongenerator;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;

public class GlobalApplication extends Application {

    private static final String CRASH_DIR = "crash_logs";
    private static final String CRASH_FILE = "last_crash.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));
    }

    private static File getCrashDir(Context context) {
        File dir = new File(context.getFilesDir(), CRASH_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getLastCrashFile(Context context) {
        return new File(context.getFilesDir(), CRASH_FILE);
    }

    public static void saveCrashLog(Context context, String log) {
        try {
            String time = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File dir = getCrashDir(context);
            try (PrintWriter pw = new PrintWriter(new File(dir, "crash_" + time + ".txt"))) {
                pw.println(log);
            }

            File lastCrash = getLastCrashFile(context);
            try (FileOutputStream fos = new FileOutputStream(lastCrash)) {
                fos.write(log.getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    public static String readLastCrashLog(Context context) {
        File file = getLastCrashFile(context);
        if (!file.exists()) return null;
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static void clearCrashLog(Context context) {
        File file = getLastCrashFile(context);
        if (file.exists()) file.delete();
        File dir = getCrashDir(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
    }

    private static volatile boolean isHandlingCrash = false;

    private static class CrashHandler implements Thread.UncaughtExceptionHandler {
        private final Context context;

        CrashHandler(Context context) {
            this.context = context.getApplicationContext();
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            if (isHandlingCrash) {
                Process.killProcess(Process.myPid());
                System.exit(0);
                return;
            }
            isHandlingCrash = true;
            try {
                String log = buildLog(throwable);
                saveCrashLog(context, log);

                Intent intent = new Intent(context, CrashActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        private String buildLog(Throwable throwable) {
            String time = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(new Date());

            String versionName = "unknown";
            long versionCode = 0;
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                versionName = pi.versionName;
                versionCode = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            } catch (Throwable ignored) {}

            LinkedHashMap<String, String> head = new LinkedHashMap<>();
            head.put("Time", time);
            head.put("Device", String.format("%s %s", Build.MANUFACTURER, Build.MODEL));
            head.put("Android", String.format(Locale.US, "%s (API %d)", Build.VERSION.RELEASE, Build.VERSION.SDK_INT));
            head.put("Version", String.format(Locale.US, "%s (%d)", versionName, versionCode));
            head.put("Abis", Build.SUPPORTED_ABIS != null ? Arrays.toString(Build.SUPPORTED_ABIS) : "unknown");

            StringBuilder sb = new StringBuilder();
            sb.append("=== 崩溃日志 ===\n\n");
            for (String key : head.keySet()) {
                sb.append(key).append(": ").append(head.get(key)).append("\n");
            }
            sb.append("\n");

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            pw.flush();
            sb.append(sw.toString());

            return sb.toString();
        }
    }

    // ==================== 崩溃显示页面 ====================

    public static final class CrashActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            setTitle("应用崩溃");
            getWindow().setStatusBarColor(0xFFBF360C);

            View content = buildDefaultView();
            setContentView(content);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            isHandlingCrash = false;
        }

        private View buildDefaultView() {
            ScrollView sv = new ScrollView(this);
            sv.setPadding(16, 16, 16, 16);
            sv.setFillViewport(true);

            TextView tvLog = new TextView(this);
            tvLog.setId(android.R.id.text1);
            tvLog.setTextIsSelectable(true);
            tvLog.setText(readLastCrashLog(this) != null ? readLastCrashLog(this) : "未捕获到崩溃信息");

            Button btnShare = new Button(this);
            btnShare.setText("转发给开发者");
            btnShare.setOnClickListener(v -> {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, tvLog.getText().toString());
                startActivity(Intent.createChooser(share, "分享崩溃日志"));
            });

            Button btnClear = new Button(this);
            btnClear.setText("我知道了");
            btnClear.setOnClickListener(v -> {
                clearCrashLog(this);
                isHandlingCrash = false;
                finishAffinity();
                Process.killProcess(Process.myPid());
                System.exit(0);
            });

            android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            layout.addView(tvLog, new android.widget.LinearLayout.LayoutParams(
                    -1, 0, 1));
            layout.addView(btnShare, new android.widget.LinearLayout.LayoutParams(-1, -2));
            layout.addView(btnClear, new android.widget.LinearLayout.LayoutParams(-1, -2));

            sv.addView(layout);
            return sv;
        }
    }
}