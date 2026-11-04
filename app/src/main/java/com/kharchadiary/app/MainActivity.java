package com.kharchadiary.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://ais-dev-yxbpumfrq6u6putvsqz67c-639935997715.asia-east1.run.app";
    private static final String BACKUP_FOLDER = "Download/KharchaDiary";
    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFF5F6FA);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(10, 8, 10, 8);
        actions.setBackgroundColor(0xFF18243A);
        Button backup = new Button(this);
        backup.setText("Backup Data");
        Button restore = new Button(this);
        restore.setText("Restore Data");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, -2, 1);
        buttonParams.setMargins(5, 0, 5, 0);
        actions.addView(backup, buttonParams);
        actions.addView(restore, buttonParams);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(APP_URL);

        backup.setOnClickListener(v -> startBackup());
        restore.setOnClickListener(v -> showBackups());
    }

    private void startBackup() {
        String js = "(function(){var d={};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);d[k]=localStorage.getItem(k);}return JSON.stringify({format:'KharchaDiaryBackup',version:1,createdAt:new Date().toISOString(),source:location.origin,storage:d});})()";
        webView.evaluateJavascript(js, value -> {
            try {
                if (value == null || "null".equals(value)) throw new Exception("App data could not be read.");
                String decoded = value;
                if (decoded.startsWith("\"") && decoded.endsWith("\""))
                    decoded = new org.json.JSONTokener(decoded).nextValue().toString();
                JSONObject payload = new JSONObject(decoded);
                if (!"KharchaDiaryBackup".equals(payload.optString("format"))) throw new Exception("Invalid backup data.");
                saveToInternalStorage(payload.toString(2));
            } catch (Exception e) {
                toast("Backup failed: " + e.getMessage());
            }
        });
    }

    // Android 10+: saves a normal, user-visible file in the phone's internal shared storage.
    // Android 7-9: saves in this app's private internal files directory.
    private void saveToInternalStorage(String json) throws Exception {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String name = "KharchaDiary_Backup_" + stamp + ".json";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, name);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/KharchaDiary");
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Could not create backup file.");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("Could not write backup file.");
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            File dir = new File(getFilesDir(), "KharchaDiaryBackups");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("Could not create backup folder.");
            File file = new File(dir, name);
            try (OutputStream out = new java.io.FileOutputStream(file)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        toast("Backup saved in Internal Storage / Download / KharchaDiary");
    }

    private void showBackups() {
        try {
            ArrayList<BackupItem> backups = getSavedBackups();
            if (backups.isEmpty()) {
                toast("No KharchaDiary backup found in internal storage.");
                return;
            }
            String[] names = new String[backups.size()];
            for (int i = 0; i < backups.size(); i++) names[i] = backups.get(i).name;
            new AlertDialog.Builder(this)
                .setTitle("Choose backup to restore")
                .setItems(names, (dialog, which) -> confirmRestore(backups.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
        } catch (Exception e) {
            toast("Could not read backups: " + e.getMessage());
        }
    }

    private ArrayList<BackupItem> getSavedBackups() {
        ArrayList<BackupItem> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relativePath = Environment.DIRECTORY_DOWNLOADS + "/KharchaDiary/";
            String selection = MediaStore.Downloads.RELATIVE_PATH + "=? AND " + MediaStore.Downloads.DISPLAY_NAME + " LIKE ?";
            String[] args = new String[]{relativePath, "KharchaDiary_Backup_%.json"};
            try (Cursor cursor = getContentResolver().query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads._ID},
                    selection, args, MediaStore.Downloads.DATE_ADDED + " DESC")) {
                if (cursor != null) while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    long id = cursor.getLong(1);
                    Uri uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.valueOf(id));
                    result.add(new BackupItem(name, uri, null));
                }
            }
        } else {
            File dir = new File(getFilesDir(), "KharchaDiaryBackups");
            File[] files = dir.listFiles((d, name) -> name.startsWith("KharchaDiary_Backup_") && name.endsWith(".json"));
            if (files != null) {
                java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File file : files) result.add(new BackupItem(file.getName(), null, file));
            }
        }
        return result;
    }

    private void confirmRestore(BackupItem item) {
        new AlertDialog.Builder(this)
            .setTitle("Restore KharchaDiary data?")
            .setMessage("This will replace the current app's local browser data with the selected backup. Continue?")
            .setNegativeButton("Cancel", (d, w) -> d.dismiss())
            .setPositiveButton("Restore", (d, w) -> restoreBackup(item))
            .show();
    }

    private void restoreBackup(BackupItem item) {
        try {
            InputStream input = item.file != null ? new FileInputStream(item.file) : getContentResolver().openInputStream(item.uri);
            if (input == null) throw new Exception("Cannot open backup file.");
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) content.append(line);
            }
            JSONObject backup = new JSONObject(content.toString());
            if (!"KharchaDiaryBackup".equals(backup.optString("format")) || backup.optInt("version") != 1 || !backup.has("storage"))
                throw new JSONException("This is not a supported KharchaDiary backup.");
            JSONObject storage = backup.getJSONObject("storage");
            String js = "(function(){var d=" + storage.toString() + ";localStorage.clear();Object.keys(d).forEach(function(k){localStorage.setItem(k,String(d[k]));});return 'ok';})()";
            webView.evaluateJavascript(js, value -> {
                toast("Restore completed. Reloading KharchaDiary...");
                webView.reload();
            });
        } catch (Exception e) {
            toast("Restore failed: " + e.getMessage());
        }
    }

    private static class BackupItem {
        final String name; final Uri uri; final File file;
        BackupItem(String name, Uri uri, File file) { this.name = name; this.uri = uri; this.file = file; }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
