package com.simpleweather.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
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

/**
 * v9.90：诊断日志 —— 固定单文件 + 大小上限 + 总开关。
 *  - 单文件：所有日志统一写入 Download/WeatherTool_log.log（不再按天分文件，
 *    旧版按天文件 WeatherTool_log_yyyyMMdd.log 在首次启动时自动清理）
 *  - 大小上限：设置可调（最低 1MB，0 = 不限制），超过上限自动清空重写并补会话头
 *  - 总开关：设置可关闭，关闭后不再写入任何日志（设置页可重新开启）
 *  写入链路（逐级降级，保证一定有日志可写）：
 *    API 29+ : MediaStore.Downloads（无需存储权限）
 *    失败降级 : App 私有 filesDir（一定可写，导出时经 SAF 存到用户指定位置）
 *    API<29  : 公共 Download 目录直写（需 WRITE_EXTERNAL_STORAGE，manifest maxSdkVersion=28）
 *  逐行实时写入并 flush。 */
public class LogFile {
    private static final String TAG = "LogFile";
    private static final String PREFIX = "WeatherTool_log";
    private static final String NAME = PREFIX + ".log";      // v9.90：固定单文件
    private static final String PREFS = "log_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_MAX_MB = "max_mb";
    private static final int DEFAULT_MAX_MB = 5;             // 默认上限 5MB
    private static final Object LOCK = new Object();
    private static OutputStream out;
    private static String currentName = NAME;
    private static String currentPath = "";
    private static String initState = "未初始化";
    private static Uri currentUri;      // MediaStore 主路径 uri（成功时非空）
    private static File currentFile;    // 文件路径（私有目录降级 / API<29 直写）
    private static Context appCtx;      // v9.90：供 truncate / 设置同步使用
    private static boolean enabled = true;                  // v9.90：日志总开关
    private static long maxBytes = DEFAULT_MAX_MB * 1024L * 1024L;  // v9.90：大小上限（0=不限）

    private static final SimpleDateFormat TS =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private LogFile() { }

