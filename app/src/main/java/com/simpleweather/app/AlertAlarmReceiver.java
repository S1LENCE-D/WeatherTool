package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * v9.87：预警周期闹钟接收器。
 * 每 30 分钟由 AlertWatcher 注册的一次性精确闹钟触发，
 * 启动一次预警检查服务（短时运行、自停，通知栏无常驻通知），
 * 并立即续排下一轮（精确闹钟无自动重复，必须手动续排）。
 */
public class AlertAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Diag.i("AlertAlarmReceiver.onReceive action=" + intent.getAction());
        if (!AlertWatcher.ACTION_ALERT_TICK.equals(intent.getAction())) return;
        Diag.i("tick 收到，startCheck + 续排 enabled=" + AlertWatcher.enabled(context));
        AlertWatcher.startCheck(context);
        // 续排下一轮：开关已关时 scheduleTick 内不检查开关，
        // 由服务侧（startCheck -> AlertWatchService）自行自停；此处仅在开关开着时续排
        if (AlertWatcher.enabled(context)) {
            AlertWatcher.scheduleTick(context);
        }
    }
}
