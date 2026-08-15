package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * v9.87：后台缓存刷新闹钟接收器。
 * goAsync + 子线程直接拉取最新天气写缓存（不启动服务，规避后台启动限制），
 * 10 秒广播窗口内完成：fetchWeather 单次网络请求通常 1~3 秒。
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
                    // 失败静默跳过：下一轮闹钟 / 回前台补刷兜底
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }
}
