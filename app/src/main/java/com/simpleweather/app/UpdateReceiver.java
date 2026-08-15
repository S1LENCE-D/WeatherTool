package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 应用更新恢复（v9.84）：
 * Android 在应用被替换（覆盖安装/更新）时会清除该应用注册的全部闹钟，
 * 导致定时推送与预警监控在更新后静默失效（开关仍显示开启，但不再触发）。
 * 监听 ACTION_MY_PACKAGE_REPLACED（更新完成后系统发给本应用自身的广播），
 * 立即按开关状态恢复两类闹钟：
 *  ① 每日定时推送闹钟（WeatherReporter.ensureScheduled）
 *  ② 预警监控 30 分钟周期闹钟（AlertWatcher.ensureRunning）
 * 与开机恢复（BootReceiver）、启动恢复（MainActivity）互为兜底。
 */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            WeatherReporter.ensureScheduled(context);
            AlertWatcher.ensureRunning(context);
            CacheRefresher.ensureRunning(context);
            KeepAliveManager.ensureRunning(context);
        }
    }
}
