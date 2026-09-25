package com.simpleweather.app;

import android.content.Context;

import org.json.JSONObject;

/**
 * 每日天气简报：一次「拉最新天气 → 发通知 → 记账」的完整动作。
 *
 * <p><b>v10.1 重构</b>：不再启动任何服务（原 {@code SpeakService} 已删除）。
 * 旧实现的三个失败面：
 * <ol>
 *   <li>从广播接收器（后台）启动服务：Android 8+ 禁普通后台服务、Android 12+ 禁后台
 *       启前台服务，会抛 {@code IllegalStateException} /
 *       {@code ForegroundServiceStartNotAllowedException}，且原代码未捕获 → 后台进程崩溃；</li>
 *   <li>前台服务的占位通知会随 {@code stopSelf} 被系统移除，为此要多维护一个通知 ID 与
 *       一套「占位→正式」的替换流程；</li>
 *   <li>{@code startForeground} 之后 5 秒内没把通知发出去就会被判 ANR。</li>
 * </ol>
 * 现在改由调用方**在自己的后台线程里直接调用** {@link #run}：不碰服务、不碰前台通知，
 * 异常一律吞掉并返回 false。调用方（每日闹钟接收器 / 15 分钟心跳 / 主页「立即播报」）
 * 都已有 goAsync 或线程上下文，后台广播窗口（60 秒）足够覆盖一次网络请求。
 */
public final class ReportRunner {

    private ReportRunner() { }

    /**
     * 拉取最新天气并推送每日简报。**必须在子线程调用**（内部有网络请求）。
     *
     * @return true = 通知已送出并记账；false = 拉取或发送失败（心跳会自动重试，
     *         且**不会**写「今日已播报」标记，保证当天还有补发机会）
     */
    public static boolean run(Context ctx) {
        try {
            // 坐标策略与旧服务一致：手动城市优先，否则用最近一次前台定位
            double lat, lng;
            String city;
            if (WeatherReporter.hasManualCity(ctx)) {
                lat = WeatherReporter.manualLat(ctx);
                lng = WeatherReporter.manualLng(ctx);
                city = WeatherReporter.manualCityName(ctx);
            } else {
                lat = WeatherReporter.lat(ctx);
                lng = WeatherReporter.lng(ctx);
                city = WeatherReporter.city(ctx);
            }
            JSONObject json = WeatherCenter.get().fetchWeather(ctx, lat, lng, city);
            String text = buildText(json, city);
            Notifier.notifyReport(ctx, text);
            // 真的送出去了，才记「今日已播报」——旧代码在发送之前就写标记，
            // 一旦失败当天再也不会补（这也是「定时提醒不工作」的成因之一）
            WeatherReporter.markReported(ctx);
            WeatherReporter.recordOk(ctx);
            Diag.i("ReportRunner: 简报已发送");
            return true;
        } catch (Throwable t) {
            Diag.i("ReportRunner: 失败 " + t);
            return false;
        }
    }

    /** 组装中文天气简报文本（原 SpeakService.buildText 迁移至此） */
    static String buildText(JSONObject json, String city) throws Exception {
        JSONObject cur = json.getJSONObject("current");
        int code = cur.getInt("weather_code");
        int t = (int) Math.round(cur.getDouble("temperature_2m"));
        int feels = (int) Math.round(cur.getDouble("apparent_temperature"));
        int hum = cur.getInt("relative_humidity_2m");
        int wind = (int) Math.round(cur.getDouble("wind_speed_10m"));
        JSONObject daily = json.getJSONObject("daily");
        int max = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
        int min = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
        String sr = WeatherApi.hhmm(daily.getJSONArray("sunrise").getString(0));
        String ss = WeatherApi.hhmm(daily.getJSONArray("sunset").getString(0));
        String c = (city == null || city.isEmpty()) ? "" : city + "，";
        return c + "现在气温" + t + "度，" + WeatherApi.text(code)
                + "。体感温度" + feels + "度，湿度百分之" + hum
                + "，风速每小时" + wind + "公里。今天最高" + max
                + "度，最低" + min + "度。日出" + sr + "，日落" + ss + "。";
    }
}
