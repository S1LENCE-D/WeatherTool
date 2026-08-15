package com.simpleweather.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Open-Meteo 天气接口（免费、无需 key，全球网格气象数据 —— 国内外同一数据源）
 * 一次请求包含：实时天气 / 24小时逐小时 / 7天预报 / 日出日落
 * timezone=auto：国外城市自动按当地时区返回（显示/通知均为当地时间）
 */
public class WeatherApi {

    public static final String[] WEEK = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    /** 拉取指定经纬度的全量天气 JSON */
    public static JSONObject fetch(double lat, double lng) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + lat
                + "&longitude=" + lng
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,wind_direction_10m,is_day,cloud_cover,uv_index"
                + "&hourly=temperature_2m,weather_code,precipitation_probability,uv_index"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max"
                + "&timezone=auto&forecast_days=7";
        return getJson(url);
    }

    /** 通用 GET 请求 -> JSON（默认 12 秒超时，天气 / 预警主链路用） */
    public static JSONObject getJson(String url) throws Exception {
        return getJson(url, 12000, 12000);
    }

    /** 通用 GET 请求 -> JSON（可指定连接 / 读取超时；IP 定位等对速度敏感的链路用短超时） */
    public static JSONObject getJson(String url, int connectMs, int readMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(connectMs);
        conn.setReadTimeout(readMs);
        conn.setRequestProperty("User-Agent", "WeatherTool/9.62 (Android)");
        BufferedReader in = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line);
        }
        in.close();
        conn.disconnect();
        return new JSONObject(sb.toString());
    }

    /** WMO 天气码 -> 中文描述 */
    public static String text(int code) {
        switch (code) {
            case 0: return "晴";
            case 1: return "基本晴朗";
            case 2: return "多云";
            case 3: return "阴";
            case 45: case 48: return "有雾";
            case 51: case 53: case 55: return "毛毛雨";
            case 56: case 57: return "冻毛毛雨";
            case 61: case 63: case 65: return "雨";
            case 66: case 67: return "冻雨";
            case 71: case 73: case 75: return "雪";
            case 77: return "雪粒";
            case 80: case 81: case 82: return "阵雨";
            case 85: case 86: return "阵雪";
            case 95: case 96: case 99: return "雷暴";
            default: return "未知";
        }
    }

    /** WMO 天气码 -> 列表短标签（含强度：大雨 / 中雨 / 小雪 等，用于 24h 与 7 天列表） */
    public static String label(int code) {
        switch (code) {
            case 0: case 1: return "晴";
            case 2: return "多云";
            case 3: return "阴";
            case 45: case 48: return "雾";
            case 51: case 53: case 55: return "小雨";
            case 56: case 57: return "冻雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 66: case 67: return "冻雨";
            case 71: return "小雪";
            case 73: return "中雪";
            case 75: return "大雪";
            case 77: return "雪粒";
            case 80: return "小阵雨";
            case 81: return "阵雨";
            case 82: return "强阵雨";
            case 85: case 86: return "阵雪";
            case 95: case 96: case 99: return "雷暴";
            default: return "未知";
        }
    }

    /**
     * WMO 天气码 -> Material Icons 字形（谷歌 Material 风格，PUA 码点）。
     * wb_sunny=\uE430 nights_stay=\uEA46 cloud=\uE2BD cloud_queue=\uE2C2
     * foggy=\uE818 grain=\uE3EA water_drop=\uE798 ac_unit=\uEB3B thunderstorm=\uEBDB
     */
    public static String icon(int code, boolean day) {
        switch (code) {
            case 0: case 1: return day ? "\uE430" : "\uEA46";   // 晴：wb_sunny 太阳 / nights_stay 月亮
            case 2: return "\uE2BD";                            // 多云：cloud 实心云
            case 3: return "\uE2C2";                            // 阴：cloud_queue 空心云
            case 45: case 48: return "\uE818";                  // 雾：foggy 云+雾线
            case 51: case 53: case 55: case 56: case 57: return "\uE3EA";  // 毛毛雨/冻毛毛雨：grain
            case 61: case 63: case 65: case 66: case 67: return "\uE798";  // 雨/冻雨：water_drop
            case 71: case 73: case 75: case 77: return "\uEB3B";           // 雪/雪粒：ac_unit 雪花
            case 80: case 81: case 82: return "\uE798";         // 阵雨：water_drop
            case 85: case 86: return "\uEB3B";                  // 阵雪：ac_unit 雪花
            case 95: case 96: case 99: return "\uEBDB";         // 雷暴/雷暴冰雹：thunderstorm 闪电
            default: return day ? "\uE430" : "\uEA46";
        }
    }

    /** 中央气象台实时预警接口。
     *  v9.43：全量拉取 pageSize=2000 一次覆盖当日全部预警（旧版 200 条只含最新，
     *  排在后面的城市永远匹配不到）；带省名时按省过滤拉取（省列表短，500 足够）。 */
    public static final String ALARM_URL_ALL = "https://www.nmc.cn/rest/findAlarm"
            + "?pageNo=1&pageSize=2000&signaltype=&signallevel=";
    public static final String ALARM_URL_PROV = "https://www.nmc.cn/rest/findAlarm"
            + "?pageNo=1&pageSize=500&signaltype=&signallevel=&province=";

    /** 预警结果：local=本地精确匹配（省+市/区县）；national=全国最新（仅兜底展示用） */
    public static class AlarmResult {
        public java.util.List<String[]> local = new java.util.ArrayList<String[]>();
        public java.util.List<String[]> national = new java.util.ArrayList<String[]>();
        public int nationalCount = 0;
    }

    /** 去掉行政区划后缀，如 "赣州市"->"赣州"、"南康区"->"南康" */
    private static String trimSuffix(String s) {
        if (s == null) return "";
        s = s.trim();
        for (String suf : new String[]{"市", "地区", "自治州", "盟",
                "县", "区", "旗", "新区", "林区", "特区"}) {
            if (s.endsWith(suf) && s.length() > 2) {
                return s.substring(0, s.length() - suf.length());
            }
        }
        return s;
    }

    /**
     * 拉取中央气象台实时预警并按「省 + 市/区县」精确过滤；
     * title 固定格式：xxx省xxx市xxx区气象台发布xxx预警信号。
     * 接口异常返回 null。
     *
     * @param city     城市名（如 赣州市 / 北京市），可为空
     * @param province 省名（如 江西省 / 北京市），可为空
     * @param district 区县名（如 南康区），可为空
     */
    public static AlarmResult fetchAlarms(String city, String province, String district) {
        AlarmResult r = new AlarmResult();
        try {
            String prov = province == null ? "" : province.trim();
            // v9.43：城市名先截断「宝安区 · 深圳市」式显示名，再取全名与去后缀简称
            String cityFull = city == null ? "" : city.trim();
            int sp = cityFull.indexOf(" · ");
            if (sp > 0) cityFull = cityFull.substring(0, sp);
            String cityKey = trimSuffix(cityFull);
            String distKey = trimSuffix(district);

            // v9.43：带省名 -> 按省拉取（URL 编码）；无省名 -> 全量拉取一次覆盖
            final String url = prov.length() >= 2
                    ? ALARM_URL_PROV + java.net.URLEncoder.encode(prov, "UTF-8")
                    : ALARM_URL_ALL;

            // v9.45：三重尝试——https 主通道 → http 备用通道 → https 重试
            JSONObject j = null;
            for (int attempt = 0; attempt < 3 && j == null; attempt++) {
                try {
                    j = getJson(attempt == 1
                            ? url.replace("https://", "http://") : url);
                } catch (Exception e) {
                    if (attempt < 2) Thread.sleep(600);
                }
            }
            if (j == null) return null;
            JSONObject page = j.getJSONObject("data").getJSONObject("page");
            r.nationalCount = page.optInt("count", 0);
            JSONArray list = page.getJSONArray("list");

            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                String title = o.optString("title", "");
                String[] item = {title, o.optString("issuetime", ""),
                        o.optString("pic", "")};
                if (r.national.size() < 10) r.national.add(item);

                // v9.43 匹配：全名优先（title 必带完整区划名，如「…宝安区气象台…」），
                // 去后缀简称兜底（「宝安」）。市级名同理（「深圳市」）。
                boolean hit = false;
                if (cityFull.length() >= 2 && title.contains(cityFull)) {
                    hit = true;
                } else if (cityKey.length() >= 2 && title.contains(cityKey)) {
                    hit = true;
                }
                if (!hit && distKey.length() >= 2 && title.contains(distKey)) {
                    hit = true;
                }
                if (hit) r.local.add(item);
            }

            // v9.44：同类型预警只保留最严重一条（等级高优先，同等级取最新发布），按严重度降序
            dedupAlarms(r.local);
        } catch (Exception e) {
            return null;
        }
        return r;
    }

    // ---- v9.44：预警类型 / 等级解析 + 去重 ----

    /** 类型词表：长词在前，避免「大风」抢先匹配到「雷雨大风」 */
    private static final String[] ALARM_TYPES = {
            "雷雨大风", "沙尘暴", "道路结冰", "森林火险", "地质灾害",
            "山洪灾害", "台风", "暴雨", "暴雪", "寒潮", "大风", "高温",
            "干旱", "雷电", "冰雹", "霜冻", "大雾", "霾"
    };
    private static final String[] ALARM_LEVELS = {"红色", "橙色", "黄色", "蓝色", "白色"};

    /** 等级严重度：红 5 > 橙 4 > 黄 3 > 蓝 2 > 白 1 > 无 0 */
    static int alarmLevel(String title) {
        if (title == null) return 0;
        for (int i = 0; i < ALARM_LEVELS.length; i++) {
            if (title.contains(ALARM_LEVELS[i])) return ALARM_LEVELS.length - i;
        }
        return 0;
    }

    /** 预警类型名；未识别归为「其他」 */
    static String alarmType(String title) {
        if (title == null) return "其他";
        for (String t : ALARM_TYPES) {
            if (title.contains(t)) return t;
        }
        return "其他";
    }

    /** 同类型去重取最严重（等级高优先，同等级取 issuetime 较新），并按等级降序输出。
     *  省一级定位时同理由成立：全省各类型各留一条最严重的市级预警。 */
    static void dedupAlarms(java.util.List<String[]> local) {
        java.util.Map<String, String[]> best =
                new java.util.LinkedHashMap<String, String[]>();
        for (String[] it : local) {
            String type = alarmType(it[0]);
            String[] cur = best.get(type);
            if (cur == null) {
                best.put(type, it);
                continue;
            }
            int lv = alarmLevel(it[0]);
            int cv = alarmLevel(cur[0]);
            if (lv > cv) {
                best.put(type, it);
            } else if (lv == cv && it[1] != null && cur[1] != null
                    && it[1].compareTo(cur[1]) > 0) {
                best.put(type, it);
            }
        }
        local.clear();
        local.addAll(best.values());
        java.util.Collections.sort(local, new java.util.Comparator<String[]>() {
            @Override
            public int compare(String[] a, String[] b) {
                return Integer.compare(alarmLevel(b[0]), alarmLevel(a[0]));
            }
        });
    }

    /** ISO 时间 "2026-08-09T05:32" -> "05:32" */
    public static String hhmm(String iso) {
        if (iso == null) return "--:--";
        int i = iso.indexOf('T');
        return i >= 0 && iso.length() >= i + 6 ? iso.substring(i + 1, i + 6) : "--:--";
    }

    /**
     * 反向地理编码（简化版，v9.16 供定时通知服务用）：只取城市名。
     * BigDataCloud 优先，失败回退 Nominatim；都失败返回 ""。
     * 注：主页另有带省/区县与缓存的完整版 reverseGeocode。
     */
    public static String reverseCity(double lat, double lng) {
        try {
            String url = "https://api.bigdatacloud.net/data/reverse-geocode-client"
                    + "?latitude=" + lat + "&longitude=" + lng + "&localityLanguage=zh";
            JSONObject j = getJson(url, 6000, 6000);
            String c = j.optString("city", "");
            if (c.isEmpty()) c = j.optString("locality", "");
            if (c.isEmpty()) c = j.optString("principalSubdivision", "");
            if (c.isEmpty()) c = j.optString("countryName", "");
            if (!c.isEmpty()) return c;
        } catch (Exception ignored) { }
        try {
            String url = "https://nominatim.openstreetmap.org/reverse"
                    + "?lat=" + lat + "&lon=" + lng + "&format=json&accept-language=zh";
            JSONObject j = getJson(url, 6000, 6000);
            JSONObject addr = j.optJSONObject("address");
            if (addr != null) {
                String c = addr.optString("city", "");
                if (c.isEmpty()) c = addr.optString("town", "");
                if (c.isEmpty()) c = addr.optString("county", "");
                if (c.isEmpty()) c = addr.optString("state", "");
                return c;
            }
        } catch (Exception ignored) { }
        return "";
    }

    /**
     * 通用 GET -> JSON（自动识别 gzip，供第三方天气源使用）。
     * 和风 v7 等接口强制返回 gzip；Android 的 HttpURLConnection 默认会
     * 自动请求并透明解压 gzip，但为兼容异常环境，此处按 gzip magic 头兜底解包。
     */
    public static JSONObject getJsonAuto(String url, int connectMs, int readMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(connectMs);
        conn.setReadTimeout(readMs);
        conn.setRequestProperty("User-Agent", "WeatherTool/9.87 (Android)");
        java.io.InputStream is = conn.getInputStream();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        is.close();
        conn.disconnect();
        byte[] data = bos.toByteArray();
        java.io.InputStream body =
                (data.length > 1 && (data[0] & 0xff) == 0x1f && (data[1] & 0xff) == 0x8b)
                ? new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(data))
                : new java.io.ByteArrayInputStream(data);
        BufferedReader br = new BufferedReader(
                new java.io.InputStreamReader(body, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return new JSONObject(sb.toString());
    }
}
