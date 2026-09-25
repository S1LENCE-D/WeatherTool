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
 *  - 三项工作在同一个 goAsync 线程内完成，**不启动任何服务**：
 *    旧版在这里 startService / startForegroundService，被 Android 8+/12+ 的后台
 *    限制拒绝并抛异常（未捕获 → 后台崩溃，功能静默失效）。
 *  - 无通知无权限：纯静默更新，用户无感；
 *  - 用上次成功坐标（不重新定位），保证广播 10 秒窗口内完成；
 *  - 检查完成即静默结束，无常驻进程、无通知栏痕迹。
 */
public final class CacheRefresher {

    public static final String ACTION_REFRESH = "com.simpleweather.app.CACHE_REFRESH";
    /** v9.87test：15 分钟一次（移除前台常驻通知后，靠高频率闹钟补偿保活弱化） */
    public static final long INTERVAL_MS = 15L * 60 * 1000;
    private static final int REQ_REFRESH = 3;   // 与每日闹钟(0)、预警闹钟(2)区分

    private CacheRefresher() { }

    /** 应用启动 / 开机：只要曾成功拉取过天气（有缓存）就开启后台刷新 */
    public static void ensureRunning(Context ctx) {
        if (WeatherCache.load(ctx) != null) schedule(ctx);
    }

    /** 注册 15 分钟周期心跳（非精确：Doze/省电下会被系统延后，属预期行为） */
    public static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent it = new Intent(ctx, CacheRefreshReceiver.class)
                .setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ_REFRESH, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // 首轮延迟 60 秒，避开 App 刚启动时的定位/首刷高峰
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 60_000, INTERVAL_MS, pi);
    }
}
