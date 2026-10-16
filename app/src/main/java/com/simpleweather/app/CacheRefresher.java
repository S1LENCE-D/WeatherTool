package com.simpleweather.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * v9.88.3：后台心跳（每 15 分钟静默检查一次，检查后进程自然回收）。
 *
 * 原 v9.87 为「后台缓存自动刷新」；v9.88.3 起作为统一的静默心跳：
 * ① 拉取最新天气写缓存；
 * ② 定时播报错过补发（见 WeatherReporter.catchUpReport）；
 * ③ 气象预警检查（见 AlertChecker.check，开关开启时）。
 *
 * 实现要点：
 *  - 三项工作在同一个 goAsync 线程内完成，不启动任何服务：
 *    旧版在这里 startService / startForegroundService，被 Android 8+/12+ 的后台
 *    限制拒绝并抛异常（未捕获 → 后台崩溃，功能静默失效）。
 *  - 无通知无权限：纯静默更新，用户无感；
 *  - 用上次成功坐标（不重新定位），保证广播 10 秒窗口内完成；
 *  - 检查完成即静默结束，无常驻进程、无通知栏痕迹。
 */
public final class CacheRefresher {

    public static final String ACTION_REFRESH = "com.simpleweather.app.CACHE_REFRESH";
    /** 正常模式：15 分钟一次（Doze/省电下会被系统延后，属预期行为） */
    public static final long INTERVAL_MS = 15L * 60 * 1000;

    /**
     * v10.1.3：<b>快速检查模式</b> —— 每 30 秒跑一轮后台流程，<b>仅供验证自启动/后台链路</b>。
     * 周期任务（JobScheduler/WorkManager）最短间隔是 15 分钟，做不了 30 秒，
     * 所以这里改用「一次性闹钟 + 每次触发后自己续下一次」的接力写法；
     * 接力由系统持有，因此划掉应用后仍然会到点唤醒 —— 这样才测得出自启动到底通不通。
     * 代价：费电、频繁请求接口，验证完请关掉。
     */
    public static final long FAST_INTERVAL_MS = 30L * 1000;
    private static final int REQ_REFRESH = 3;   // 与每日闹钟(0)、预警闹钟(2)区分

    private CacheRefresher() { }

    /** 快速检查开关（存 bg_prefs，与后台去重标记同一处） */
    public static boolean fastCheck(Context c) {
        return c.getSharedPreferences("bg_prefs", Context.MODE_PRIVATE)
                .getBoolean("fast_check", false);  // 默认关：只有验证自启动时才手动打开
    }

    public static void setFastCheck(Context c, boolean on) {
        c.getSharedPreferences("bg_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("fast_check", on).apply();
    }

    /** 默认心跳间隔（分钟）。与"快速检查"是两个独立设置项 */
    public static final int DEFAULT_INTERVAL_MIN = 15;

    /** 可选间隔（分钟）：15 / 30 / 60 / 120 */
    public static final int[] INTERVAL_OPTIONS = {15, 30, 60, 120};

    public static int intervalMinutes(Context c) {
        return c.getSharedPreferences("bg_prefs", Context.MODE_PRIVATE)
                .getInt("interval_min", DEFAULT_INTERVAL_MIN);
    }

    /** 自定义间隔允许范围（分钟）。预设之外的值由此输入 */
    public static final int MIN_INTERVAL_MIN = 1;
    public static final int MAX_INTERVAL_MIN = 600;

    public static void setIntervalMinutes(Context c, int minutes) {
        int v = Math.max(MIN_INTERVAL_MIN, Math.min(MAX_INTERVAL_MIN, minutes));
        c.getSharedPreferences("bg_prefs", Context.MODE_PRIVATE)
                .edit().putInt("interval_min", v).apply();
    }

    /** 当前心跳间隔：快速检查优先，否则取用户设置的分钟数 */
    public static long intervalMs(Context c) {
        return fastCheck(c) ? FAST_INTERVAL_MS : (intervalMinutes(c) * 60_000L);
    }

    /** 应用启动 / 开机：只要曾成功拉取过天气（有缓存）就开启后台刷新 */
    public static void ensureRunning(Context ctx) {
        if (WeatherCache.load(ctx) != null) schedule(ctx);
    }

    /** 注册心跳：按当前模式决定用「周期闹钟」还是「一次性接力」 */
    public static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        cancel(ctx);   // 两种模式互斥，先清旧的，避免同时留着两份
        if (fastCheck(ctx)) {
            scheduleNext(ctx, FAST_INTERVAL_MS);
            return;
        }
        Intent it = new Intent(ctx, CacheRefreshReceiver.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ_REFRESH, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // 正常模式：沿用非精确周期闹钟，首轮延迟 60 秒避开启动高峰
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60_000, intervalMs(ctx), pi);
    }

    /**
     * 排下一次心跳（快速检查模式的接力棒）。每次触发后由接收器回调，
     * 保证"这一轮跑完立刻定下一轮"，链路由系统持有、不依赖进程存活。
     */
    public static void scheduleNext(Context ctx, long delayMs) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent it = new Intent(ctx, CacheRefreshReceiver.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ_REFRESH, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + Math.max(delayMs, 5_000L);
        try {
            if (WeatherReporter.canExactAlarms(ctx)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (Throwable t) {
            try { am.set(AlarmManager.RTC_WAKEUP, at, pi); } catch (Throwable ignored) { }
        }
    }

    /** 撤销心跳（切模式时用） */
    public static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent it = new Intent(ctx, CacheRefreshReceiver.class).setAction(ACTION_REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ_REFRESH, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.cancel(pi);
        } catch (Throwable ignored) { }
    }
}
