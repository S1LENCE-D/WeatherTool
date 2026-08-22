package com.simpleweather.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * v9.88.3：后台心跳（每 15 分钟静默检查一次，检查后进程自然回收）。
 *
 * 原 v9.87 为「后台缓存自动刷新」；v9.88.3 起作为统一的静默心跳：
 * ① 拉取最新天气写缓存（原功能，进程存活时后台信息保持新鲜）；
 * ② 定时播报错过补发（见 WeatherReporter.maybeCatchUpReport）；
 * ③ 预警每次心跳都查一次（见 AlertWatcher.startCheck，开关开启时）。
 *
 * 实现要点：
 *  - 广播接收器内 goAsync + 线程直接拉取（不启动常驻服务，规避
 *    Android 8+ 后台启动限制 / Android 12+ 前台服务限制）；
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

    /** 注册每小时周期闹钟 */
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
