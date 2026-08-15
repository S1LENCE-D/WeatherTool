package com.simpleweather.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v9.87：多天气源管理器 —— 自定义天气源的核心。
 * 内置 Open-Meteo（默认）/ 和风 / 心知 / 彩云 / 高德 五类源，
 * 统一把各家返回结构归一化为 Open-Meteo 兼容结构（current/hourly/daily），
 * 使主页、定时推送、桌面小组件等消费方无需感知底层数据源差异。
 *
 * 配置持久化在 SharedPreferences "src_pref"：
 *   type = openmeteo | qweather | seniverse | caiyun | amap
 *   key_* = 各源独立保存的 API Key / Token（和风 / 心知 / 彩云 / 高德）
 */
public final class WeatherSources {

    public static final String OPEN_METEO = "openmeteo";
    public static final String QWEATHER = "qweather";
    public static final String SENIVERSE = "seniverse";
    public static final String CAIYUN = "caiyun";
    public static final String AMAP = "amap";

    private static final String PREFS = "src_pref";
    private static final String K_TYPE = "type";
    // v9.89：每个源独立保存 API Key / Token，互不覆盖
    private static final String K_KEY_Q = "key_qweather";
    private static final String K_KEY_S = "key_seniverse";
    private static final String K_KEY_C = "key_caiyun";
    private static final String K_KEY_A = "key_amap";

    // ---------------- 配置读写 ----------------

    public static String type(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(K_TYPE, OPEN_METEO);
    }

    /** 某个源对应的 Key 存储字段名（Open-Meteo 无 key，返回 null） */
    private static String keyField(String type) {
        if (QWEATHER.equals(type)) return K_KEY_Q;
        if (SENIVERSE.equals(type)) return K_KEY_S;
        if (CAIYUN.equals(type)) return K_KEY_C;
        if (AMAP.equals(type)) return K_KEY_A;
        return null;
    }

    /** 读取指定源已保存的 Key / Token（Open-Meteo 返回空串） */
    public static String key(Context c, String type) {
        String f = keyField(type);
        if (f == null) return "";
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(f, "");
    }

    /** 保存源 + 该源对应的 Key（只写当前源字段，其他源保留不动） */
    public static void save(Context c, String type, String key) {
        String t = type == null || type.isEmpty() ? OPEN_METEO : type;
        android.content.SharedPreferences.Editor e =
                c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString(K_TYPE, t);
        String f = keyField(t);
        if (f != null) {
            e.putString(f, key == null ? "" : key.trim());
        }
        e.apply();
    }

    /** 恢复默认源（Open-Meteo，免 Key 开箱即用），清空全部源的 Key */
    public static void reset(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(K_TYPE, OPEN_METEO)
                .putString(K_KEY_Q, "")
                .putString(K_KEY_S, "")
                .putString(K_KEY_C, "")
                .putString(K_KEY_A, "")
                .apply();
    }

    /** 当前源显示名 */
    public static String label(Context c) {
        String t = type(c);
        if (QWEATHER.equals(t)) return "和风天气";
        if (SENIVERSE.equals(t)) return "心知天气";
        if (CAIYUN.equals(t)) return "彩云天气";
        if (AMAP.equals(t)) return "高德天气";
        return "Open-Meteo";
    }

    // ---------------- 主入口 ----------------

    /** 按当前配置拉取归一化天气 JSON（需在后台线程调用） */
    public static JSONObject fetch(Context c, double lat, double lng) throws Exception {
        String t = type(c);
        String k = key(c, t);
        // 需要 Key 的源未填 Key 时回退 Open-Meteo（不报错）
        if (!OPEN_METEO.equals(t) && (k == null || k.trim().isEmpty())) {
            return WeatherApi.fetch(lat, lng);
        }
        if (QWEATHER.equals(t)) return qweather(lat, lng, k);
        if (SENIVERSE.equals(t)) return seniverse(lat, lng, k);
        if (CAIYUN.equals(t)) return caiyun(lat, lng, k);
        if (AMAP.equals(t)) return amap(lat, lng, k);
        return WeatherApi.fetch(lat, lng);   // Open-Meteo
    }

