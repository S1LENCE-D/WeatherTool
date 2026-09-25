package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 15 分钟后台心跳：① 拉取最新天气写缓存 ② 定时播报错过补发 ③ 气象预警检查。
 *
 * <p><b>v10.1 重构</b>：三件事**全部在本接收器的 goAsync 线程里完成，不再启动任何服务**。
 * 旧实现在这里 `startForegroundService`（补播报）与 `startService`（预警检查）——
 * 两者都是从后台启动服务：Android 8+ 禁普通后台服务、Android 12+ 禁后台启前台服务，
 * 会抛 {@code IllegalStateException} / {@code ForegroundServiceStartNotAllowedException}，
 * 而原代码没有捕获：结果是**后台进程周期性崩溃**，同一接收器里排在后面的预警检查
 * 永远执行不到，补播报还会反复重试重崩（「定时提醒不工作」的根因之一）。
 *
 * <p>广播窗口（后台 60 秒）足够覆盖一次网络请求；异常全部吞掉，下一轮心跳自会重试。
 */
public class CacheRefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null) return;
        if (!CacheRefresher.ACTION_REFRESH.equals(intent.getAction())) return;
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // ① 用上次成功坐标拉最新天气（内部已写缓存）
                    WeatherCache.Data d = WeatherCache.load(context);
                    if (d != null) {
                        WeatherCenter.get().fetchWeather(context, d.lat, d.lng, d.city);
                    }
                    // ② 补播报（今天已送达会自动跳过；失败留给下一轮）
                    WeatherReporter.catchUpReport(context);
                    // ③ 预警检查（开关关闭时内部直接跳过）
                    AlertChecker.check(context);
                } catch (Throwable ignored) {
                    // 失败静默跳过：下一轮心跳 / 回前台补刷兜底
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }
}
