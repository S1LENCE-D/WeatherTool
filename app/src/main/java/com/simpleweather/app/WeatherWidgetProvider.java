package com.simpleweather.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * v9.21：桌面小组件公共基类（不注册，仅提供渲染与刷新逻辑）。
 * 两个注册子类各自固定布局，不再按尺寸切换布局——
 * v9.20 曾按 minWidth 阈值在 2x2/2x4 布局间切换，拉伸时新布局高度超出
 * 格子实际高度，部分启动器直接报“载入失败”，v9.21 彻底移除该机制：
 *   WeatherWidgetProvider2x2 -> widget_2x2（当日实时）
 *   WeatherWidgetProvider2x4 -> widget_2x4（今日 + 未来 5 日）
 * 拉伸只放大内容区域；深浅色仍随主题自适应。
 * 注意：图标用 Unicode emoji（系统字体自带），不能用 WeatherApi.icon 的
 * Material 字体字符（小组件无法加载 assets 字体，会显示成方块）。
 */
public class WeatherWidgetProvider extends AppWidgetProvider {

    /** 主页更新成功后调用：同步刷新两类小组件（线程安全） */
    public static void updateAll(Context context) {
        refreshAll(context, WeatherWidgetProvider2x2.class, R.layout.widget_2x2);
        refreshAll(context, WeatherWidgetProvider2x4.class, R.layout.widget_2x4);
    }

