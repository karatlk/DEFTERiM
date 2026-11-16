package com.atolye.defteri;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import org.json.JSONObject;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    WebView w;
    ValueCallback<Uri[]> cb;
    Uri camUri;

    class Bridge {
        @JavascriptInterface
        public void http(final int id, final String method, final String url, final String hdrs, final String body) {
            new Thread(new Runnable() {
                public void run() {
                    int code = 0;
                    String text = "";
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                        c.setRequestMethod(method);
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(30000);
                        JSONObject h = new JSONObject(hdrs);
                        Iterator<String> it = h.keys();
                        while (it.hasNext()) {
                            String key = it.next();
                            c.setRequestProperty(key, h.getString(key));
                        }
                        if (body != null && body.length() > 0) {
                            c.setDoOutput(true);
                            OutputStream o = c.getOutputStream();
                            o.write(body.getBytes("UTF-8"));
                            o.close();
                        }
                        code = c.getResponseCode();
                        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                        if (is != null) {
                            ByteArrayOutputStream bo = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
                            text = bo.toString("UTF-8");
                        }
                    } catch (Exception e) {
                        code = 0;
                        text = String.valueOf(e.getMessage());
                    }
                    final String js = "__http(" + id + "," + code + "," + JSONObject.quote(text) + ")";
                    runOnUiThread(new Runnable() {
                        public void run() { w.evaluateJavascript(js, null); }
                    });
                }
            }).start();
        }

        @JavascriptInterface
        public void openUrl(final String url) {
            runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception e) {
                        toast("Açılamadı: " + e.getMessage());
                    }
                }
            });
        }

        @JavascriptInterface
        public void saveFile(String name, String b64, String sub) {
            try {
                byte[] d = Base64.decode(b64, Base64.DEFAULT);
                String mime = name.endsWith(".pdf") ? "application/pdf"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                String folder = "Atölye Defteri" + (sub == null || sub.length() == 0 ? "" : "/" + sub);
                if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
                    File dir = new File(Environment.getExternalStorageDirectory(), folder);
                    dir.mkdirs();
                    FileOutputStream fo = new FileOutputStream(new File(dir, name));
                    fo.write(d);
                    fo.close();
                    toast("Kaydedildi: Dahili depolama/" + folder + "/" + name);
                    return;
                }
                if (Build.VERSION.SDK_INT < 29) {
                    File dir2 = new File(getExternalFilesDir(null), folder);
                    dir2.mkdirs();
                    FileOutputStream fo2 = new FileOutputStream(new File(dir2, name));
                    fo2.write(d);
                    fo2.close();
                    toast("Kaydedildi: " + dir2.getAbsolutePath() + "/" + name);
                    return;
                }
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + folder);
                Uri u = getContentResolver().insert(MediaStore.Files.getContentUri("external"), v);
                OutputStream o = getContentResolver().openOutputStream(u);
                o.write(d);
                o.close();
                toast("Kaydedildi: Belgeler/" + folder + "/" + name
                        + "\nDahili depolama/Atölye Defteri için Tüm dosyalara erişim izni verin.");
                askAll();
            } catch (Exception e) {
                toast("Kaydedilemedi: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void shareFile(final String name, String b64) {
            try {
                byte[] d = Base64.decode(b64, Base64.DEFAULT);
                File dir = new File(getCacheDir(), "share");
                dir.mkdirs();
                final File f = new File(dir, name);
                FileOutputStream o = new FileOutputStream(f);
                o.write(d);
                o.close();
                runOnUiThread(new Runnable() {
                    public void run() {
                        Uri u = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fp", f);
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("application/pdf");
                        i.putExtra(Intent.EXTRA_STREAM, u);
                        i.putExtra(Intent.EXTRA_SUBJECT, name);
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(i, "PDF paylaş"));
                    }
                });
            } catch (Exception e) {
                toast("Paylaşılamadı: " + e.getMessage());
            }
        }
    }

    void askAll() {
        final SharedPreferences sp = getSharedPreferences("p", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT < 30 || sp.getBoolean("asked", false)) return;
        sp.edit().putBoolean("asked", true).apply();
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                } catch (Exception e) { }
            }
        });
    }

    void toast(final String t) {
        runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(MainActivity.this, t, Toast.LENGTH_LONG).show(); }
        });
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        w = new WebView(this);
        setContentView(w);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        w.addJavascriptInterface(new Bridge(), "Android");
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> c, FileChooserParams p) {
                if (cb != null) cb.onReceiveValue(null);
                cb = c;
                try {
                    Intent i;
                    if (p.isCaptureEnabled()) {
                        File f = new File(getCacheDir(), "cam.jpg");
                        camUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fp", f);
                        i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        i.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } else {
                        camUri = null;
                        i = p.createIntent();
                    }
                    startActivityForResult(i, 1);
                } catch (Exception e) {
                    cb = null;
                    return false;
                }
                return true;
            }
        });
        w.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int r, int c, Intent d) {
        if (r == 1 && cb != null) {
            Uri[] res = null;
            if (c == RESULT_OK) {
                if (camUri != null) res = new Uri[]{camUri};
                else res = WebChromeClient.FileChooserParams.parseResult(c, d);
            }
            cb.onReceiveValue(res);
            cb = null;
        }
        super.onActivityResult(r, c, d);
    }
}
