package com.simpleweather.app;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

/**
 * 定时天气通知服务（v9.81：改普通服务）：
 * 1) 强制重新定位（GPS/网络/IP 并行）-> 2) 拉取最新天气 -> 3) 推送天气简报通知
 * -> 4) 数秒后自动停止。
 * 由 AlarmReceiver（每日定时，闹钟触发有后台启动豁免）或 MainActivity（立即推送，App 在前台）触发。
 * 不再使用前台服务：前台通知会随服务 stopSelf 被系统移除（表现为推送几秒后消失），
 * 普通通知没有该生命周期问题。
 * v9.16：推送前必定重新定位 + 拉最新天气（不念旧数据），并把坐标与天气写回缓存。
 * v9.75：通知构建统一走 Notifier（渠道/样式集中管理）。
 */
public class SpeakService extends Service {

    private final Handler timeout = new Handler(Looper.getMainLooper());
    private final Runnable stopTask = new Runnable() {
        @Override
        public void run() {
            finishSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Notifier.ensureChannels(this);
        // v9.87：API 26+ 前台服务启动凭证——startForegroundService 后必须 5 秒内
        // startForeground，否则系统判 ANR。占位通知走独立 ID（ID_FG），
        // 最终简报走 ID_REPORT 普通通知，两者互不影响：
        // 服务 stopSelf 时 stopForeground 只移除占位，简报通知得以保留。
        if (Build.VERSION.SDK_INT >= 26) {
            startForeground(Notifier.ID_FG, Notifier.buildReport(this, "正在获取天气…", true));
        } else {
            Notifier.notifyReport(this, "正在获取天气…", true);
        }
    }

    /** v9.87：统一收工——移除前台占位通知后自停（正式简报为普通通知，保留） */
    private void finishSelf() {
        if (Build.VERSION.SDK_INT >= 26) stopForeground(true);
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text;
                try {
                    // v9.88.3：后台无法可靠定位（GPS 超时退化为 IP 粗定位，导致
                    // 播报位置偏移）——强制使用「最后一次打开 APP 的定位」
                    // （MainActivity.render 每次成功都会 saveLocation 写缓存），
                    // 天气数据仍每次实时拉取，只是坐标固定为最近一次前台定位。
                    double lat, lng;
                    String city;
                    if (WeatherReporter.hasManualCity(SpeakService.this)) {
                        lat = WeatherReporter.manualLat(SpeakService.this);
                        lng = WeatherReporter.manualLng(SpeakService.this);
                        city = WeatherReporter.manualCityName(SpeakService.this);
                    } else {
                        lat = WeatherReporter.lat(SpeakService.this);
                        lng = WeatherReporter.lng(SpeakService.this);
                        city = WeatherReporter.city(SpeakService.this);
                    }
                    // v9.79：统一走 WeatherCenter（拉取 + 写缓存，后台数据保持新鲜）
                    JSONObject json = WeatherCenter.get()
                            .fetchWeather(SpeakService.this, lat, lng, city);
                    text = buildText(json, city);
                } catch (Exception e) {
                    text = "抱歉，天气获取失败，请检查网络。";
                }
                final String ft = text;
                timeout.removeCallbacks(stopTask);
                timeout.postDelayed(stopTask, 5000);   // 通知发出后 5 秒收工
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        // 同 ID 更新：占位通知 → 最终简报
                        Notifier.notifyReport(SpeakService.this, ft, false);
                    }
                });
            }
        }).start();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        timeout.removeCallbacks(stopTask);
        if (Build.VERSION.SDK_INT >= 26) stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** 组装中文天气简报文本（通知内容） */
    private String buildText(JSONObject json, String city) throws Exception {
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