    private static void refreshAll(Context context, Class<?> cls, int layout) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, cls));
            for (int id : ids) refresh(context, mgr, id, layout);
        } catch (Exception ignored) { }
    }

    /** 子类 onUpdate / onAppWidgetOptionsChanged 调用 */
    protected static void refresh(final Context context, final AppWidgetManager mgr,
                                  final int id, final int layout) {
        // v9.27：手动查询的城市优先（缓存参考点与拉新坐标都用手动城市）
        final boolean manual = WeatherReporter.hasManualCity(context);
        // 读缓存：新鲜 + 位置一致（与最近一次成功定位距离 <50km）直接渲染，
        // 避免换地区后小组件仍显示旧城市（v9.22 增加位置校验）
        WeatherCache.Data d = WeatherCache.load(context);
        if (d != null && WeatherCache.fresh(d) && sameArea(context, d, manual)) {
            render(context, mgr, id, d, layout);
            return;
        }
        // 缓存缺失/过期/位置已变：后台重新拉最新天气（手动城市免定位）
        final WeatherCache.Data fallback = d;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    double lat, lng;
                    String city;
                    if (manual) {
                        // v9.27：手动城市直接用其坐标，不走定位
                        lat = WeatherReporter.manualLat(context);
                        lng = WeatherReporter.manualLng(context);
                        city = WeatherReporter.manualCityName(context);
                    } else {
                        // v9.22：用 Locator 拿当前位置（lastKnown/IP 缓存窗口已大幅缩短，
                        // 换地区后能及时得到新坐标），不再盲目沿用 WeatherReporter 旧坐标
                        Location loc = new Locator(context).locateFast();
                        if (loc == null) {
                            // 定位失败：退回缓存渲染（有的话）
                            if (fallback != null) render(context, mgr, id, fallback, layout);
                            return;
                        }
                        lat = loc.getLatitude();
                        lng = loc.getLongitude();
                        city = WeatherReporter.city(context);
                        if (city == null || city.isEmpty()
                                || distKm(lat, lng, WeatherReporter.lat(context),
                                          WeatherReporter.lng(context)) > 50) {
                            // 换地区了（或没有城市名）：重新反查城市
                            city = WeatherApi.reverseCity(lat, lng);
                        }
                    }
                    // v9.79：统一走 WeatherCenter（拉取 + 写缓存）
                    JSONObject json = WeatherCenter.get()
                            .fetchWeather(context, lat, lng, city);
                    // 同步上次成功定位，保证主页/定时通知/小组件坐标一致（防错位）
                    WeatherReporter.saveLocation(context, lat, lng, city);
                    WeatherCache.Data d2 = WeatherCenter.get().freshCache(context);
                    render(context, mgr, id, d2, layout);
                } catch (Exception ignored) {
                    // v9.28：拉新失败时回退旧缓存渲染，避免小组件卡住不更新
                    if (fallback != null) render(context, mgr, id, fallback, layout);
                }
            }
        }).start();
    }

    /** 缓存坐标是否与最近一次成功定位（或手动城市）一致（50km 内视为同一地区） */
    private static boolean sameArea(Context context, WeatherCache.Data d, boolean manual) {
        try {
            double rlat = manual ? WeatherReporter.manualLat(context)
                                 : WeatherReporter.lat(context);
            double rlng = manual ? WeatherReporter.manualLng(context)
                                 : WeatherReporter.lng(context);
            return distKm(d.lat, d.lng, rlat, rlng) < 50;
        } catch (Exception e) {
            return true;   // 取不到参考点时按一致处理（直接渲染缓存）
        }
    }
    /** 球面距离（km） */
    protected static double distKm(double la1, double lo1, double la2, double lo2) {
        double R = 6371.0;
        double dLat = Math.toRadians(la2 - la1);
        double dLng = Math.toRadians(lo2 - lo1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    /** 按布局渲染：layout == R.layout.widget_2x2 -> 当日实时；否则 2x4 多日 */
    private static void render(Context context, AppWidgetManager mgr, int id,
                               WeatherCache.Data d, int layout) {
        if (d == null || d.json == null) return;
        try {
            JSONObject j = new JSONObject(d.json);
            RemoteViews views = new RemoteViews(context.getPackageName(), layout);

            boolean dark = Theme.isDark(context);
            views.setInt(layout == R.layout.widget_2x2 ? R.id.widgetRoot2 : R.id.widgetRoot4,
                    "setBackgroundResource",
                    dark ? R.drawable.bg_widget_dark : R.drawable.bg_widget_light);
            int cPrimary = Theme.textPrimary(context);
            int cSecondary = Theme.textSecondary(context);
            int cAccent = Theme.accent(context);

            JSONObject cur = j.optJSONObject("current");
            JSONObject daily = j.optJSONObject("daily");
            int code = 0, temp = 0;
            boolean day = true;
            if (cur != null) {
                code = cur.optInt("weather_code", 0);
                day = cur.optInt("is_day", 1) == 1;
                temp = (int) Math.round(cur.optDouble("temperature_2m", 0));
            }
            String city = (d.city != null && !d.city.isEmpty()) ? d.city : "我的位置";
            String time = "· " + new SimpleDateFormat("HH:mm", Locale.US)
                    .format(new Date(d.ts));

            // ---- 两类布局共用的头部 ----
            views.setTextViewText(R.id.wCity, city);
            views.setTextViewText(R.id.wTime, time);
            views.setTextColor(R.id.wCity, cPrimary);
            views.setTextColor(R.id.wTime, cSecondary);

            if (layout == R.layout.widget_2x4) {
                // ---- 2x4：今日概览 + 未来 5 日 ----
                views.setTextViewText(R.id.wTodayIcon, emoji(code, day));
                views.setTextViewText(R.id.wTodayDesc,
                        WeatherApi.text(code) + " · " + temp + "°");
                views.setTextColor(R.id.wTodayDesc, cPrimary);
                views.setTextColor(R.id.wTodayRange, cAccent);
                // v9.24：实时详情行（湿度/风/云量/UV，Material 矢量图标）
                fillExtra(views, context, cur, true);
                if (daily != null) {
                    JSONArray times = daily.optJSONArray("time");
                    JSONArray wcodes = daily.optJSONArray("weather_code");
                    JSONArray tmax = daily.optJSONArray("temperature_2m_max");
                    JSONArray tmin = daily.optJSONArray("temperature_2m_min");
                    int n = Math.min(5, times == null ? 0 : times.length());
                    for (int i = 0; i < 5; i++) {
                        int row = rowId(i), dayId = dayId(i), iconId = iconId(i), hiId = hiId(i);
                        if (i < n) {
                            views.setViewVisibility(row, View.VISIBLE);
                            views.setTextViewText(dayId, dayLabel(i, times.optString(i, "")));
                            views.setTextViewText(iconId,
                                    emoji(wcodes == null ? 0 : wcodes.optInt(i, 0), true));
                            int hi = tmax == null ? 0
                                    : (int) Math.round(tmax.optDouble(i, 0));
                            int lo = tmin == null ? 0
                                    : (int) Math.round(tmin.optDouble(i, 0));
                            views.setTextViewText(hiId, "↑" + hi + "°  ↓" + lo + "°");
                            views.setTextColor(dayId, cSecondary);
                            views.setTextColor(hiId, cPrimary);
                            if (i == 0) {
                                views.setTextViewText(R.id.wTodayRange,
                                        "↑" + hi + "° ↓" + lo + "°");
                            }
                        } else {
                            views.setViewVisibility(row, View.GONE);
                        }
                    }
                }
            } else {
                // ---- 2x2：当日实时 ----
                views.setTextViewText(R.id.wTemp, temp + "°");
                views.setTextColor(R.id.wTemp, cPrimary);
                views.setTextViewText(R.id.wIcon, emoji(code, day));
                views.setTextViewText(R.id.wDesc, WeatherApi.text(code));
                views.setTextColor(R.id.wDesc, cSecondary);
                // v9.24：体感 + 湿度 详情行（2x2 空间有限，精简两项）
                fillExtra(views, context, cur, false);
            }

            // 点击小组件打开 App
            Intent open = new Intent(context, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(layout == R.layout.widget_2x2
                    ? R.id.widgetRoot2 : R.id.widgetRoot4, pi);
            mgr.updateAppWidget(id, views);
        } catch (Exception ignored) { }
    }

    // ---------- 工具 ----------

    /**
     * v9.24：小组件实时详情行。full=true（2x4）显示 湿度/风/云量/UV 四项；
     * full=false（2x2）只显示 体感 + 湿度。图标用从 MaterialIcons-Regular.ttf
     * 提取的矢量 drawable（RemoteViews 无法加载 assets 字体，会变方块），
     * tint 跟随主题文字色，与 App 内 Material 字形完全一致（Google UI 标准）。
     * 数据缺失时对应图标+数值一并隐藏，避免出现 "--" 占位。
     */
    private static void fillExtra(RemoteViews views, Context context,
                                  JSONObject cur, boolean full) {
        if (cur == null) {
            views.setViewVisibility(R.id.wExtraRow, View.GONE);
            return;
        }
        views.setViewVisibility(R.id.wExtraRow, View.VISIBLE);
        int c = Theme.textSecondary(context);
        int hum = cur.optInt("relative_humidity_2m", -1);
        double wind = cur.optDouble("wind_speed_10m", -1);
        int cloud = cur.optInt("cloud_cover", -1);
        double uv = cur.optDouble("uv_index", -1);
        if (full) {
            setPair(views, R.id.wIcHum, R.id.wHum, R.drawable.w_ic_hum,
                    hum < 0 ? null : hum + "%", c);
            setPair(views, R.id.wIcWind, R.id.wWind, R.drawable.w_ic_wind,
                    wind < 0 ? null : Math.round(wind) + "km/h", c);
            setPair(views, R.id.wIcCloud, R.id.wCloud, R.drawable.w_ic_cloud,
                    cloud < 0 ? null : cloud + "%", c);
            setPair(views, R.id.wIcUv, R.id.wUv, R.drawable.w_ic_uv,
                    uv < 0 ? null : String.format(Locale.US, "UV %.0f", uv), c);
        } else {
            double feels = cur.optDouble("apparent_temperature", Double.NaN);
            views.setTextViewText(R.id.wFeels,
                    Double.isNaN(feels) ? "" : "体感 " + Math.round(feels) + "°");
            views.setTextColor(R.id.wFeels, c);
            setPair(views, R.id.wIcHum, R.id.wHum, R.drawable.w_ic_hum,
                    hum < 0 ? null : hum + "%", c);
        }
    }

    /** 图标 + 数值 成对设置；text 为 null 时隐藏整对 */
    private static void setPair(RemoteViews views, int icId, int tvId,
                                int drawable, String text, int color) {
        boolean show = text != null;
        views.setViewVisibility(icId, show ? View.VISIBLE : View.GONE);
        views.setViewVisibility(tvId, show ? View.VISIBLE : View.GONE);
        if (show) {
            views.setImageViewResource(icId, drawable);
            views.setInt(icId, "setColorFilter", color);
            views.setTextViewText(tvId, text);
            views.setTextColor(tvId, color);
        }
    }

    /** 天气码 -> Unicode emoji（小组件专用；系统字体自带字形） */
    protected static String emoji(int code, boolean day) {
        switch (code) {
            case 0: case 1: return day ? "☀️" : "🌙";
            case 2: return "⛅";
            case 3: return "☁️";
            case 45: case 48: return "🌫️";
            case 51: case 53: case 55:
            case 56: case 57: return "🌦️";
            case 61: case 63: case 65:
            case 66: case 67: return "🌧️";
            case 71: case 73: case 75:
            case 77: return "🌨️";
            case 80: case 81: case 82: return "🌧️";
            case 85: case 86: return "🌨️";
            case 95: case 96: case 99: return "⛈️";
            default: return day ? "☀️" : "🌙";
        }
    }

    /** 预报日期标签：今天 / 明天 / 周X */
    protected static String dayLabel(int idx, String iso) {
        if (idx == 0) return "今天";
        if (idx == 1) return "明天";
        try {
            String s = iso.length() > 10 ? iso.substring(0, 10) : iso;
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s);
            return new SimpleDateFormat("EEE", Locale.CHINA).format(d);   // 周六
        } catch (Exception e) {
            return "";
        }
    }

    private static int rowId(int i) {
        return new int[]{R.id.wRow0, R.id.wRow1, R.id.wRow2, R.id.wRow3, R.id.wRow4}[i];
    }

    private static int dayId(int i) {
        return new int[]{R.id.wDay0, R.id.wDay1, R.id.wDay2, R.id.wDay3, R.id.wDay4}[i];
    }

    private static int iconId(int i) {
        return new int[]{R.id.wIcon0, R.id.wIcon1, R.id.wIcon2, R.id.wIcon3, R.id.wIcon4}[i];
    }

    private static int hiId(int i) {
        return new int[]{R.id.wHi0, R.id.wHi1, R.id.wHi2, R.id.wHi3, R.id.wHi4}[i];
    }
}
