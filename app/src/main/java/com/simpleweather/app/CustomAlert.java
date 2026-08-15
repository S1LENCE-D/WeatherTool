package com.simpleweather.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * v9.87test：自定义气象提醒。
 *
 * 用户可设定温度（高 / 低）、湿度（高 / 低）、紫外线（高）阈值，
 * 每次天气数据刷新后（前台 / 后台 / 闹钟统一经 WeatherCenter）检查当前值，
 * 超阈值则推送通知。
 *
 * 阈值存字符串（空串 = 不启用该项），避免「0」这类合法值无法区分。
 * 整体 1 小时冷却：每小时内最多提醒一次，无论几项超限合并成一条。
 */
public final class CustomAlert {

    private static final String PREFS = "custom_alert_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_T_HIGH = "temp_high";
    private static final String KEY_T_LOW = "temp_low";
    private static final String KEY_H_HIGH = "hum_high";
    private static final String KEY_H_LOW = "hum_low";
    private static final String KEY_UV_HIGH = "uv_high";
    /** 整体冷却：每小时内最多提醒一次 */
    private static final long COOL_MS = 60L * 60 * 1000;
    private static final String KEY_LAST = "last_alert";

    private CustomAlert() { }

    public static boolean enabled(Context ctx) {
        return sp(ctx).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context ctx, boolean on) {
        sp(ctx).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    public static String get(Context ctx, String key) {
        return sp(ctx).getString(key, "");
    }

    public static void put(Context ctx, String key, String value) {
        sp(ctx).edit().putString(key, value).apply();
    }

    /** 阈值键（供设置页按固定顺序渲染） */
    public static String[] keys() {
        return new String[]{KEY_T_HIGH, KEY_T_LOW, KEY_H_HIGH, KEY_H_LOW, KEY_UV_HIGH};
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** 刷新后检查当前天气是否超阈值，超则提醒（每小时内最多一条） */
    public static void check(Context ctx, JSONObject json) {
        if (ctx == null || json == null || !enabled(ctx)) return;
        JSONObject cur = json.optJSONObject("current");
        if (cur == null) return;
        double t = cur.optDouble("temperature_2m", Double.NaN);
        double h = cur.optDouble("relative_humidity_2m", Double.NaN);
        double uv = cur.optDouble("uv_index", Double.NaN);

        List<String> hits = new ArrayList<>();
        Double th = parse(get(ctx, KEY_T_HIGH));
        if (th != null && t > th) hits.add("气温 " + fmt(t) + "° 已超过 " + fmt(th) + "°");
        Double tl = parse(get(ctx, KEY_T_LOW));
        if (tl != null && t < tl) hits.add("气温 " + fmt(t) + "° 已低于 " + fmt(tl) + "°");
        Double hh = parse(get(ctx, KEY_H_HIGH));
        if (hh != null && h > hh) hits.add("湿度 " + fmt(h) + "% 已超过 " + fmt(hh) + "%");
        Double hl = parse(get(ctx, KEY_H_LOW));
        if (hl != null && h < hl) hits.add("湿度 " + fmt(h) + "% 已低于 " + fmt(hl) + "%");
        Double uh = parse(get(ctx, KEY_UV_HIGH));
        if (uh != null && uv > uh) hits.add("紫外线指数 " + fmt(uv) + " 已超过 " + fmt(uh));

        if (hits.isEmpty()) return;

        SharedPreferences prefs = sp(ctx);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST, 0);
        if (now - last < COOL_MS) return;
        prefs.edit().putLong(KEY_LAST, now).apply();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(hits.get(i));
        }
        Notifier.notifyCustomAlert(ctx, sb.toString());
    }

    private static Double parse(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "0";
        double r = Math.round(v * 10) / 10.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
