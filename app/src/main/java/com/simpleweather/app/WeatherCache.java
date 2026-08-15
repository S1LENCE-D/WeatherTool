package com.simpleweather.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * 天气数据本地缓存（SharedPreferences）。
 * 打开 APP 时先渲染上次成功数据，弱网 / 断网 / 定位失败也不至于空白。
 *
 * v9.73：每次成功拉取新数据后顺带清理历史缓存（WebView webcache、过期临时文件、
 * 崩溃日志），防止 App 越用越大。清理带 6 小时节流，避免频繁 IO。
 */
public class WeatherCache {

    private static final String PREFS = "weather_cache";
    private static final String KEY_JSON = "json";
    private static final String KEY_CITY = "city";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";
    private static final String KEY_TS = "ts";
    /** v9.82：缓存 schema 版本——旧版缓存缺 daily 降雨概率字段，升版后强制重拉 */
    private static final int CACHE_VER = 2;
    private static final String KEY_VER = "ver";
    private static final String KEY_LAST_CLEAN = "last_cleanup";

    /** 缓存有效期：24 小时内的数据才用于首屏展示 */
    public static final long MAX_AGE_MS = 24L * 3600 * 1000;

    /** 清理节流：距上次清理不足 6 小时则跳过（WebView 缓存重建成本高） */
    private static final long CLEAN_THROTTLE_MS = 6L * 3600 * 1000;

    /** 崩溃日志超过 10MB 即截断，只保留最新一条（v9.81：上限放宽到 10MB） */
    private static final long LOG_MAX_BYTES = 10L * 1024 * 1024;

    public static class Data {
        public String json;
        public String city;
        public double lat, lng;
        public long ts;
    }

    /** 保存一次成功的天气数据 */
    public static void save(Context c, String json, String city, double lat, double lng) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_JSON, json)
                .putString(KEY_CITY, city == null ? "" : city)
                .putFloat(KEY_LAT, (float) lat)
                .putFloat(KEY_LNG, (float) lng)
                .putLong(KEY_TS, System.currentTimeMillis())
                .putInt(KEY_VER, CACHE_VER)
                .apply();
        cleanup(c);   // v9.73：拉取新数据后清理旧缓存，防止 App 越用越大
    }

    /** 读取缓存；无缓存或已损坏返回 null */
    public static Data load(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString(KEY_JSON, null);
        if (json == null || json.isEmpty()) return null;
        // v9.82：旧 schema 缓存（缺降雨概率等字段）视为无效，触发重新拉取
        if (sp.getInt(KEY_VER, 0) < CACHE_VER) return null;
        Data d = new Data();
        d.json = json;
        d.city = sp.getString(KEY_CITY, "");
        d.lat = sp.getFloat(KEY_LAT, 0f);
        d.lng = sp.getFloat(KEY_LNG, 0f);
        d.ts = sp.getLong(KEY_TS, 0L);
        return d;
    }

    /** 缓存是否新鲜（未过期） */
    public static boolean fresh(Data d) {
        return d != null && d.ts > 0
                && System.currentTimeMillis() - d.ts < MAX_AGE_MS;
    }

    // ============ v9.73：缓存清理 ============

    /**
     * 清理历史缓存：① cacheDir 下 7 天前的临时文件；② 外置目录崩溃日志（超过 300KB 截断）。
     * 注意：webcache 目录（MSN 云图 cookie/会话缓存）不删除——云图页面依赖会话状态，
     * 且 WebView 自带 LRU 淘汰机制，缓存体积会自动收敛，不会无限增长。
     * 带节流：距上次清理不足 6 小时直接跳过。
     */
    public static void cleanup(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long last = sp.getLong(KEY_LAST_CLEAN, 0L);
            long now = System.currentTimeMillis();
            if (now - last < CLEAN_THROTTLE_MS) return;
            sp.edit().putLong(KEY_LAST_CLEAN, now).apply();

            // ① cacheDir 下 7 天前的临时文件（webcache 保留，WebView 自管理）
            File[] tmp = c.getCacheDir().listFiles();
            if (tmp != null) {
                for (File f : tmp) {
                    if (f.getName().equals("webcache")) continue;   // 云图 cookie/会话缓存，保留
                    if (now - f.lastModified() > 7L * 24 * 3600 * 1000) {
                        deleteRecursive(f);
                    }
                }
            }

            // ② 崩溃日志截断（应用专属目录 + 下载目录）
            try {
                File dir = c.getExternalFilesDir(null);
                if (dir != null) trimLog(new File(dir, "crash.log"));
            } catch (Exception ignored) { }
            try {
                File dl = android.os.Environment
                        .getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS);
                trimLog(new File(dl, "weathertool_crash.log"));
            } catch (Exception ignored) { }
        } catch (Exception ignored) { }
    }

    /** 日志超过上限则只保留最新一条（重写） */
    private static void trimLog(File f) {
        if (f == null || !f.exists() || f.length() <= LOG_MAX_BYTES) return;
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "rw");
            byte[] buf = new byte[(int) Math.min(raf.length(), 64L * 1024)];
            raf.seek(raf.length() - buf.length);
            raf.read(buf);
            raf.setLength(0);
            raf.seek(0);
            raf.write(buf);
            raf.close();
        } catch (Exception ignored) { }
    }

    /** 递归删除文件或目录（失败静默） */
    public static void deleteRecursive(File f) {
        if (f == null) return;
        try {
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File k : kids) deleteRecursive(k);
                }
            }
            f.delete();
        } catch (Exception ignored) { }
    }
}
