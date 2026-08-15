package com.simpleweather.app;

import android.content.Context;

import org.json.JSONObject;

/**
 * v9.79：天气数据源接口 —— 天气信息的统一入口。
 * 主页 / 定时推送 / 桌面小组件等所有"拉天气"的调用方，
 * 一律通过该接口取数，不再各自拼 WeatherApi + WeatherCache。
 */
public interface WeatherSource {

    /**
     * 拉取当前天气（需在后台线程调用），成功后自动写入缓存。
     *
     * @param ctx  上下文（写缓存用）
     * @param lat  纬度
     * @param lng  经度
     * @param city 城市名（可为空，缓存兜底）
     * @return 天气 JSON（WeatherApi 原始结构）
     * @throws Exception 网络/解析失败
     */
    JSONObject fetchWeather(Context ctx, double lat, double lng, String city)
            throws Exception;

    /**
     * 读取新鲜缓存（未过期）；无缓存或已过期返回 null。
     */
    WeatherCache.Data freshCache(Context ctx);
}
