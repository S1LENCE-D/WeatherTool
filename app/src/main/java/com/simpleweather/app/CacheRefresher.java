package com.simpleweather.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * v9.87：后台缓存自动刷新。
 *
 * 解决「后台信息不自己刷新缓存」：原实现靠 MainActivity 的 Handler 定时器
 * （进程存活时每 1 小时刷一次），但进程被系统回收或设备进入 Doze 后
 * Handler 定时器完全不执行，缓存永远停留在最后一次前台刷新。
 *
 * 改为 AlarmManager 每小时触发一次广播，接收器内 goAsync + 线程
 * 直接拉取最新天气并写缓存（WeatherCenter 统一链路）：
 *  - 不启动服务：规避 Android 8+ 后台启动限制 / Android 12+ 前台服务限制；
 *  - 无通知无权限：纯静默更新，用户无感；
 *  - 用上次成功坐标（不重新定位），保证广播 10 秒窗口内完成；
 *  - 拉取失败静默跳过，等下一轮 / 回前台补刷兜底。
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
