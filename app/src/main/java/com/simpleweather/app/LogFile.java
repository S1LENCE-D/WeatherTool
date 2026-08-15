package com.simpleweather.app;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** v9.87-fix：定位诊断日志——自动写入 Download 目录 WeatherTool_log_*.log。
 *  写入链路（逐级降级，保证一定有日志可写）：
 *    API 29+ : MediaStore.Downloads（无需存储权限）
 *    失败降级 : App 私有 filesDir（一定可写，导出时经 SAF 存到用户指定位置）
 *    API<29  : 公共 Download 目录直写（需 WRITE_EXTERNAL_STORAGE，manifest maxSdkVersion=28）
 *  每次启动新建一份会话日志，Download 中保留最近 5 份；逐行实时写入并 flush。 */
public class LogFile {
    private static final String TAG = "LogFile";
    private static final String PREFIX = "WeatherTool_log";
    private static final int KEEP = 5;
    private static final Object LOCK = new Object();
    private static OutputStream out;
    private static String currentName = "";
    private static String currentPath = "";
    private static String initState = "未初始化";
    private static Uri currentUri;      // MediaStore 主路径 uri（成功时非空）
    private static File currentFile;    // 文件路径（私有目录降级 / API<29 直写）

    private static final SimpleDateFormat TS =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private LogFile() { }

    /** App 启动时调用：清理旧日志 -> 新建会话日志（自动降级）-> 写设备信息头 */
    public static void init(Context ctx) {
        synchronized (LOCK) {
            try {
                cleanup(ctx);
                currentName = PREFIX + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                        .format(new Date()) + ".log";
                String reason = "";
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Downloads.DISPLAY_NAME, currentName);
                        cv.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                        cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        currentUri = ctx.getContentResolver()
                                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (currentUri != null) {
                            out = ctx.getContentResolver().openOutputStream(currentUri);
                            currentPath = "Download/" + currentName;
                            initState = currentPath;
                            header();
                            return;
                        }
                        reason = "insert 返回 null";
                    } catch (Exception e) {
                        reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                    // 降级：App 私有目录
                    File f = new File(ctx.getFilesDir(), currentName);
                    out = new FileOutputStream(f);
                    currentFile = f;
                    currentPath = "私有目录(Download 写入失败: " + reason + ") " + f.getAbsolutePath();
                    initState = currentPath;
                    header();
                    return;
                }
                // API<29：公共 Download 直写
                try {
                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (dir != null && (dir.exists() || dir.mkdirs())) {
                        File f = new File(dir, currentName);
                        out = new FileOutputStream(f, false);
                        currentFile = f;
                        currentPath = f.getAbsolutePath();
                        initState = currentPath;
                        header();
                        return;
                    }
                    reason = "Download 目录不可用";
                } catch (Exception e) {
                    reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                File f = new File(ctx.getFilesDir(), currentName);
                out = new FileOutputStream(f);
                currentFile = f;
                currentPath = "私有目录(Download 写入失败: " + reason + ") " + f.getAbsolutePath();
                initState = currentPath;
                header();
            } catch (Exception e) {
                out = null;
                initState = "日志初始化失败: " + e;
            }
        }
    }

    /** 当前日志状态（展示用）：Download/xxx.log 或 降级说明 */
    public static String state() {
        synchronized (LOCK) { return initState; }
    }

    /** 当前日志文件名 */
    public static String fileName() {
        synchronized (LOCK) { return currentName; }
    }

    /** 把当前会话日志全文写入 target（SAF 导出用）；成功返回 true */
    public static boolean exportTo(Context ctx, Uri target) {
        synchronized (LOCK) {
            String text = readText(ctx);
            if (text == null) return false;
            try {
                OutputStream os = ctx.getContentResolver().openOutputStream(target);
                if (os == null) return false;
                os.write(text.getBytes("UTF-8"));
                os.flush();
                os.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** 读回当前会话日志全文（MediaStore uri 与文件路径都支持）；不可读返回 null */
    private static String readText(Context ctx) {
        try {
            if (currentUri != null) {
                java.io.InputStream is = ctx.getContentResolver().openInputStream(currentUri);
                if (is == null) return null;
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                is.close();
                return bos.toString("UTF-8");
            }
            if (currentFile != null && currentFile.exists()) {
                FileInputStream fis = new FileInputStream(currentFile);
                byte[] b = new byte[(int) currentFile.length()];
                int n = fis.read(b);
                fis.close();
                return n > 0 ? new String(b, 0, n, "UTF-8") : "";
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void i(String tag, String msg) { write('I', tag, msg); }
    public static void w(String tag, String msg) { write('W', tag, msg); }
    public static void e(String tag, String msg) { write('E', tag, msg); }

    public static void e(String tag, String msg, Throwable t) {
        StringBuilder sb = new StringBuilder(msg);
        if (t != null) {
            sb.append(" | ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            for (StackTraceElement el : t.getStackTrace()) {
                if (el.getClassName().startsWith("com.simpleweather")) {
                    sb.append("\n    at ").append(el);
                    if (sb.length() > 900) break;
                }
            }
        }
        write('E', tag, sb.toString());
    }

    private static void header() {
        i(TAG, "==== 简洁天气 v9.87 日志会话开始 ====");
        i(TAG, "SDK=" + Build.VERSION.SDK_INT + " target=33");
        i(TAG, "设备=" + Build.MANUFACTURER + " " + Build.MODEL);
        i(TAG, "ROM=" + Build.DISPLAY + " | " + Build.FINGERPRINT);
    }

    private static void write(char lvl, String tag, String msg) {
        synchronized (LOCK) {
            if (out == null) return;
            try {
                StackTraceElement st = new Throwable().getStackTrace()[2];  // 调用者
                String cn = st.getClassName();
                int idx = cn.lastIndexOf('.');
                if (idx >= 0) cn = cn.substring(idx + 1);
                String line = String.format(Locale.US, "%s %c %s (%s:%d) %s",
                        TS.format(new Date()), lvl, tag, cn, st.getLineNumber(), msg);
                out.write((line + "\n").getBytes("UTF-8"));
                out.flush();
            } catch (Exception ignored) { }
        }
    }

    /** 清理旧会话日志：仅保留最近 KEEP 份（仅 Download 中的） */
    private static void cleanup(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.database.Cursor c = ctx.getContentResolver().query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME,
                                MediaStore.Downloads.DATE_ADDED},
                        null, null, MediaStore.Downloads.DATE_ADDED + " DESC");
                if (c == null) return;
                int kept = 0;
                while (c.moveToNext()) {
                    String name = c.getString(1);
                    if (name == null || !name.startsWith(PREFIX)) continue;
                    kept++;
                    if (kept > KEEP) {
                        try {
                            ctx.getContentResolver().delete(
                                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                    MediaStore.Downloads._ID + "=?",
                                    new String[]{String.valueOf(c.getLong(0))});
                        } catch (Exception ignored) { }
                    }
                }
                c.close();
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (dir == null || !dir.isDirectory()) return;
                File[] files = dir.listFiles();
                if (files == null) return;
                java.util.List<File> logs = new java.util.ArrayList<File>();
                for (File f : files) {
                    if (f.getName().startsWith(PREFIX)) logs.add(f);
                }
                java.util.Collections.sort(logs, new java.util.Comparator<File>() {
                    @Override public int compare(File a, File b) {
                        return Long.valueOf(b.lastModified()).compareTo(a.lastModified());
                    }
                });
                for (int i = KEEP; i < logs.size(); i++) logs.get(i).delete();
            }
        } catch (Exception ignored) { }
    }
}
