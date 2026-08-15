package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * v9.78：预警周期闹钟接收器。
 * 每 30 分钟由 AlertWatcher 注册的 AlarmManager 触发，
 * 启动一次预警检查服务（短时运行、自停，通知栏无常驻通知）。
 */
public class AlertAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!AlertWatcher.ACTION_ALERT_TICK.equals(intent.getAction())) return;
        AlertWatcher.startCheck(context);
    }
}
