package com.simpleweather.app;

import android.content.Context;

import org.json.JSONObject;

/**
 * v9.79：天气数据仓库 —— WeatherSource 的标准实现（单例）。
 * 统一「拉取 + 写缓存」链路：
 *   WeatherApi.fetch() -> WeatherCache.save()
 * 主页刷新、定时推送、桌面小组件共用此实现，保证取数口径一致；
 * 后续若换数据源（自建服务/多源聚合），只需替换本实现。
 */
public final class WeatherCenter implements WeatherSource {

    private static WeatherCenter instance;

    public static WeatherCenter get() {
        if (instance == null) instance = new WeatherCenter();
        return instance;
    }

    private WeatherCenter() { }

    @Override
    public JSONObject fetchWeather(Context ctx, double lat, double lng, String city)
            throws Exception {
        JSONObject json = WeatherSources.fetch(ctx, lat, lng);
        WeatherCache.save(ctx, json.toString(),
                city == null ? "" : city, lat, lng);
        // v9.87test：自定义气象提醒（温度 / 湿度 / UV 超阈值）
        CustomAlert.check(ctx, json);
        return json;
    }

    @Override
    public WeatherCache.Data freshCache(Context ctx) {
        WeatherCache.Data d = WeatherCache.load(ctx);
        if (d == null || !WeatherCache.fresh(d)) return null;
        return d;
    }
}
