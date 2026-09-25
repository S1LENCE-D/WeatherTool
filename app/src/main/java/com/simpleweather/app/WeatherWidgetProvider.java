package com.simpleweather.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 桌面小组件渲染基类（不注册，仅提供取数与绘制逻辑）。
 *
 * <p><b>v10.1 重写要点</b>
 * <ul>
 *   <li><b>统一布局</b>：单日与多日两个 Provider 共用 {@code R.layout.widget} 与同一套
 *       渲染代码，差异只在设计基准尺寸（{@link WidgetSize}）与主块字号上，
 *       因此同尺寸下的对齐、留白、层级完全一致。</li>
 *   <li><b>尺寸驱动的内容阶梯</b>：不再"把一套内容等比放大"，而是按可用尺寸决定
 *       显示哪些块、预报显示几行（见 {@link WidgetSize}）。拉伸时内容生长而非被拉扯，
 *       余量摊成块间距与行距，四个方向都不会空荡或被挤爆。</li>
 *   <li><b>真·高斯模糊底</b>：底图由 {@link WidgetGlass} 生成（缩小 → 三次 box blur
 *       → 放大 → 圆角 → 蒙版），并按底子平均亮度决定深字/浅字与蒙版方向，
 *       保证任何壁纸与深浅主题下文字都有足够对比。</li>
 *   <li><b>纯色矢量图标</b>：天气图标改用从 MaterialIcons 提取的单色矢量
 *       （{@code w_wx_*}），运行时按主题 on-surface / on-surface-variant 着色，
 *       不再是 emoji（emoji 在不同 ROM 上字形不一致，且无法着色）。</li>
 *   <li><b>缩放即重排</b>：{@code onAppWidgetOptionsChanged} 先用缓存立刻重排
 *       （不等网络），再走常规刷新，拖动改变大小时不会出现"卡着不动"。</li>
 * </ul>
 */
public class WeatherWidgetProvider extends AppWidgetProvider {

    /** 两个 Provider 共用布局 */
    protected static final int LAYOUT = R.layout.widget;

    /** 主页更新成功后调用：同步刷新两类小组件 */
    public static void updateAll(Context context) {
        refreshAll(context, WeatherWidgetProvider2x2.class, false);
        refreshAll(context, WeatherWidgetProvider2x4.class, true);
    }

