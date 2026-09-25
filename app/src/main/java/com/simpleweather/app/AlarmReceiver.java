package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 每日闹钟触发：拉最新天气并推送简报。
 *
 * <p><b>v10.1 重构</b>：不再启动前台服务（原 SpeakService 已删除）。
 * 旧写法 {@code startForegroundService()} 只在「精确闹钟广播」这一条路径上才有后台启动
 * 豁免；一旦闹钟被系统降级成非精确、或从其它入口触发，就会抛
 * {@code ForegroundServiceStartNotAllowedException}（原代码未捕获 → 后台崩溃）。
 * 现在改为 {@code goAsync()} + 子线程直接完成（广播窗口 60 秒，一次网络请求足够），
 * 无论从哪条路径进来都不会被系统拒绝。
 *
 * <p>注意顺序：先重排下一天的闹钟（保证链条不断），再做简报；
 * 简报是否成功由 {@link ReportRunner} 自行记账，失败时留给 15 分钟心跳补发。
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        // 触发后重排下一次（一次性闹钟 → 每日循环）
        WeatherReporter.ensureScheduled(context);
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ReportRunner.run(context);
                } catch (Throwable ignored) {
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }
}
