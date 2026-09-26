package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机自启动：恢复每日定时天气闹钟 + 预警监控周期闹钟（需用户在系统设置中允许自启动）。
 *  v9.78 后预警监控已改为 AlarmManager 周期闹钟（无常驻服务），这里仅恢复调度。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(a);
        if (boot) {
            WeatherReporter.ensureScheduled(context);
            // v9.88.3：恢复后台心跳（每 15 分钟：刷缓存 + 补播报 + 查预警，全部静默）
            CacheRefresher.ensureRunning(context);
            // v10.1.2：persisted 任务重启后由系统自动恢复（不需要用户打开应用）
            BackgroundTasks.ensureJob(context);
        }
    }
}