    // ---------------- 配置前测试 ----------------

    /** 用北京坐标真实请求一次，校验能否正确解析出温度与天气描述 */
    public static TestResult test(Context c, String t, String k) {
        try {
            JSONObject j;
            if (QWEATHER.equals(t)) j = qweather(39.9042, 116.4074, k);
            else if (SENIVERSE.equals(t)) j = seniverse(39.9042, 116.4074, k);
            else if (CAIYUN.equals(t)) j = caiyun(39.9042, 116.4074, k);
            else if (AMAP.equals(t)) j = amap(39.9042, 116.4074, k);
            else j = WeatherApi.fetch(39.9042, 116.4074);
            JSONObject cur = j.getJSONObject("current");
            double temp = cur.getDouble("temperature_2m");
            int code = cur.optInt("weather_code", 0);
            return new TestResult(true,
                    Math.round(temp) + "° · " + WeatherApi.text(code), null);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.isEmpty()) msg = e.getClass().getSimpleName();
            return new TestResult(false, null, msg);
        }
    }

    public static class TestResult {
        public final boolean ok;
        public final String summary;
        public final String error;
        public TestResult(boolean ok, String s, String e) {
            this.ok = ok; this.summary = s; this.error = e;
        }
    }

    // ---------------- 和风天气（v7） ----------------

    static JSONObject qweather(double lat, double lng, String key) throws Exception {
        String loc = lng + "," + lat;
        // v9.87test：补充紫外线指数（生活指数 type=5），保持 Open-Meteo 的 UV 功能不缺失
        double uvNow = -1;
        try {
            JSONObject uvJ = WeatherApi.getJsonAuto(
                    "https://devapi.qweather.com/v7/indices/1d?type=5&location=" + loc + "&key=" + key,
                    12000, 12000);
            JSONArray uvd = uvJ.optJSONArray("daily");
            if (uvd != null && uvd.length() > 0) {
                uvNow = qUvLevel(iInt(uvd.getJSONObject(0).optString("level", "0")));
            }
        } catch (Exception ignored) { }
        JSONObject nowJ = WeatherApi.getJsonAuto(
                "https://devapi.qweather.com/v7/weather/now?location=" + loc + "&key=" + key,
                12000, 12000);
        JSONObject n = nowJ.getJSONObject("now");
        JSONObject h24 = WeatherApi.getJsonAuto(
                "https://devapi.qweather.com/v7/weather/24h?location=" + loc + "&key=" + key,
                12000, 12000);
        JSONArray hArr = h24.getJSONArray("hourly");
        JSONObject d7 = WeatherApi.getJsonAuto(
                "https://devapi.qweather.com/v7/weather/7d?location=" + loc + "&key=" + key,
                12000, 12000);
        JSONArray dArr = d7.getJSONArray("daily");

        JSONObject out = new JSONObject();
        out.put("utc_offset_seconds", 28800);   // 国内源按 UTC+8

        JSONObject cur = new JSONObject();
        cur.put("temperature_2m", dbl(n.optString("temp", "0")));
        cur.put("relative_humidity_2m", (int) Math.round(dbl(n.optString("humidity", "0"))));
        cur.put("apparent_temperature",
                dbl(n.optString("feelsLike", n.optString("temp", "0"))));
        cur.put("weather_code", qIconToWmo(iInt(n.optString("icon", "100"))));
        cur.put("wind_speed_10m", dbl(n.optString("windSpeed", "0")));
        cur.put("wind_direction_10m", dbl(n.optString("wind360", "-1")));
        cur.put("is_day", isDayNow() ? 1 : 0);
        cur.put("cloud_cover", (int) Math.round(dbl(n.optString("cloud", "-1"))));
        cur.put("uv_index", uvNow);
        out.put("current", cur);

        JSONObject hourly = new JSONObject();
        JSONArray hTime = new JSONArray(), hTemp = new JSONArray(),
                hCode = new JSONArray(), hPop = new JSONArray(), hUv = new JSONArray();
        for (int i = 0; i < hArr.length(); i++) {
            JSONObject h = hArr.getJSONObject(i);
            String t = h.optString("fxTime", "");
            hTime.put(t.length() >= 16 ? t.substring(0, 16) : t);
            hTemp.put(dbl(h.optString("temp", "0")));
            hCode.put(qIconToWmo(iInt(h.optString("icon", "100"))));
            hPop.put((int) Math.round(dbl(h.optString("pop", "0"))));
            hUv.put(uvNow);
        }
        hourly.put("time", hTime);
        hourly.put("temperature_2m", hTemp);
        hourly.put("weather_code", hCode);
        hourly.put("precipitation_probability", hPop);
        hourly.put("uv_index", hUv);
        out.put("hourly", hourly);

        JSONObject daily = new JSONObject();
        JSONArray dTime = new JSONArray(), dCode = new JSONArray(), dMax = new JSONArray(),
                dMin = new JSONArray(), dSr = new JSONArray(), dSs = new JSONArray(),
                dPop = new JSONArray();
        for (int i = 0; i < dArr.length(); i++) {
            JSONObject d = dArr.getJSONObject(i);
            String date = d.optString("fxDate", "");
            dTime.put(date);
            dCode.put(qIconToWmo(iInt(d.optString("iconDay", "100"))));
            dMax.put(dbl(d.optString("tempMax", "0")));
            dMin.put(dbl(d.optString("tempMin", "0")));
            dSr.put(date + "T" + d.optString("sunrise", "06:00"));
            dSs.put(date + "T" + d.optString("sunset", "18:00"));
            dPop.put((int) Math.round(dbl(d.optString("precip", "0"))));
        }
        daily.put("time", dTime);
        daily.put("weather_code", dCode);
        daily.put("temperature_2m_max", dMax);
        daily.put("temperature_2m_min", dMin);
        daily.put("sunrise", dSr);
        daily.put("sunset", dSs);
        daily.put("precipitation_probability_max", dPop);
        out.put("daily", daily);

        return out;
    }

    /** 和风生活指数紫外线等级（1~5）-> WHO UV 指数近似值（0~11） */
    private static double qUvLevel(int level) {
        switch (level) {
            case 1: return 2.0;
            case 2: return 4.0;
            case 3: return 6.0;
            case 4: return 8.0;
            case 5: return 10.0;
            default: return -1.0;
        }
    }

    // ---------------- 心知天气（v3） ----------------

    static JSONObject seniverse(double lat, double lng, String key) throws Exception {
        String loc = lat + ":" + lng;
        JSONObject nowJ = WeatherApi.getJsonAuto(
                "https://api.seniverse.com/v3/weather/now.json?key=" + key
                        + "&location=" + loc + "&language=zh-Hans&unit=c", 12000, 12000);
        JSONObject n = nowJ.getJSONArray("results").getJSONObject(0).getJSONObject("now");
        JSONObject dayJ = WeatherApi.getJsonAuto(
                "https://api.seniverse.com/v3/weather/daily.json?key=" + key
                        + "&location=" + loc + "&start=0&days=7&language=zh-Hans&unit=c",
                12000, 12000);
        JSONArray dArr = dayJ.getJSONArray("results").getJSONObject(0).getJSONArray("daily");
        JSONObject hourJ = WeatherApi.getJsonAuto(
                "https://api.seniverse.com/v3/weather/hourly.json?key=" + key
                        + "&location=" + loc + "&start=0&hours=24&language=zh-Hans&unit=c",
                12000, 12000);
        JSONArray hArr = hourJ.getJSONArray("results").getJSONObject(0).getJSONArray("hourly");

        JSONObject out = new JSONObject();
        out.put("utc_offset_seconds", 28800);

        JSONObject cur = new JSONObject();
        cur.put("temperature_2m", dbl(n.optString("temperature", "0")));
        cur.put("relative_humidity_2m", (int) Math.round(dbl(n.optString("humidity", "-1"))));
        cur.put("apparent_temperature",
                dbl(n.optString("feels_like", n.optString("temperature", "0"))));
        cur.put("weather_code", seniCodeToWmo(n.optString("code", "0")));
        cur.put("wind_speed_10m", dbl(n.optString("wind_speed", "0")));
        cur.put("wind_direction_10m", dbl(n.optString("wind_direction_degree", "-1")));
        cur.put("is_day", isDayNow() ? 1 : 0);
        cur.put("cloud_cover", (int) Math.round(dbl(n.optString("clouds", "-1"))));
        cur.put("uv_index", -1);
        out.put("current", cur);

        JSONObject hourly = new JSONObject();
        JSONArray hTime = new JSONArray(), hTemp = new JSONArray(),
                hCode = new JSONArray(), hPop = new JSONArray(), hUv = new JSONArray();
        for (int i = 0; i < hArr.length(); i++) {
            JSONObject h = hArr.getJSONObject(i);
            String t = h.optString("time", "");
            hTime.put(t.length() >= 16 ? t.substring(0, 16) : t);
            hTemp.put(dbl(h.optString("temperature", "0")));
            hCode.put(seniCodeToWmo(h.optString("code", "0")));
            hPop.put(0);
            hUv.put(-1);
        }
        hourly.put("time", hTime);
        hourly.put("temperature_2m", hTemp);
        hourly.put("weather_code", hCode);
        hourly.put("precipitation_probability", hPop);
        hourly.put("uv_index", hUv);
        out.put("hourly", hourly);

        JSONObject daily = new JSONObject();
        JSONArray dTime = new JSONArray(), dCode = new JSONArray(), dMax = new JSONArray(),
                dMin = new JSONArray(), dSr = new JSONArray(), dSs = new JSONArray(),
                dPop = new JSONArray();
        for (int i = 0; i < dArr.length(); i++) {
            JSONObject d = dArr.getJSONObject(i);
            String date = d.optString("date", "");
            dTime.put(date);
            dCode.put(seniCodeToWmo(d.optString("code_day", "0")));
            dMax.put(dbl(d.optString("high", "0")));
            dMin.put(dbl(d.optString("low", "0")));
            dSr.put(date + "T06:00");
            dSs.put(date + "T18:00");
            dPop.put((int) Math.round(dbl(d.optString("precip", "0"))));
        }
        daily.put("time", dTime);
        daily.put("weather_code", dCode);
        daily.put("temperature_2m_max", dMax);
        daily.put("temperature_2m_min", dMin);
        daily.put("sunrise", dSr);
        daily.put("sunset", dSs);
        daily.put("precipitation_probability_max", dPop);
        out.put("daily", daily);

        return out;
    }

    // ---------------- 彩云天气（v2.6） ----------------

    static JSONObject caiyun(double lat, double lng, String key) throws Exception {
        String loc = lng + "," + lat;
        JSONObject j = WeatherApi.getJsonAuto(
                "https://api.caiyunapp.com/v2.6/" + key + "/" + loc
                        + "/weather?alert=true&dailysteps=15&hourlysteps=24",
                12000, 12000);
        JSONObject r = j.getJSONObject("result");
        JSONObject rt = r.getJSONObject("realtime");
        long tzSec = j.optLong("tzshift", 28800);   // 彩云按当地时区返回 tzshift

        JSONObject out = new JSONObject();
        out.put("utc_offset_seconds", tzSec);

        // current：湿度 0~1 转 %、风速 km/h、云量 0~1 转 %
        JSONObject cur = new JSONObject();
        cur.put("temperature_2m", rt.optDouble("temperature", 0));
        cur.put("relative_humidity_2m",
                (int) Math.round(rt.optDouble("humidity", 0) * 100));
        cur.put("apparent_temperature",
                rt.optDouble("apparent_temperature", rt.optDouble("temperature", 0)));
        cur.put("weather_code", skyconToWmo(rt.optString("skycon", "CLEAR_DAY")));
        JSONObject wind = rt.optJSONObject("wind");
        cur.put("wind_speed_10m", wind == null ? 0 : wind.optDouble("speed", 0));
        cur.put("wind_direction_10m", wind == null ? -1 : wind.optDouble("direction", -1));
        cur.put("is_day", isDayNow() ? 1 : 0);
        cur.put("cloud_cover",
                (int) Math.round(rt.optDouble("cloudrate", -1) * 100));
        cur.put("uv_index", -1);
        out.put("current", cur);

        // hourly：temperature/skycon/precipitation 均为 [{value|probability, datetime}]
        JSONObject hr = r.optJSONObject("hourly");
        JSONObject hourly = new JSONObject();
        JSONArray hTime = new JSONArray(), hTemp = new JSONArray(),
                hCode = new JSONArray(), hPop = new JSONArray(), hUv = new JSONArray();
        if (hr != null) {
            JSONArray tArr = hr.optJSONArray("temperature");
            JSONArray sArr = hr.optJSONArray("skycon");
            JSONArray pArr = hr.optJSONArray("precipitation");
            int n = tArr == null ? 0 : tArr.length();
            for (int i = 0; i < n; i++) {
                JSONObject tObj = tArr.optJSONObject(i);
                String dt = tObj == null ? "" :
                        tObj.optString("datetime", "").replace(" ", "T");
                hTime.put(dt.length() >= 16 ? dt.substring(0, 16) : dt);
                hTemp.put(tObj == null ? 0 : tObj.optDouble("value", 0));
                String sc = (sArr != null && i < sArr.length())
                        ? sArr.optJSONObject(i).optString("value", "") : "";
                hCode.put(skyconToWmo(sc));
                double prob = 0;
                if (pArr != null && i < pArr.length()) {
                    JSONObject pObj = pArr.optJSONObject(i);
                    prob = pObj == null ? 0 : pObj.optDouble("probability", 0);
                }
                hPop.put((int) Math.round(prob * 100));
                hUv.put(-1);
            }
        }
        hourly.put("time", hTime);
        hourly.put("temperature_2m", hTemp);
        hourly.put("weather_code", hCode);
        hourly.put("precipitation_probability", hPop);
        hourly.put("uv_index", hUv);
        out.put("hourly", hourly);

        // daily：temperature/skycon/astro/precipitation 数组，astro 含日出日落
        JSONObject dr = r.optJSONObject("daily");
        JSONObject daily = new JSONObject();
        JSONArray dTime = new JSONArray(), dCode = new JSONArray(), dMax = new JSONArray(),
                dMin = new JSONArray(), dSr = new JSONArray(), dSs = new JSONArray(),
                dPop = new JSONArray();
        if (dr != null) {
            JSONArray tempArr = dr.optJSONArray("temperature");
            JSONArray skyArr = dr.optJSONArray("skycon");
            JSONArray astroArr = dr.optJSONArray("astro");
            JSONArray precArr = dr.optJSONArray("precipitation");
            int n = tempArr == null ? 0 : tempArr.length();
            for (int i = 0; i < n; i++) {
                JSONObject tObj = tempArr.optJSONObject(i);
                String date = tObj == null ? "" :
                        tObj.optString("date", "").substring(0,
                                Math.min(10, tObj.optString("date", "").length()));
                dTime.put(date);
                dMax.put(tObj == null ? 0 : tObj.optDouble("max", 0));
                dMin.put(tObj == null ? 0 : tObj.optDouble("min", 0));
                String sc = (skyArr != null && i < skyArr.length())
                        ? skyArr.optJSONObject(i).optString("value", "") : "";
                dCode.put(skyconToWmo(sc));
                String sr = "06:00", ss = "18:00";
                if (astroArr != null && i < astroArr.length()) {
                    JSONObject aObj = astroArr.optJSONObject(i);
                    if (aObj != null) {
                        JSONObject srJ = aObj.optJSONObject("sunrise");
                        JSONObject ssJ = aObj.optJSONObject("sunset");
                        if (srJ != null) sr = srJ.optString("time", "06:00");
                        if (ssJ != null) ss = ssJ.optString("time", "18:00");
                    }
                }
                dSr.put(date + "T" + sr);
                dSs.put(date + "T" + ss);
                double prob = 0;
                if (precArr != null && i < precArr.length()) {
                    JSONObject pObj = precArr.optJSONObject(i);
                    prob = pObj == null ? 0 : pObj.optDouble("probability", 0);
                }
                dPop.put((int) Math.round(prob * 100));
            }
        }
        daily.put("time", dTime);
        daily.put("weather_code", dCode);
        daily.put("temperature_2m_max", dMax);
        daily.put("temperature_2m_min", dMin);
        daily.put("sunrise", dSr);
        daily.put("sunset", dSs);
        daily.put("precipitation_probability_max", dPop);
        out.put("daily", daily);

        return out;
    }

    // ---------------- 高德天气（Web 服务） ----------------

    static JSONObject amap(double lat, double lng, String key) throws Exception {
        String loc = lng + "," + lat;   // 高德要求 "经度,纬度"
        JSONObject j = WeatherApi.getJsonAuto(
                "https://restapi.amap.com/v3/weather/weatherInfo?key=" + key
                        + "&city=" + loc + "&extensions=all",
                12000, 12000);

        JSONArray lives = j.optJSONArray("lives");
        JSONArray forecasts = j.optJSONArray("forecasts");

        JSONObject out = new JSONObject();
        out.put("utc_offset_seconds", 28800);   // 高德为国内数据，按 UTC+8

        // current：lives[0] 实况（无风向度数，风力等级转 km/h）
        JSONObject cur = new JSONObject();
        double temp = 0, windKmh = 0;
        double hum = -1;
        String weather = "";
        if (lives != null && lives.length() > 0) {
            JSONObject lv = lives.getJSONObject(0);
            temp = lv.optDouble("temperature", 0);
            hum = lv.optInt("humidity", -1);
            weather = lv.optString("weather", "");
            windKmh = windPowerToKmh(lv.optString("windpower", ""));
        }
        cur.put("temperature_2m", temp);
        cur.put("relative_humidity_2m", (int) Math.round(hum));
        cur.put("apparent_temperature", temp);
        cur.put("weather_code", amapWeatherToWmo(weather));
        cur.put("wind_speed_10m", windKmh);
        cur.put("wind_direction_10m", -1);
        cur.put("is_day", isDayNow() ? 1 : 0);
        cur.put("cloud_cover", -1);
        cur.put("uv_index", -1);
        out.put("current", cur);

        // daily：forecasts[0].casts[]（dayweather/nightweather + daytemp/nighttemp）
        JSONArray casts = null;
        if (forecasts != null && forecasts.length() > 0) {
            casts = forecasts.getJSONObject(0).optJSONArray("casts");
        }
        JSONObject daily = new JSONObject();
        JSONArray dTime = new JSONArray(), dCode = new JSONArray(), dMax = new JSONArray(),
                dMin = new JSONArray(), dSr = new JSONArray(), dSs = new JSONArray(),
                dPop = new JSONArray();
        if (casts != null) {
            for (int i = 0; i < casts.length(); i++) {
                JSONObject cst = casts.getJSONObject(i);
                String date = cst.optString("date", "");
                dTime.put(date);
                dCode.put(amapWeatherToWmo(cst.optString("dayweather", "多云")));
                double hi = cst.optDouble("daytemp", 0);
                double lo = cst.optDouble("nighttemp", 0);
                dMax.put(hi);
                dMin.put(lo);
                dSr.put(date + "T06:00");   // 高德无日出日落，用默认值
                dSs.put(date + "T18:00");
                dPop.put(0);                // 高德无降水概率
            }
        }
        daily.put("time", dTime);
        daily.put("weather_code", dCode);
        daily.put("temperature_2m_max", dMax);
        daily.put("temperature_2m_min", dMin);
        daily.put("sunrise", dSr);
        daily.put("sunset", dSs);
        daily.put("precipitation_probability_max", dPop);
        out.put("daily", daily);

        // hourly：高德无逐小时数据，按当天白天/夜间温度插值 24 段，保证 24h 组件不空
        JSONObject hourly = new JSONObject();
        JSONArray hTime = new JSONArray(), hTemp = new JSONArray(),
                hCode = new JSONArray(), hPop = new JSONArray(), hUv = new JSONArray();
        double dayT = 0, nightT = 0;
        int dayCode = 2;
        if (casts != null && casts.length() > 0) {
            JSONObject c0 = casts.getJSONObject(0);
            dayT = c0.optDouble("daytemp", 0);
            nightT = c0.optDouble("nighttemp", 0);
            dayCode = amapWeatherToWmo(c0.optString("dayweather", "多云"));
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        java.text.SimpleDateFormat fh = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:00", java.util.Locale.US);
        for (int h = 0; h < 24; h++) {
            cal.set(java.util.Calendar.HOUR_OF_DAY, h);
            cal.set(java.util.Calendar.MINUTE, 0);
            hTime.put(fh.format(cal.getTime()));
            hTemp.put(h >= 6 && h <= 18 ? dayT : nightT);
            hCode.put(dayCode);
            hPop.put(0);
            hUv.put(-1);
        }
        hourly.put("time", hTime);
        hourly.put("temperature_2m", hTemp);
        hourly.put("weather_code", hCode);
        hourly.put("precipitation_probability", hPop);
        hourly.put("uv_index", hUv);
        out.put("hourly", hourly);

        return out;
    }

    // ---------------- 工具 ----------------

    static double dbl(String s) {
        try { return s == null ? 0 : Double.parseDouble(s.trim()); }
        catch (Exception e) { return 0; }
    }

    static int iInt(String s) {
        try { return s == null ? 0 : Integer.parseInt(s.trim()); }
        catch (Exception e) { return 0; }
    }

    static boolean isDayNow() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 6 && h < 18;
    }

    /** 和风 icon 码 -> WMO 天气码 */
    static int qIconToWmo(int icon) {
        switch (icon) {
            case 100: case 150: return 0;                       // 晴
            case 101: case 151: return 2;                       // 多云
            case 102: case 152: case 103: case 153: return 1;   // 少云 / 晴间多云
            case 104: return 3;                                 // 阴
            case 300: case 301: case 305: case 314: return 61;  // 阵雨 / 小雨
            case 306: case 315: return 63;                      // 中雨
            case 307: case 310: case 311: case 312:
            case 316: case 317: case 318: case 399: return 65;  // 大雨 / 暴雨
            case 302: case 303: return 95;                      // 雷阵雨
            case 304: return 96;                                // 雷阵雨伴冰雹
            case 309: return 51;                                // 毛毛雨
            case 313: return 66;                                // 冻雨
            case 400: case 404: case 405: case 406:
            case 408: case 499: return 71;                      // 小雪 / 雨夹雪
            case 401: case 409: return 73;                      // 中雪
            case 402: case 403: case 410: return 75;            // 大雪 / 暴雪
            case 407: case 456: case 457: return 85;            // 阵雪
            case 500: case 501: case 509:
            case 510: case 514: case 515: return 45;            // 雾
            case 502: case 511: case 512: case 513: return 3;   // 霾
            case 503: case 504: case 507: case 508: return 3;   // 沙尘
            default: return 2;
        }
    }

    /** 心知天气 code -> WMO 天气码 */
    static int seniCodeToWmo(String code) {
        int c;
        try { c = Integer.parseInt(code); } catch (Exception e) { return 2; }
        switch (c) {
            case 0: return 0;                                   // 晴
            case 1: return 2;                                   // 多云
            case 2: return 3;                                   // 阴
            case 3: return 80;                                  // 阵雨
            case 4: return 95;                                  // 雷阵雨
            case 5: return 96;                                  // 雷阵雨伴冰雹
            case 6: case 14: return 71;                         // 雨夹雪 / 小雪
            case 7: return 61;                                  // 小雨
            case 8: case 21: return 63;                         // 中雨
            case 9: case 10: case 11: case 12:
            case 22: case 23: case 24: case 25: return 65;      // 大雨 / 暴雨
            case 13: return 85;                                 // 阵雪
            case 15: case 26: return 73;                        // 中雪
            case 16: case 17: case 27: case 28: return 75;      // 大雪 / 暴雪
            case 18: case 32: case 33: case 38: return 45;      // 雾 / 浓雾
            case 19: return 66;                                 // 冻雨
            case 20: case 29: case 30: case 31:
            case 34: case 35: case 36: case 37: return 3;       // 沙尘 / 霾
            default: return 2;
        }
    }

    /** 彩云 skycon 天气现象 -> WMO 天气码 */
    static int skyconToWmo(String s) {
        if (s == null) return 2;
        if ("CLEAR_DAY".equals(s) || "CLEAR_NIGHT".equals(s)) return 0;
        if ("PARTLY_CLOUDY_DAY".equals(s) || "PARTLY_CLOUDY_NIGHT".equals(s)) return 2;
        if ("CLOUDY".equals(s)) return 3;
        if ("LIGHT_RAIN".equals(s)) return 61;
        if ("MODERATE_RAIN".equals(s)) return 63;
        if ("HEAVY_RAIN".equals(s) || "STORM_RAIN".equals(s)) return 65;
        if ("FOG".equals(s)) return 45;
        if ("LIGHT_SNOW".equals(s)) return 71;
        if ("MODERATE_SNOW".equals(s)) return 73;
        if ("HEAVY_SNOW".equals(s) || "STORM_SNOW".equals(s)) return 75;
        // 雾霾 / 浮尘 / 沙尘 / 大风 统一按阴（无专用 WMO 码）
        if ("LIGHT_HAZE".equals(s) || "MODERATE_HAZE".equals(s) || "HEAVY_HAZE".equals(s)
                || "DUST".equals(s) || "SAND".equals(s) || "WIND".equals(s)) return 3;
        return 2;
    }

    /** 高德天气文字 -> WMO 天气码（按关键字，冰雹/雷优先判断） */
    static int amapWeatherToWmo(String w) {
        if (w == null) return 2;
        if (w.contains("晴")) return 0;
        if (w.contains("多云")) return 2;
        if (w.contains("阴")) return 3;
        if (w.contains("冰雹")) return 96;      // 雷阵雨并伴有冰雹
        if (w.contains("雷")) return 95;
        if (w.contains("雨夹雪")) return 66;
        if (w.contains("阵雨")) return 80;
        if (w.contains("暴雨") || w.contains("大雨")) return 65;
        if (w.contains("中雨")) return 63;
        if (w.contains("小雨") || w.contains("雨")) return 61;
        if (w.contains("阵雪")) return 85;
        if (w.contains("暴雪") || w.contains("大雪")) return 75;
        if (w.contains("中雪")) return 73;
        if (w.contains("小雪") || w.contains("雪")) return 71;
        if (w.contains("雾")) return 45;
        if (w.contains("霾") || w.contains("沙") || w.contains("尘")) return 3;
        if (w.contains("冻雨")) return 66;
        if (w.contains("风")) return 3;
        return 2;
    }

    /** 高德风力等级（如 "≤3" / "4" / "6-7"）-> 风速 km/h 近似 */
    static double windPowerToKmh(String p) {
        if (p == null) return 0;
        String num = p.replaceAll("[^0-9]", "");
        if (num.isEmpty()) return 0;
        int lv;
        try { lv = Integer.parseInt(num.substring(0, 1)); }
        catch (Exception e) { return 0; }
        switch (lv) {
            case 0: return 1;
            case 1: return 5;
            case 2: return 11;
            case 3: return 19;
            case 4: return 28;
            case 5: return 38;
            case 6: return 50;
            case 7: return 61;
            case 8: return 74;
            case 9: return 88;
            case 10: return 102;
            case 11: return 117;
            default: return 40;
        }
    }
}
