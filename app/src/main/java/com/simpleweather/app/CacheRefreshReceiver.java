package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * v9.88.3：后台心跳接收器（每 15 分钟）。
 * ① 拉取最新天气写缓存；② 定时播报错过补发；③ 预警每次心跳都查（开关开启时）。
 * goAsync + 子线程：网络请求 1~3 秒，补播/补查仅启动短时服务（毫秒级），
 * 10 秒广播窗口内完成；全部静默，无常驻痕迹。
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
                    WeatherCache.Data d = WeatherCache.load(context);
                    if (d == null) return;   // 从未成功拉取过，等主页首次刷新
                    // 用上次成功坐标拉最新天气；内部 WeatherCache.save 已写缓存
                    WeatherCenter.get().fetchWeather(context, d.lat, d.lng, d.city);
                } catch (Exception ignored) {
                    // 失败静默跳过：下一轮心跳 / 回前台补刷兜底
                } finally {
                    pr.finish();
                }
            }
        }).start();
        // v9.88.3：补播报（已播报则自动跳过）；预警每次心跳都查一次（开关开着时）
        WeatherReporter.maybeCatchUpReport(context);
        if (AlertWatcher.enabled(context)) {
            AlertWatcher.startCheck(context);
        }
    }
}
