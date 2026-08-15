package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机自启动：恢复每日定时天气闹钟 + 预警监控周期闹钟（需用户在系统设置中允许自启动）。
 *  v9.78 后预警监控已改为 AlarmManager 周期闹钟（无常驻服务），这里仅恢复调度。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            WeatherReporter.ensureScheduled(context);
            // v9.70/v9.78：预警开关开着则恢复 30 分钟周期闹钟（无常驻服务）
            AlertWatcher.ensureRunning(context);
            // v9.87：恢复每小时后台缓存刷新（有成功缓存即开启）
            CacheRefresher.ensureRunning(context);
            KeepAliveManager.ensureRunning(context);
        }
    }
}
