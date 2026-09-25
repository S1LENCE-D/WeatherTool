package com.simpleweather.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 后台预警监控：
 * 静态管理类 —— 开关状态 / 地区快照 / 已通知去重。
 * v9.88.3：不再有独立周期闹钟——预警检查并入 15 分钟后台心跳
 * （CacheRefreshReceiver 每次心跳都查一次），检查服务短时运行自停，
 * 通知栏无常驻痕迹。
 */
public final class AlertWatcher {
    public static final String PREFS = "alert_watch_prefs";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_CITY = "city";
    public static final String KEY_PROV = "prov";
    public static final String KEY_DIST = "dist";
    public static final String KEY_NOTIFIED = "notified";
    private AlertWatcher() { }

    public static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    /** 开关：开启即立即首查并注册 30 分钟周期闹钟，关闭则取消 */
    public static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, on).apply();
        Diag.i("setEnabled on=" + on);
        if (on) {
            startCheck(ctx);        // 立即首查
            CacheRefresher.ensureRunning(ctx);   // 确保后台心跳在跑（预警并入心跳）
        }
    }

    /** 主页渲染天气 / 切换城市时同步地区快照（后台服务无 UI 实例，靠它拿省/区） */
    public static void saveRegion(Context ctx, String city, String prov, String dist) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CITY, city == null ? "" : city)
                .putString(KEY_PROV, prov == null ? "" : prov)
                .putString(KEY_DIST, dist == null ? "" : dist)
                .apply();
    }

    public static String city(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CITY, "");
    }

    public static String prov(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_PROV, "");
    }

    public static String dist(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DIST, "");
    }

    /** 已通知预警集合（title + 日期，同日同标题不重复提醒） */
    public static Set<String> notified(Context ctx) {
        return new HashSet<String>(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_NOTIFIED, new HashSet<String>()));
    }

    public static void rememberNotified(Context ctx, Set<String> set) {
        // 只留最近 60 条，防无限膨胀
        while (set.size() > 60) {
            String oldest = null;
            for (String s : set) {
                if (oldest == null || s.compareTo(oldest) < 0) oldest = s;
            }
            if (oldest != null) set.remove(oldest);
            else break;
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putStringSet(KEY_NOTIFIED, set).apply();
    }

    public static String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /**
     * v10.1：启动一次预警检查。
     *
     * <p>**不再启动服务**——旧实现 `ctx.startService(new Intent(ctx, AlertWatchService.class))`
     * 在后台（15 分钟心跳）调用时会抛
     * {@code IllegalStateException: Not allowed to start service ... app is in background}
     * （Android 8+ 限制），异常在接收器里未捕获 → 进程崩溃、预警检查从未真正执行；
     * 只有用户在设置里打开开关时的**前台**首查能跑，所以看起来「开了一次就再也没动静」。
     *
     * <p>现在直接在子线程里完成检查。后台心跳里请直接调用
     * {@link AlertChecker#check(Context)}（在同一 goAsync 作用域内）。
     */
    public static void startCheck(final Context ctx) {
        new Thread(new Runnable() {
            @Override
            public void run() { AlertChecker.check(ctx); }
        }).start();
    }

}