    /** App 启动时调用：读设置 -> 清理旧版按天文件 -> 定位固定单文件（自动降级）-> 写设备信息头。 */
    public static void init(Context ctx) {
        synchronized (LOCK) {
            appCtx = ctx.getApplicationContext();
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            enabled = sp.getBoolean(KEY_ENABLED, true);
            int mb = sp.getInt(KEY_MAX_MB, DEFAULT_MAX_MB);
            maxBytes = mb > 0 ? mb * 1024L * 1024L : 0;
            try {
                cleanupOld(ctx);   // v9.90：一次性清理旧版按天文件
                if (!enabled) {
                    closeOut();
                    initState = "日志已关闭（设置中可重新开启）";
                    return;
                }
                // 固定单文件：存在则追加，不存在则新建
                String reason = "";
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        Uri exist = findExisting(ctx, NAME);
                        if (exist != null) {
                            currentUri = exist;
                            out = ctx.getContentResolver().openOutputStream(exist, "wa");
                            currentPath = "Download/" + NAME + "（追加）";
                            initState = currentPath;
                            header(ctx);
                            return;
                        }
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Downloads.DISPLAY_NAME, NAME);
                        // v9.90：MIME 用 octet-stream——text/plain 会让 MediaStore 在
                        // 无 .txt 扩展名时自动追加，导致文件名变成 WeatherTool_log.log.txt
                        cv.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                        cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        currentUri = ctx.getContentResolver()
                                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        if (currentUri != null) {
                            out = ctx.getContentResolver().openOutputStream(currentUri, "wa");
                            currentPath = "Download/" + NAME;
                            initState = currentPath;
                            header(ctx);
                            return;
                        }
                        reason = "insert 返回 null";
                    } catch (Exception e) {
                        reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                    }
                    // 降级：App 私有目录（追加模式）
                    File f = new File(ctx.getFilesDir(), NAME);
                    out = new FileOutputStream(f, true);
                    currentFile = f;
                    currentPath = "私有目录(Download 写入失败: " + reason + ") " + f.getAbsolutePath();
                    initState = currentPath;
                    header(ctx);
                    return;
                }
                // API<29：公共 Download 直写（追加模式）
                try {
                    File dir = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS);
                    if (dir != null && (dir.exists() || dir.mkdirs())) {
                        File f = new File(dir, NAME);
                        out = new FileOutputStream(f, true);
                        currentFile = f;
                        currentPath = f.getAbsolutePath();
                        initState = currentPath;
                        header(ctx);
                        return;
                    }
                    reason = "Download 目录不可用";
                } catch (Exception e) {
                    reason = e.getClass().getSimpleName() + ": " + e.getMessage();
                }
                File f = new File(ctx.getFilesDir(), NAME);
                out = new FileOutputStream(f, true);
                currentFile = f;
                currentPath = "私有目录(Download 写入失败: " + reason + ") " + f.getAbsolutePath();
                initState = currentPath;
                header(ctx);
            } catch (Exception e) {
                closeOut();
                initState = "日志初始化失败: " + e;
            }
        }
    }

    /** v9.90：设置页切换日志总开关（持久化 + 立即生效） */
    public static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, on).apply();
        synchronized (LOCK) {
            enabled = on;
            if (on) {
                init(ctx);
            } else {
                closeOut();
                initState = "日志已关闭";
            }
        }
    }

    /** v9.90：设置页调整大小上限（MB；0 = 不限制，最低 1） */
    public static void setMaxMb(Context ctx, int mb) {
        if (mb < 0) mb = 0;
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_MAX_MB, mb).apply();
        synchronized (LOCK) {
            maxBytes = mb > 0 ? mb * 1024L * 1024L : 0;
        }
    }

    /** v9.90：日志开关当前状态 */
    public static boolean enabled() {
        synchronized (LOCK) { return enabled; }
    }

    /** v9.90：当前大小上限（MB；0 = 不限） */
    public static int maxMb() {
        synchronized (LOCK) {
            return maxBytes > 0 ? (int) (maxBytes / 1024 / 1024) : 0;
        }
    }

    /** 当前日志状态（展示用）：Download/xxx.log 或 降级说明 / 已关闭 */
    public static String state() {
        synchronized (LOCK) {
            if (!enabled) return "日志已关闭";
            return initState;
        }
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

    /** v9.88.1：版本号动态读取（不再写死，避免发版后日志标题停留在旧版） */
    private static void header(Context ctx) {
        String ver = "?";
        try {
            ver = ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception ignored) { }
        i(TAG, "==== 简洁天气 v" + ver + " 日志会话开始 ====");
        i(TAG, "SDK=" + Build.VERSION.SDK_INT + " target=33");
        i(TAG, "设备=" + Build.MANUFACTURER + " " + Build.MODEL);
        i(TAG, "ROM=" + Build.DISPLAY + " | " + Build.FINGERPRINT);
    }

    private static void write(char lvl, String tag, String msg) {
        synchronized (LOCK) {
            if (out == null || !enabled) return;
            try {
                // v9.90：大小上限检查——超限先清空重写（单文件滚动，补会话头）
                if (maxBytes > 0 && size() > maxBytes) {
                    truncate();
                }
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

    /** v9.90：当前日志大小（字节）；MediaStore 优先 AssetFileDescriptor，失败回退 File.length() */
    private static long size() {
        try {
            if (currentUri != null && appCtx != null) {
                AssetFileDescriptor afd =
                        appCtx.getContentResolver().openAssetFileDescriptor(currentUri, "r");
                if (afd != null) {
                    try {
                        long len = afd.getLength();
                        if (len >= 0) return len;
                    } finally {
                        afd.close();
                    }
                }
            }
        } catch (Exception ignored) { }
        if (currentFile != null && currentFile.exists()) return currentFile.length();
        return 0;
    }

    /** v9.90：清空当前日志并补写会话头（单文件滚动）；滚动后发低打扰通知提示用户 */
    private static void truncate() {
        try {
            closeOut();
            if (currentUri != null && appCtx != null) {
                out = appCtx.getContentResolver().openOutputStream(currentUri, "w");
            } else if (currentFile != null) {
                out = new FileOutputStream(currentFile, false);
            }
            if (out != null && appCtx != null) header(appCtx);
            // v9.90：日志写满提示（仅状态栏通知，不响铃不震动）
            if (appCtx != null) {
                int mb = maxMb();
                Notifier.notifyLogFull(appCtx, "日志文件已达上限（" + mb + "MB），"
                        + "已自动清空重写。可在 设置 → 诊断日志 中调整上限或关闭日志。");
            }
        } catch (Exception ignored) {
            out = null;
        }
    }

    private static void closeOut() {
        try { if (out != null) out.close(); } catch (Exception ignored) { }
        out = null;
    }

    /** 查询 Download 中是否已有同名日志文件（单文件复用用）。
     *  先查后插，避免 MediaStore 对同名 insert 自动改名生成 "(1)" 副本。 */
    private static Uri findExisting(Context ctx, String name) {
        try {
            android.database.Cursor c = ctx.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Downloads._ID},
                    MediaStore.Downloads.DISPLAY_NAME + "=?",
                    new String[]{name},
                    MediaStore.Downloads.DATE_ADDED + " DESC");
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        return android.content.ContentUris.withAppendedId(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0));
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** v9.90：清理旧格式日志残留（按天文件 WeatherTool_log_yyyyMMdd.log、
     *  以及 MIME 误追加导致的 WeatherTool_log.log.txt / "(1)" 副本等），
     *  仅保留固定单文件 WeatherTool_log.log。 */
    private static void cleanupOld(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.database.Cursor c = ctx.getContentResolver().query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME},
                        null, null, null);
                if (c == null) return;
                try {
                    while (c.moveToNext()) {
                        String name = c.getString(1);
                        if (name != null && name.startsWith(PREFIX)
                                && !NAME.equals(name)) {
                            try {
                                ctx.getContentResolver().delete(
                                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                        MediaStore.Downloads._ID + "=?",
                                        new String[]{String.valueOf(c.getLong(0))});
                            } catch (Exception ignored) { }
                        }
                    }
                } finally {
                    c.close();
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                if (dir == null || !dir.isDirectory()) return;
                File[] files = dir.listFiles();
                if (files == null) return;
                for (File f : files) {
                    String n = f.getName();
                    if (n.startsWith(PREFIX) && !NAME.equals(n)) f.delete();
                }
            }
        } catch (Exception ignored) { }
    }
}
