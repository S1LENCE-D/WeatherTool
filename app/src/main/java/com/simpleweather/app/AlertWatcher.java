package com.simpleweather.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 后台预警监控（v9.78 重构）：
 * 静态管理类 —— 开关状态 / 地区快照 / 已通知去重 / 周期闹钟调度。
 * 不再使用前台常驻服务：改由 AlarmManager 每 30 分钟触发一次检查，
 * 检查完成后服务自停，通知栏无常驻痕迹。
 */
public final class AlertWatcher {
    public static final String PREFS = "alert_watch_prefs";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_CITY = "city";
    public static final String KEY_PROV = "prov";
    public static final String KEY_DIST = "dist";
    public static final String KEY_NOTIFIED = "notified";
    public static final long INTERVAL_MS = 30 * 60 * 1000L;   // 30 分钟
    public static final String ACTION_ALERT_TICK = "com.simpleweather.app.ALERT_TICK";
    public static final int REQ_ALARM = 2;   // 与每日天气闹钟(requestCode 0)区分

    private AlertWatcher() { }

    public static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    /** 开关：开启即注册 30 分钟周期闹钟，关闭则取消 */
    public static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, on).apply();
        if (on) scheduleTick(ctx);
        else cancelTick(ctx);
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

    /** 应用启动 / 开机：开关开着则恢复周期闹钟 */
    public static void ensureRunning(Context ctx) {
        if (enabled(ctx)) scheduleTick(ctx);
    }

    /** 注册周期触发：setInexactRepeating 省电，无需精确闹钟权限 */
    public static void scheduleTick(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent it = new Intent(ctx, AlertAlarmReceiver.class)
                .setAction(ACTION_ALERT_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ_ALARM, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 8000, INTERVAL_MS, pi);
    }

    public static void cancelTick(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent it = new Intent(ctx, AlertAlarmReceiver.class)
                .setAction(ACTION_ALERT_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ_ALARM, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    /** 闹钟触发：启动一次预警检查服务（系统闹钟场景允许后台启动） */
    public static void startCheck(Context ctx) {
        ctx.startService(new Intent(ctx, AlertWatchService.class));
    }
}