    private static void refreshAll(Context context, Class<?> cls, boolean listMode) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, cls));
            for (int id : ids) refresh(context, mgr, id, listMode);
        } catch (Exception ignored) { }
    }

    /** 子类 onUpdate 调用 */
    protected static void refresh(final Context context, final AppWidgetManager mgr,
                                  final int id, final boolean listMode) {
        // v9.27：手动查询的城市优先（缓存参考点与拉新坐标都用手动城市）
        final boolean manual = WeatherReporter.hasManualCity(context);
        // 读缓存：新鲜 + 位置一致（与最近一次成功定位距离 <50km）直接渲染，
        // 避免换地区后小组件仍显示旧城市（v9.22 增加位置校验）
        WeatherCache.Data d = WeatherCache.load(context);
        if (d != null && WeatherCache.fresh(d) && sameArea(context, d, manual)) {
            render(context, mgr, id, d, listMode);
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
                            if (fallback != null) render(context, mgr, id, fallback, listMode);
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
                    render(context, mgr, id, d2, listMode);
                } catch (Exception ignored) {
                    // v9.28：拉新失败时回退旧缓存渲染，避免小组件卡住不更新
                    if (fallback != null) render(context, mgr, id, fallback, listMode);
                }
            }
        }).start();
    }

    /**
     * v10.1：尺寸变化（拖动改大小）专用入口——先用现有缓存立刻按新尺寸重排，
     * 再走常规刷新流程。避免"拖动时界面滞后/空白"，也让缩放中的每一档都有反馈。
     */
    protected static void refreshOnResize(Context context, AppWidgetManager mgr,
                                          int id, boolean listMode) {
        try {
            WeatherCache.Data d = WeatherCache.load(context);
            if (d != null) render(context, mgr, id, d, listMode);
        } catch (Throwable ignored) { }
        refresh(context, mgr, id, listMode);
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

    // ---------- 渲染 ----------

    private static void render(Context context, AppWidgetManager mgr, int id,
                               WeatherCache.Data d, boolean listMode) {
        if (d == null || d.json == null) return;
        try {
            JSONObject j = new JSONObject(d.json);
            // 尺寸档：没有 options（launcher 首帧未上报）时自动回落到设计基准
            Bundle opts = mgr.getAppWidgetOptions(id);
            if (opts == null) opts = new Bundle();   // 个别 launcher 首帧返回 null
            WidgetSize sc = WidgetSize.compute(
                    opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                    opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
                    opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
                    opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
                    listMode,
                    context.getResources().getConfiguration().fontScale);

            boolean dark = Theme.isDark(context);
            float density = context.getResources().getDisplayMetrics().density;
            JSONObject cur = j.optJSONObject("current");
            JSONObject daily = j.optJSONObject("daily");
            int code = 0, temp = 0;
            boolean day = true;
            if (cur != null) {
                code = cur.optInt("weather_code", 0);
                day = cur.optInt("is_day", 1) == 1;
                temp = (int) Math.round(cur.optDouble("temperature_2m", 0));
            }

            // ① 毛玻璃底：生成成功则用它决定深/浅字；失败回退静态 drawable
            WidgetGlass.Result glass = WidgetGlass.get(context, sc.w, sc.h, code, day, dark);
            boolean lightFrame = glass != null ? glass.lightFrame : !dark;

            int cPrimary, cSecondary, cAccent;
            if (lightFrame) {           // 底子偏亮 → 深字
                cPrimary = 0xFF16202B;
                cSecondary = 0xA616202B;
                cAccent = 0xFF2F5D8A;
            } else {                    // 底子偏暗 → 浅字
                cPrimary = 0xFFFFFFFF;
                cSecondary = 0xB3FFFFFF;
                cAccent = 0xFFB9D4F0;
            }

            RemoteViews views = new RemoteViews(context.getPackageName(), LAYOUT);

            // ② 背景层与玻璃边线
            if (glass != null) {
                views.setImageViewBitmap(R.id.wBg, glass.bitmap);
                // 关键：有玻璃图时根布局必须透明，否则 bg_widget_* 会在玻璃下面再垫一层，
                // 桌面就透不出来了（透明感会被这层吃掉）
                views.setInt(R.id.widgetRoot, "setBackgroundResource",
                        android.R.color.transparent);
            } else {
                // 生成失败：回退到静态玻璃 drawable（此时才需要它当底）
                views.setViewVisibility(R.id.wBg, View.GONE);
                views.setInt(R.id.widgetRoot, "setBackgroundResource",
                        dark ? R.drawable.bg_widget_dark : R.drawable.bg_widget_light);
            }
            views.setInt(R.id.wGlassEdge, "setBackgroundResource",
                    lightFrame ? R.drawable.w_glass_edge_light : R.drawable.w_glass_edge_dark);

            // ③ 根内边距：随尺寸档统一缩放
            int pad = sc.padDp;
            setPad(views, R.id.wContent, pad, pad, pad, pad, density);

            // ④ 头部
            String city = (d.city != null && !d.city.isEmpty()) ? d.city : "我的位置";
            views.setTextViewText(R.id.wCity, city);
            views.setTextViewText(R.id.wTime, "· " + new SimpleDateFormat("HH:mm", Locale.US)
                    .format(new Date(d.ts)));
            views.setTextColor(R.id.wCity, cPrimary);
            views.setTextColor(R.id.wTime, cSecondary);
            views.setTextViewTextSize(R.id.wCity, TypedValue.COMPLEX_UNIT_SP, 12f * sc.scale);
            views.setTextViewTextSize(R.id.wTime, TypedValue.COMPLEX_UNIT_SP, 10f * sc.scale);

            // ⑤ 主块：图标 + 温度（单日给大温度，多日给紧凑概览，保持两种组件的性格）
            // 主块（图标 + 大温度）。两处约束：
            //   ① 高：尺寸富余时按 heroBoost 放大（WidgetSize 已把这份高度计入预算）；
            //   ② 宽：放大后的"图标 + 温度文本"必须放得下——2x3/2x4 只有 110dp 宽，
            //      之前只按高度放大，温度被顶出右边界，于是"26°"只剩个"2"。
            //      所以这里用真实字宽量一遍，超宽就收图标、再按剩余宽度收字号。
            float heroK = sc.scale * (1f + sc.heroBoost);
            float iconBase = WidgetSize.iconBase(listMode);
            float tempBase = WidgetSize.tempBase(listMode);
            String tempText = temp + "°";
            float avail = sc.w - 2f * sc.padDp - 6f;          // 减掉图标与文字的间距
            float iconDp = iconBase * heroK;
            if (Build.VERSION.SDK_INT < 31) iconDp = WidgetSize.ICON_HERO;  // 旧系统沿布局固定尺寸
            iconDp = Math.min(iconDp, Math.max(iconBase, avail * 0.34f));
            float tempSp = tempBase * heroK;
            float need = textWidthDp(context, tempSp, tempText);
            float rest = Math.max(8f, avail - iconDp);
            if (need > rest) tempSp *= rest / need;            // 按剩余宽度等比收字号

            views.setImageViewResource(R.id.wIconImg, wxIcon(code, day));
            views.setInt(R.id.wIconImg, "setColorFilter", cPrimary);
            sizeIcon(views, R.id.wIconImg, iconDp);
            views.setTextViewText(R.id.wTemp, tempText);
            views.setTextColor(R.id.wTemp, cPrimary);
            views.setTextViewTextSize(R.id.wTemp, TypedValue.COMPLEX_UNIT_SP, tempSp);

            // ⑥ 描述 + 今日升降
            views.setTextViewText(R.id.wDesc, WeatherApi.text(code));
            views.setTextColor(R.id.wDesc, cSecondary);
            views.setTextViewTextSize(R.id.wDesc, TypedValue.COMPLEX_UNIT_SP, 11f * sc.scale);
            // 描述行右侧：正常放今日升降；极矮尺寸下详情行放不下，就改放体感，
            // 免得这一行空着、也不白丢一项信息（icon+温度已表达天气，描述不至于被顶掉）
            String range = todayRange(daily);
            if (sc.slotsA <= 0 && cur != null) {
                double feels = cur.optDouble("apparent_temperature", Double.NaN);
                if (!Double.isNaN(feels)) range = "体感 " + Math.round(feels) + "°";
            }
            views.setViewVisibility(R.id.wRange, range == null ? View.GONE : View.VISIBLE);
            views.setTextColor(R.id.wRange, cAccent);
            views.setTextViewTextSize(R.id.wRange, TypedValue.COMPLEX_UNIT_SP, 11f * sc.scale);
            if (range != null) views.setTextViewText(R.id.wRange, range);
            blockGap(views, R.id.wDescRow, sc, density);

            // ⑦ 详情两行（按尺寸档给的名额逐项装入）
            fillExtras(views, cur, sc, cPrimary, cSecondary);

            // ⑧ 日出日落
            String sun = sunMoon(daily);
            views.setViewVisibility(R.id.wSunMoon, (sc.sunMoon && sun != null)
                    ? View.VISIBLE : View.GONE);
            views.setTextColor(R.id.wSunMoon, cSecondary);
            views.setTextViewTextSize(R.id.wSunMoon, TypedValue.COMPLEX_UNIT_SP, 10f * sc.scale);
            if (sun != null) views.setTextViewText(R.id.wSunMoon, sun);
            // 分隔线：其下方还有内容（日出日落 / 预报）时才画
            setDivider(views, (sc.sunMoon && sun != null) || sc.rows > 0, lightFrame);

            // ⑨ 预报块（单日组件尺寸够大时同样会长出预报，避免大面积留白）
            fillForecast(views, daily, sc, lightFrame, cPrimary, cSecondary, density);

            // ⑩ 逐块留白：把尺寸余量摊开，四个方向都不空荡
            blockGap(views, R.id.wHeader, sc, density);
            blockGap(views, R.id.wHero, sc, density);
            blockGap(views, R.id.wExtraRow, sc, density);
            blockGap(views, R.id.wExtraRow2, sc, density);
            blockGap(views, R.id.wSunMoon, sc, density);

            // ⑪ 点击打开 App
            Intent open = new Intent(context, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(context, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widgetRoot, pi);

            mgr.updateAppWidget(id, views);
        } catch (Throwable ignored) { }
    }

    /** 块间额外留白（上下各一份），用于吸收尺寸余量 */
    private static void blockGap(RemoteViews views, int blockId, WidgetSize sc,
                                 float density) {
        if (sc.gapDp <= 0) return;
        setPad(views, blockId, 0, sc.gapDp, 0, sc.gapDp, density);
    }

    /**
     * 统一的内边距下发。注意：RemoteViews 的 int 版 {@code setViewPadding} 单位是
     * <b>像素</b>（官方文档明确写 padding in pixels），直接传 dp 值在高密度屏上会明显偏小
     * ——旧版本正是这么传的。这里统一换算：Android 12+ 用带单位的重载，更低版本
     * 自己乘屏幕密度。
     */
    private static void setPad(RemoteViews views, int id, int left, int top,
                               int right, int bottom, float density) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                views.setViewPadding(id, left, top, right, bottom,
                        TypedValue.COMPLEX_UNIT_DIP);
                return;
            } catch (Throwable ignored) { }
        }
        views.setViewPadding(id, Math.round(left * density), Math.round(top * density),
                Math.round(right * density), Math.round(bottom * density));
    }

    /**
     * 量一段文字在"当前系统字体设置"下的宽度(dp)。用于把大温度夹进可用宽度 ——
     * 估算字宽容易失手（不同 ROM 的字体、用户改过字号、负号与度数符号宽度各异），
     * 直接问 Paint 最稳。
     *
     * @return 宽度(dp)；量不出来时返回 0，调用方据此跳过夹取
     */
    private static float textWidthDp(Context ctx, float sp, String s) {
        try {
            float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp,
                    ctx.getResources().getDisplayMetrics());
            Paint p = new Paint();
            p.setTextSize(px);
            return p.measureText(s) / ctx.getResources().getDisplayMetrics().density;
        } catch (Throwable t) {
            return 0f;
        }
    }

    /** API 31+ 可动态指定视图尺寸；旧系统保持布局内的固定值（图标仍是纯色矢量） */
    private static void sizeIcon(RemoteViews views, int id, float dp) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                views.setViewLayoutWidth(id, dp, TypedValue.COMPLEX_UNIT_DIP);
                views.setViewLayoutHeight(id, dp, TypedValue.COMPLEX_UNIT_DIP);
            } catch (Throwable ignored) { }
        }
    }

    /**
     * 详情两行。名额来自尺寸档：
     * 行一 体感/湿度/风（最多 3 项），行二 云量/UV（最多 2 项）。
     * 按布局内的项序装入，数据缺失的项跳过且不占名额，因此不会出现空洞或 "--"。
     */
    private static void fillExtras(RemoteViews views, JSONObject cur, WidgetSize sc,
                                   int cPrimary, int cSecondary) {
        boolean anyA;
        int shown = 0;
        if (cur != null) {
            // 行一 —— 体感（纯文本项）
            double feels = cur.optDouble("apparent_temperature", Double.NaN);
            boolean feelsOk = !Double.isNaN(feels) && shown < sc.slotsA;
            views.setViewVisibility(R.id.wFeels, feelsOk ? View.VISIBLE : View.GONE);
            if (feelsOk) {
                views.setTextViewText(R.id.wFeels, "体感 " + Math.round(feels) + "°");
                views.setTextColor(R.id.wFeels, cSecondary);
                views.setTextViewTextSize(R.id.wFeels, TypedValue.COMPLEX_UNIT_SP,
                        10f * sc.scale);
                shown++;
            }
            int hum = cur.optInt("relative_humidity_2m", -1);
            if (hum >= 0 && shown < sc.slotsA) {
                setPair(views, R.id.wIcHum, R.id.wHum, R.drawable.w_ic_hum, hum + "%",
                        cSecondary, sc.scale);
                shown++;
            } else {
                hidePair(views, R.id.wIcHum, R.id.wHum);
            }
            double wind = cur.optDouble("wind_speed_10m", -1);
            if (wind >= 0 && shown < sc.slotsA) {
                setPair(views, R.id.wIcWind, R.id.wWind, R.drawable.w_ic_wind,
                        Math.round(wind) + "km/h", cSecondary, sc.scale);
                shown++;
            } else {
                hidePair(views, R.id.wIcWind, R.id.wWind);
            }
            anyA = shown > 0;
        } else {
            hidePair(views, R.id.wIcHum, R.id.wHum);
            hidePair(views, R.id.wIcWind, R.id.wWind);
            views.setViewVisibility(R.id.wFeels, View.GONE);
            anyA = false;
        }
        views.setViewVisibility(R.id.wExtraRow, anyA ? View.VISIBLE : View.GONE);

        // 行二 —— 云量 / UV
        int shown2 = 0;
        if (cur != null) {
            int cloud = cur.optInt("cloud_cover", -1);
            if (cloud >= 0 && shown2 < sc.slotsB) {
                setPair(views, R.id.wIcCloud, R.id.wCloud, R.drawable.w_ic_cloud,
                        cloud + "%", cSecondary, sc.scale);
                shown2++;
            } else {
                hidePair(views, R.id.wIcCloud, R.id.wCloud);
            }
            double uv = cur.optDouble("uv_index", -1);
            if (uv >= 0 && shown2 < sc.slotsB) {
                setPair(views, R.id.wIcUv, R.id.wUv, R.drawable.w_ic_uv,
                        String.format(Locale.US, "UV %.0f", uv), cSecondary, sc.scale);
                shown2++;
            } else {
                hidePair(views, R.id.wIcUv, R.id.wUv);
            }
        } else {
            hidePair(views, R.id.wIcCloud, R.id.wCloud);
            hidePair(views, R.id.wIcUv, R.id.wUv);
        }
        views.setViewVisibility(R.id.wExtraRow2, shown2 > 0 ? View.VISIBLE : View.GONE);
    }

    /** 分隔线：只在预报块上方、且预报块可见时才画，颜色随底子深浅 */
    private static void setDivider(RemoteViews views, boolean show, boolean lightFrame) {
        views.setViewVisibility(R.id.wDivider, show ? View.VISIBLE : View.GONE);
        if (show) {
            views.setInt(R.id.wDivider, "setBackgroundResource",
                    lightFrame ? R.drawable.divider_setting_light
                               : R.drawable.divider_setting_dark);
        }
    }

    /** 预报块：显示几行由尺寸档决定，每行包含 日期 / 图标 / 降水概率 / 高低温 */
    private static void fillForecast(RemoteViews views, JSONObject daily, WidgetSize sc,
                                     boolean lightFrame, int cPrimary, int cSecondary,
                                     float density) {
        int rows = sc.rows;
        if (rows <= 0 || daily == null) {
            views.setViewVisibility(R.id.wForecast, View.GONE);
            return;
        }
        JSONArray times = daily.optJSONArray("time");
        JSONArray codes = daily.optJSONArray("weather_code");
        JSONArray tmax = daily.optJSONArray("temperature_2m_max");
        JSONArray tmin = daily.optJSONArray("temperature_2m_min");
        JSONArray pops = daily.optJSONArray("precipitation_probability_max");
        int n = Math.min(rows, times == null ? 0 : times.length());
        if (n <= 0) {
            views.setViewVisibility(R.id.wForecast, View.GONE);
            return;
        }
        views.setViewVisibility(R.id.wForecast, View.VISIBLE);
        float rowScale = sc.scale * (sc.rowsCompact ? 0.85f : 1f);
        for (int i = 0; i < 5; i++) {
            int rowId = ROW[i], dayId = DAY[i], icId = FICON[i], popId = POP[i],
                    hiId = HI[i], loId = LO[i];
            if (i >= n) {
                views.setViewVisibility(rowId, View.GONE);
                continue;
            }
            views.setViewVisibility(rowId, View.VISIBLE);
            views.setTextViewText(dayId, dayLabel(i, times.optString(i, "")));
            views.setTextColor(dayId, cSecondary);
            views.setTextViewTextSize(dayId, TypedValue.COMPLEX_UNIT_SP, 12f * rowScale);
            views.setImageViewResource(icId, wxIcon(codes == null ? 0 : codes.optInt(i, 0), true));
            views.setInt(icId, "setColorFilter", cSecondary);
            sizeIcon(views, icId, (sc.rowsCompact ? 18f : 20f) * sc.scale);
            int hi = tmax == null ? 0 : (int) Math.round(tmax.optDouble(i, 0));
            int lo = tmin == null ? 0 : (int) Math.round(tmin.optDouble(i, 0));
            views.setTextViewText(hiId, hi + "°");
            views.setTextViewText(loId, lo + "°");
            views.setTextColor(hiId, cPrimary);
            views.setTextColor(loId, cSecondary);
            views.setTextViewTextSize(hiId, TypedValue.COMPLEX_UNIT_SP, 12f * rowScale);
            views.setTextViewTextSize(loId, TypedValue.COMPLEX_UNIT_SP, 12f * rowScale);
            int pop = pops == null ? -1 : (int) Math.round(pops.optDouble(i, -1));
            // 窄尺寸下收起降水概率列，优先保证"日期+高低温"读得清
            boolean showPop = pop >= 0 && !sc.rowsCompact;
            views.setViewVisibility(popId, showPop ? View.VISIBLE : View.GONE);
            if (showPop) {
                views.setTextViewText(popId, pop + "%");
                views.setTextColor(popId, cSecondary);
                views.setTextViewTextSize(popId, TypedValue.COMPLEX_UNIT_SP, 11f * rowScale);
            }
            // 行距：基础行高 + 尺寸档摊出的额外内边距
            setPad(views, rowId, 0, 2 + sc.rowPadDp, 0, 2 + sc.rowPadDp, density);
        }
    }

    /** 图标 + 数值 成对设置（纯色矢量 + 主题色 tint，Material You 单色规范） */
    private static void setPair(RemoteViews views, int icId, int tvId, int drawable,
                                String text, int color, float scale) {
        views.setViewVisibility(icId, View.VISIBLE);
        views.setViewVisibility(tvId, View.VISIBLE);
        views.setImageViewResource(icId, drawable);
        views.setInt(icId, "setColorFilter", color);
        sizeIcon(views, icId, 14f * scale);
        views.setTextViewText(tvId, text);
        views.setTextColor(tvId, color);
        views.setTextViewTextSize(tvId, TypedValue.COMPLEX_UNIT_SP, 10f * scale);
    }

    private static void hidePair(RemoteViews views, int icId, int tvId) {
        views.setViewVisibility(icId, View.GONE);
        views.setViewVisibility(tvId, View.GONE);
    }

    /** 今日升降（预报缺失返回 null） */
    private static String todayRange(JSONObject daily) {
        try {
            double hi = daily.getJSONArray("temperature_2m_max").optDouble(0, Double.NaN);
            double lo = daily.getJSONArray("temperature_2m_min").optDouble(0, Double.NaN);
            if (Double.isNaN(hi) || Double.isNaN(lo)) return null;
            return "↑" + Math.round(hi) + "° ↓" + Math.round(lo) + "°";
        } catch (Exception e) {
            return null;
        }
    }

    /** 日出日落文案（缺失返回 null） */
    private static String sunMoon(JSONObject daily) {
        try {
            return "日出 " + WeatherApi.hhmm(daily.getJSONArray("sunrise").getString(0))
                    + " · 日落 " + WeatherApi.hhmm(daily.getJSONArray("sunset").getString(0));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * WMO 天气码 -> 单色矢量图标（提取自 MaterialIcons，与 App 内字形同源）。
     * 小组件无法加载 assets 字体，所以用矢量化后的同一套字形；
     * 按主题 on-surface / on-surface-variant 着色，符合 Material You 单色图标规范。
     */
    protected static int wxIcon(int code, boolean day) {
        switch (code) {
            case 0: case 1: return day ? R.drawable.w_wx_sun : R.drawable.w_wx_moon;
            case 2: return R.drawable.w_wx_cloud;
            case 3: return R.drawable.w_wx_cloud_q;
            case 45: case 48: return R.drawable.w_wx_fog;
            case 51: case 53: case 55:
            case 56: case 57: return R.drawable.w_wx_drizzle;
            case 61: case 63: case 65:
            case 66: case 67:
            case 80: case 81: case 82: return R.drawable.w_wx_rain;
            case 71: case 73: case 75:
            case 77: case 85: case 86: return R.drawable.w_wx_snow;
            case 95: case 96: case 99: return R.drawable.w_wx_storm;
            default: return day ? R.drawable.w_wx_sun : R.drawable.w_wx_moon;
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

    // ---------- 视图 id 表（RemoteViews 不支持动态查 id，这里集中登记，改布局时只改一处）----------
    private static final int[] ROW = {R.id.wRow0, R.id.wRow1, R.id.wRow2, R.id.wRow3, R.id.wRow4};
    private static final int[] DAY = {R.id.wDay0, R.id.wDay1, R.id.wDay2, R.id.wDay3, R.id.wDay4};
    private static final int[] FICON = {R.id.wFIcon0, R.id.wFIcon1, R.id.wFIcon2,
            R.id.wFIcon3, R.id.wFIcon4};
    private static final int[] POP = {R.id.wPop0, R.id.wPop1, R.id.wPop2, R.id.wPop3, R.id.wPop4};
    private static final int[] HI = {R.id.wHi0, R.id.wHi1, R.id.wHi2, R.id.wHi3, R.id.wHi4};
    private static final int[] LO = {R.id.wLo0, R.id.wLo1, R.id.wLo2, R.id.wLo3, R.id.wLo4};
}
