package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 每日闹钟触发：启动天气简报服务。
 * v9.87：改为前台服务启动（API 26+ startForegroundService）——
 * 普通 startService 在 Android 8+ 后台启动限制下可能被系统拒绝（后台不通知的主因之一）；
 * SpeakService 内部以前台占位通知为启动凭证，正式简报用独立 ID 普通通知，
 * 服务结束后占位移除、简报保留，不会出现旧版「推送几秒后消失」。
 * 闹钟改为一次性精确闹钟，每次触发后立即重排下一天（等效每日循环）。
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // 触发后重排下一次（一次性闹钟 → 每日循环）
        WeatherReporter.ensureScheduled(context);
        // v9.88.3：记录今日已播报（心跳补播报据此跳过）
        WeatherReporter.markReported(context);
        Intent svc = new Intent(context, SpeakService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(svc);
        } else {
            context.startService(svc);
        }
    }
}
