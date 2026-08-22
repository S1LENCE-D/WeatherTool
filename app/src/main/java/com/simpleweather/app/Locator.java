package com.simpleweather.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Looper;

import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * v9.22：定位工具，MainActivity（前台/后台刷新）与 SpeakService（通知前强制定位）共用。
 * 流程：2 分钟内 last known 且精度 <=150m 秒回；
 * 否则先检测定位权限——无权限直接走 IP 定位；有权限先等 GPS（至多 5 秒），
 * GPS 超时才改走 IP 定位。定位方式提示由 MainActivity 在每次更新主页信息时统一弹出。
 * IP 定位：ipwho.is / ipinfo.io / geojs.io 三路并行先到先得 + 60 秒本地缓存秒回
 * （缓存未绑定公网 IP，窗口必须足够短，避免换网络/换城市后仍秒回旧坐标）。
 * v9.23：IP 缓存双因子验证——60 秒内无条件秒回；60 秒~30 分钟用设备本地
 *         IPv6 /64 前缀做“同一网络”判定（运营商重拨 IPv4 不影响），
 *         前缀一致才秒回；前缀变化或无 IPv6 才重新查询。30 分钟以上必重查。
 * v9.25：IP 定位源重构——国内源优先（百度 qifu-api / ip.useragentinfo 返回
 *         省市名，经 CityTable 城市表换算坐标），修复国外数据库漂移上海问题；
 *         国外三路降级兜底；缓存 key 改名作废旧坐标。
 * v9.88.1：修复无 GPS 硬件设备（平板类）闪退——此类设备系统无 GPS_PROVIDER，
 *         对不存在 provider 调用 getLastKnownLocation 抛 IllegalArgumentException
 *         （非 SecurityException），原 catch 拦不住；统一扩大为 catch(Exception)。
 */
public class Locator {

    private static final long GPS_WAIT_MS = 30000;   // v9.87-fix：类原生无 AGPS 时 GPS 冷启动实测 30s~数分钟（微信正常=GNSS 硬件 OK，慢在冷启动），窗口放宽到 30s；超时后仍有 startGpsWatch 后台续等，fix 迟到自动升级
    private static final long NET_WAIT_MS = 5000;    // v9.87-fix：网络定位窗口（快）
    /** v9.22：last known 仅 2 分钟内可秒回（原 30 分钟）。
     *  lastKnown 是“上次已知位置”，精度只代表该点本身可信，不代表当前位置
     *  就在其 150m 内——用户换城市后旧 lastKnown 仍在 30 分钟内，会造成
     *  “不切换地区”。2 分钟窗口足够覆盖原地刷新，又能在移动后及时换坐标。 */
    private static final long LASTKNOWN_FRESH_MS = 2 * 60 * 1000L;
    /** v9.23：IP 缓存分层策略。
     *  60 秒内无条件秒回（双保险）；60 秒~30 分钟走 IPv6 辅助验证——
     *  国内运营商常动态重拨 IPv4，但宽带 IPv6 前缀稳定，前缀一致即同一网络、
     *  位置未变，缓存可用；前缀变化/无 IPv6 才重新查询。超过 30 分钟必重查。 */
    private static final long IP_CACHE_FAST_MS = 60 * 1000L;
    private static final long IP_CACHE_MAX_MS = 30L * 60 * 1000;
    private static final String IP_PREFS = "ip_loc_cache_v2";   // v9.25 改名：作废旧版可能漂移到上海的缓存
    /** v9.87-fix：AGPS 辅助数据注入（类原生无 GMS、GPS 冷启动极慢的关键补偿）。
     *  两个 AOSP framework 标准额外命令（API 24+）：
     *  - force_time_injection：NTP 取 UTC 时间注入 HAL（卫星时间同步，TTFF 大减）
     *  - force_xtra_injection：从 config_xtraServerUrl 下载 XTRA 星历注入 HAL
     *  成功注入 12h 内不重复；失败 30min 后可重试；全程写诊断日志。 */
    private static final String AGPS_PREFS = "agps_state";
    private static final long AGPS_OK_MS = 12L * 60 * 60 * 1000;    // 成功后节流窗口
    private static final long AGPS_RETRY_MS = 30L * 60 * 1000;      // 失败后重试窗口

    private final Context ctx;
    private final LocationManager lm;
    private long lastAgpsTs = 0;      // 进程内 AGPS 注入时间记忆
    private boolean lastAgpsOk = false;

    public Locator(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.lm = (LocationManager) this.ctx.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * 定位总入口（需在后台线程调用）：
     * 1) 2 分钟内 last known 且精度 <=150m —— 秒回（原地刷新不等待）；
     * 2) 先检测定位权限：无权限 —— 直接走 IP 定位（不白等 GPS）；
     * 3) 有权限 —— 优先等待 GPS（至多 5 秒），GPS 超时无果才改走 IP 定位。
     * 定位方式提示由 MainActivity 在每次更新主页信息时统一弹出。
     */
    public Location locateFast() {
        Location best = lastKnownFresh();
        if (best != null && best.hasAccuracy() && best.getAccuracy() <= 150) {
            return best;   // 缓存已足够准（150m 内），直接使用
        }

        if (!hasLocationPermission()) {
            return locateByIp();   // 无权限：直接 IP（locateByIp 内部会提示）
        }

        // 有权限：GPS/网络优先，等它；超时了才轮到 IP
        Location gps = locateGpsNet();
        if (gps != null) return gps;
        // v9.87-fix：实时定位超时后先兜底「任意新鲜度的 last known」
        // （类原生无 GMS 网络定位时常见：provider 在但不出 fix），再走 IP
        Location stale = anyLastKnown();
        if (stale != null) return stale;
        return locateByIp();   // GPS 超时 + 无历史缓存 → IP
    }

    /** v9.87-fix：任意新鲜度的 last known 兜底（GPS 优先，按精度择优）。
     *  不要求新鲜/高精度——目的只是「尽量给个位置」，
     *  类原生 ROM 无 GMS 网络定位、GPS 冷启动又失败时兜底用。 */
    private Location anyLastKnown() {
        Location best = null;
        for (String p : new String[]{LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l == null) continue;
                if (best == null || moreAccurate(l, best)) best = l;
            } catch (Exception ignored) { }   // v9.88.1：同 lastKnownFresh——无 GPS 硬件时 provider 缺失抛异常
        }
        return best;
    }

    /** v9.44：按用户指定的方式定位。mode="ip" 强制 IP；"gps" 强制 GPS/网络
     *  （无权限或超时兜底 IP）；"auto"/空 则自动竞争（GPS 优先，超时转 IP）。 */
    public Location locateBy(String mode) {
        LogFile.i("Locator", "locateBy mode=" + mode + " perm=" + hasPermission()
                + " gpsEnabled=" + safeProviderEnabled(LocationManager.GPS_PROVIDER)
                + " netEnabled=" + safeProviderEnabled(LocationManager.NETWORK_PROVIDER));
        if ("ip".equals(mode)) return locateByIp();
        if ("gps".equals(mode)) {
            if (!hasLocationPermission()) return locateByIp();
            Location g = locateGpsNet();
            return g != null ? g : locateByIp();
        }
        return locateFast();
    }

    /** 是否已授予定位权限（FINE 或 COARSE 任一即可） */
    /** v9.47：对外暴露定位权限状态（GPS 实时监控注册前判断用） */
    public boolean hasPermission() {
        return hasLocationPermission();
    }

    private boolean hasLocationPermission() {
        try {
            return ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED
                    || ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    /** 2 分钟内的 last known 最优值（GPS 优先，按精度择优）；无则 null */
    private Location lastKnownFresh() {
        Location best = null;
        long now = System.currentTimeMillis();
        for (String p : new String[]{LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER}) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l == null) continue;
                if (now - l.getTime() > LASTKNOWN_FRESH_MS) continue; // 丢弃 2 分钟前的旧缓存
                if (best == null || moreAccurate(l, best)) best = l;
            } catch (Exception ignored) { }   // v9.88.1：无 GPS 硬件设备 provider 不存在时抛 IllegalArgumentException
        }
        return best;
    }

        /** GPS + 网络并行实时定位：GPS 优先、网络兜底。
     *  v9.87-fix：GPS 窗口 15s / 网络窗口 5s——类原生 ROM 无 AGPS 时 GPS 冷启动
     *  可达半分钟以上，5 秒必失败；网络定位（无 GMS 时）则通常不回调，5 秒即弃。 */
    private Location locateGpsNet() {
        if (Build.VERSION.SDK_INT >= 30) return locateGpsNet30();
        return locateGpsNetLegacy();
    }

    /** v9.87-fix：GPS 是否值得请求——必须已授 FINE 权限且系统 GPS 开关开启。
     *  类原生 ROM 上 COARSE 权限请求 GPS_PROVIDER 可能抛 SecurityException，
     *  未开 GPS 则必等满超时；两条件不满足直接跳过，避免无谓等待。 */
    private boolean safeProviderEnabled(String p) {
        try { return lm != null && lm.isProviderEnabled(p); }
        catch (Exception e) { return false; }
    }

    private boolean gpsUsable() {
        try {
            // v9.87-fix：放宽为任一定位权限——官方语义下 COARSE 时 GPS provider 仍返回
            // （系统自动模糊到 ~2km），只给"大概位置"的 Android 12+ 用户也能用 GPS；
            // 个别 ROM 在 COARSE 下请求 GPS 抛异常时由调用处 try/catch 兜住
            boolean perm = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED
                    || ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
            return perm && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    /** v9.87-fix：AGPS 辅助数据注入。AOSP 标准额外命令，需 FINE 权限（gpsUsable 已检查）；
     *  返回 false 说明 framework/HAL 不支持该命令或下载失败，容忍并记日志。 */
    private void injectAgpsIfNeeded() {
        if (!gpsUsable()) {
            LogFile.w("AGPS", "跳过：gpsUsable=false（权限或开关未满足）");
            return;
        }
        long now = System.currentTimeMillis();
        SharedPreferences sp = ctx.getSharedPreferences(AGPS_PREFS, Context.MODE_PRIVATE);
        long lastTs = sp.getLong("last_ts", 0);
        boolean lastOk = sp.getBoolean("last_ok", false);
        if (lastTs > 0) {
            long sinceMin = (now - lastTs) / 60000;
            if (lastOk && now - lastTs < AGPS_OK_MS) {
                LogFile.i("AGPS", "跳过：上次成功注入距今 " + sinceMin + " 分钟（12h 节流内）");
                return;
            }
            if (!lastOk && now - lastTs < AGPS_RETRY_MS) {
                LogFile.i("AGPS", "跳过：上次失败距今 " + sinceMin + " 分钟（30min 重试窗内）");
                return;
            }
        }
        boolean tOk = false, xOk = false;
        try {
            tOk = lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_time_injection", null);
            LogFile.i("AGPS", "force_time_injection 返回=" + tOk);
        } catch (Exception e) {
            LogFile.e("AGPS", "force_time_injection 异常", e);
        }
        try {
            xOk = lm.sendExtraCommand(LocationManager.GPS_PROVIDER, "force_xtra_injection", null);
            LogFile.i("AGPS", "force_xtra_injection 返回=" + xOk);
        } catch (Exception e) {
            LogFile.e("AGPS", "force_xtra_injection 异常", e);
        }
        boolean ok = tOk || xOk;   // 任一成功都算有进展
        lastAgpsTs = now; lastAgpsOk = ok;
        sp.edit().putLong("last_ts", now).putBoolean("last_ok", ok).apply();
        LogFile.i("AGPS", "本轮注入完成 ok=" + ok + "（time=" + tOk + " xtra=" + xOk
                + "），下次窗口: 成功12h/失败30min");
    }

    /** v9.87-fix：API 30+ 实时定位——GPS/NETWORK 并行 getCurrentLocation。
     *  任一先出 fix 即返回（GPS 优先）；网络 5 秒无果再等 GPS 至 30 秒；
     *  网络回调的是旧 last known 时也先用上（后续 startGpsWatch 会自动升级 GPS）。 */
    private Location locateGpsNet30() {
        LogFile.i("Locator", "locateGpsNet30 start gpsUsable=" + gpsUsable()
                + " gpsEnabled=" + safeProviderEnabled(LocationManager.GPS_PROVIDER)
                + " netEnabled=" + safeProviderEnabled(LocationManager.NETWORK_PROVIDER));
        injectAgpsIfNeeded();   // v9.87-fix：请求发出前注入星历/时间，缩短冷启动 TTFF
        final CountDownLatch netLatch = new CountDownLatch(1);
        final CountDownLatch gpsLatch = new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<Location> net =
                new java.util.concurrent.atomic.AtomicReference<Location>(null);
        final java.util.concurrent.atomic.AtomicReference<Location> gps =
                new java.util.concurrent.atomic.AtomicReference<Location>(null);
        final CancellationSignal[] cs = new CancellationSignal[2];
        java.util.concurrent.Executor ex = ctx.getMainExecutor();
        cs[0] = new CancellationSignal();
        try {
            lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, cs[0], ex,
                    new java.util.function.Consumer<Location>() {
                        @Override
                        public void accept(Location loc) {
                            if (loc != null) {
                                net.set(loc);
                                LogFile.i("Locator", "NET fix: acc=" + loc.getAccuracy()
                                        + " 新鲜度=" + (System.currentTimeMillis() - loc.getTime()) + "ms");
                            } else {
                                LogFile.w("Locator", "NET getCurrentLocation 回调 null");
                            }
                            netLatch.countDown();
                        }
                    });
        } catch (Exception e) { LogFile.e("Locator", "NET getCurrentLocation 异常", e); netLatch.countDown(); }
        cs[1] = new CancellationSignal();
        if (gpsUsable()) {
            try {
                lm.getCurrentLocation(LocationManager.GPS_PROVIDER, cs[1], ex,
                        new java.util.function.Consumer<Location>() {
                            @Override
                            public void accept(Location loc) {
                                if (loc != null) {
                                    gps.set(loc);
                                    LogFile.i("Locator", "GPS fix: acc=" + loc.getAccuracy());
                                } else {
                                    LogFile.w("Locator", "GPS getCurrentLocation 回调 null");
                                }
                                gpsLatch.countDown();
                            }
                        });
            } catch (Exception e) { LogFile.e("Locator", "GPS getCurrentLocation 异常", e); gpsLatch.countDown(); }
        } else {
            LogFile.w("Locator", "gpsUsable=false：跳过 GPS（权限或开关未满足）");
            gpsLatch.countDown();
        }
        try { netLatch.await(NET_WAIT_MS, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) { }
        Location gl = gps.get();
        if (gl != null) { cs[0].cancel(); cs[1].cancel(); return gl; }   // GPS 先到：直接用它
        Location nl = net.get();
        if (nl != null) { cs[1].cancel(); cs[0].cancel(); return nl; }   // 网络先到（含旧 last known）
        // 网络无果：继续等 GPS 补满 15 秒窗口
        try { gpsLatch.await(GPS_WAIT_MS - NET_WAIT_MS, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) { }
        cs[0].cancel(); cs[1].cancel();
        Location g30 = gps.get();
        LogFile.i("Locator", "locateGpsNet30 结束 result="
                + (g30 != null ? ("GPS acc=" + g30.getAccuracy()) : "null(30s 无 fix，走兜底)"));
        return g30;
    }

    /** API <30 实时定位：requestLocationUpdates 双路并行（GPS 优先 / 网络 5s 兜底） */
    private Location locateGpsNetLegacy() {
        LogFile.i("Locator", "locateGpsNetLegacy start gpsUsable=" + gpsUsable());
        final CountDownLatch netLatch = new CountDownLatch(1);
        final CountDownLatch gpsLatch = new CountDownLatch(1);
        final Location[] net = new Location[1];
        final Location[] gps = new Location[1];
        android.location.LocationListener gl = new android.location.LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                gps[0] = location; gpsLatch.countDown();
                LogFile.i("Locator", "legacy GPS fix: acc=" + location.getAccuracy());
            }
            @Override
            public void onStatusChanged(String provider, int status, android.os.Bundle extras) { }
            @Override
            public void onProviderEnabled(String provider) { }
            @Override
            public void onProviderDisabled(String provider) { gpsLatch.countDown(); }
        };
        android.location.LocationListener nl = new android.location.LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                net[0] = location; netLatch.countDown();
                LogFile.i("Locator", "legacy NET fix: acc=" + location.getAccuracy());
            }
            @Override
            public void onStatusChanged(String provider, int status, android.os.Bundle extras) { }
            @Override
            public void onProviderEnabled(String provider) { }
            @Override
            public void onProviderDisabled(String provider) { netLatch.countDown(); }
        };
        // v9.87-fix：官方已废弃 requestSingleUpdate（frameworks_base 提交原文称其
        // "dangerous"），统一改用 requestLocationUpdates(0,0) + 拿到 fix 即 removeUpdates；
        // onStatusChanged 自 API 29 起不再广播，故不依赖它。
        try {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, nl, Looper.getMainLooper());
        } catch (Exception e) { LogFile.e("Locator", "legacy NET request 异常", e); netLatch.countDown(); }
        if (gpsUsable()) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gl, Looper.getMainLooper());
            } catch (Exception e) { LogFile.e("Locator", "legacy GPS request 异常", e); gpsLatch.countDown(); }
        } else {
            LogFile.w("Locator", "gpsUsable=false：跳过 GPS（权限或开关未满足）");
            gpsLatch.countDown();
        }
        try { netLatch.await(NET_WAIT_MS, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) { }
        if (gps[0] != null) {
            lm.removeUpdates(gl); lm.removeUpdates(nl);
            return gps[0];
        }
        if (net[0] != null) {
            lm.removeUpdates(gl); lm.removeUpdates(nl);
            return net[0];
        }
        try { gpsLatch.await(GPS_WAIT_MS - NET_WAIT_MS, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) { }
        lm.removeUpdates(gl); lm.removeUpdates(nl);
        LogFile.i("Locator", "locateGpsNetLegacy 结束 result="
                + (gps[0] != null ? ("GPS acc=" + gps[0].getAccuracy()) : "null(走兜底)"));
        return gps[0];
    }

    /**
     * IP 定位兜底（v9.25 重构）：
     * 国内源优先（pconline / ip.useragentinfo，免费无 key，返回中文省市名，
     * 经 CityTable 城市表换算坐标，精度城市级）——国外 IP 数据库对中国大陆
     * IP 归属记录残缺，经常漂移到上海等枢纽城市（用户实测"大概率偏上海"）；
     * ip-api.com 实测对中国 IP 定位准确，与国内源同权；ipwho.is / ipinfo.io /
     * geojs.io 老三路降级为最后兜底（漂移风险大）。
     * 全部并行同一 5 秒窗口，先到先得，国内源结果优先级更高（后到也覆盖）。
     * 缓存策略（v9.23）：60 秒内无条件秒回；60 秒~30 分钟用 IPv6 前缀辅助验证；
     * 超过 30 分钟必重查。全部失败回退缓存（无则 null）。
     */
    private Location locateByIp() {
        LogFile.i("Locator", "locateByIp 开始（缓存/网络查询）");
        // 快路径：缓存 + 双因子校验（时间窗口 / IPv6 前缀）
        Location cached = loadIpLoc();
        String cachedV6 = cached == null ? null : (String) cached.getExtras().get("v6");
        if (cached != null) {
            long age = System.currentTimeMillis() - cached.getTime();
            if (age <= IP_CACHE_FAST_MS) {
                return cached;   // 1 分钟内无条件秒回
            }
            if (age <= IP_CACHE_MAX_MS && cachedV6 != null) {
                String curV6 = globalIpv6Prefix();
                if (curV6 != null && curV6.equals(cachedV6)) {
                    return cached;   // IPv6 前缀一致：同一网络，IPv4 重拨不影响
                }
            }
            // 其余情况：缓存不可信，走真实查询（下方）
        }

        final Object lock = new Object();
        final Location[] out = new Location[1];
        final int[] bestPrio = new int[]{2};   // 0=国内源（优先） 1=国外源

        // 国内源（优先级 0）：省市名 -> 城市表坐标
        // pconline 实测稳定（GBK 编码需转码）；useragentinfo 为备用
        final String[][] cn = {
                {"pconline", "https://whois.pconline.com.cn/ipJson.jsp?json=true"},
                {"uai", "https://ip.useragentinfo.com/json"}
        };
        for (final String[] u : cn) {
            final String tag = u[0], url = u[1];
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject j = "pconline".equals(tag)
                                ? getJsonGbk(url) : WeatherApi.getJson(url, 4000, 4000);
                        if (j == null) return;
                        double[] c = chinaCityLoc(j);
                        if (c != null) {
                            synchronized (lock) {
                                if (out[0] == null || 0 < bestPrio[0]) {
                                    Location l = new Location("ip");
                                    l.setLatitude(c[0]);
                                    l.setLongitude(c[1]);
                                    l.setAccuracy(30000f);   // 城市级精度
                                    l.setTime(System.currentTimeMillis());
                                    saveIpLoc(c[0], c[1], globalIpv6Prefix());
                                    out[0] = l;
                                    bestPrio[0] = 0;
                                }
                                lock.notifyAll();
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }).start();
        }

        // 国外源（兜底）：ip-api.com 对中国 IP 定位也准（实测深圳精确命中），
        // 与国内源同权（prio 0）；ipwho.is 等老三路漂移风险大，prio 1 最后兜底
        final String[] urls = {
                "https://ipwho.is/",
                "https://ipinfo.io/json",
                "https://get.geojs.io/v1/ip/geo.json",
                "http://ip-api.com/json/?lang=zh-CN&fields=status,lat,lon,regionName,city"
        };
        for (final String url : urls) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSONObject j = WeatherApi.getJson(url, 4000, 4000);
                        double lat = Double.NaN, lng = Double.NaN;
                        int prio = 1;
                        if (url.contains("ip-api")) {
                            // ip-api.com 免费版 http：lat/lon 直接可用（国内定位准确）
                            lat = j.optDouble("lat", Double.NaN);
                            lng = j.optDouble("lon", Double.NaN);
                            prio = 0;
                        } else if (url.contains("ipinfo")) {
                            // ipinfo.io 返回 "loc": "22.55,113.88"
                            String loc = j.optString("loc", "");
                            int k = loc.indexOf(',');
                            if (k > 0) {
                                lat = Double.parseDouble(loc.substring(0, k).trim());
                                lng = Double.parseDouble(loc.substring(k + 1).trim());
                            }
                        } else {
                            // ipwho.is / geojs.io 都是 latitude / longitude 字段
                            lat = j.optDouble("latitude", Double.NaN);
                            lng = j.optDouble("longitude", Double.NaN);
                        }
                        if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                            synchronized (lock) {
                                if (out[0] == null || prio < bestPrio[0]) {
                                    Location l = new Location("ip");
                                    l.setLatitude(lat);
                                    l.setLongitude(lng);
                                    l.setAccuracy(prio == 0 ? 30000f : 50000f);
                                    l.setTime(System.currentTimeMillis());
                                    saveIpLoc(lat, lng, globalIpv6Prefix());
                                    out[0] = l;
                                    bestPrio[0] = prio;
                                }
                                lock.notifyAll();
                            }
                        }
                    } catch (Exception ignored) { }
                }
            }).start();
        }

        synchronized (lock) {
            long deadline = System.currentTimeMillis() + 5000;
            while (out[0] == null) {
                long remain = deadline - System.currentTimeMillis();
                if (remain <= 0) break;
                try { lock.wait(remain); } catch (InterruptedException e) { break; }
            }
        }
        if (out[0] != null) return out[0];
        return loadIpLoc();   // 全部失败：回退缓存（可能已过期，无则 null）
    }

    /**
     * v9.25：解析国内 IP 源返回的 JSON（pconline 字段是 pro/city，其余是
     * province/city，qifu 类有 data 层），取省市名 -> CityTable 换算城市坐标；
     * 查不到返回 null。
     */
    private static double[] chinaCityLoc(JSONObject j) {
        JSONObject d = j.optJSONObject("data");
        if (d == null) d = j;
        String province = d.optString("province", "");
        if (province.isEmpty()) province = d.optString("pro", "");
        String city = d.optString("city", "");
        if (city.isEmpty()) city = d.optString("district", "");
        double[] c = CityTable.lookup(province, city);
        if (c != null) return c;
        // 直辖市等 city 为空或查不到时，直接用省名再试一次
        return CityTable.lookup(province, province);
    }

    /** v9.25：GBK 编码 GET 请求（pconline 返回 GBK，WeatherApi 的 UTF-8 解析会乱码） */
    private static JSONObject getJsonGbk(String url) {
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) WeatherTool/9.62");
            java.io.InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new JSONObject(new String(bos.toByteArray(), "GBK"));
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void saveIpLoc(double lat, double lng, String v6Prefix) {
        ctx.getSharedPreferences(IP_PREFS, Context.MODE_PRIVATE).edit()
                .putFloat("lat", (float) lat)
                .putFloat("lng", (float) lng)
                .putLong("ts", System.currentTimeMillis())
                .putString("v6", v6Prefix == null ? "" : v6Prefix)
                .apply();
    }

    private Location loadIpLoc() {
        SharedPreferences sp = ctx.getSharedPreferences(IP_PREFS, Context.MODE_PRIVATE);
        long ts = sp.getLong("ts", 0);
        if (ts <= 0 || System.currentTimeMillis() - ts > IP_CACHE_MAX_MS) return null;
        Location l = new Location("ip");
        l.setLatitude(sp.getFloat("lat", 0f));
        l.setLongitude(sp.getFloat("lng", 0f));
        l.setAccuracy(50000f);
        l.setTime(ts);
        Bundle b = new Bundle();
        b.putString("v6", sp.getString("v6", ""));
        l.setExtras(b);
        return l;
    }

    /**
     * v9.23：取当前设备第一个全局 IPv6 地址的 /64 前缀（前 8 字节 hex），
     * 作为“网络身份”辅助验证 IP 缓存——运营商重拨 IPv4 时宽带 IPv6 前缀
     * 通常不变，前缀一致说明仍在同一网络、位置未变，缓存可放心秒回。
     * 纯本地读取（NetworkInterface），零网络请求；无全局 IPv6 返回 null。
     */
    private static String globalIpv6Prefix() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nis =
                    java.net.NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                java.net.NetworkInterface ni = nis.nextElement();
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress a = addrs.nextElement();
                    if (a instanceof java.net.Inet6Address) {
                        java.net.Inet6Address v6 = (java.net.Inet6Address) a;
                        if (v6.isLinkLocalAddress() || v6.isLoopbackAddress()
                                || v6.isSiteLocalAddress() || v6.isAnyLocalAddress()) {
                            continue;
                        }
                        byte[] raw = v6.getAddress();
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < 8; i++) {
                            sb.append(String.format("%02x", raw[i] & 0xFF));
                        }
                        return sb.toString();
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** 比较两个定位哪个更可信：非空优先，其次精度（accuracy 越小越准） */
    private static boolean moreAccurate(Location a, Location b) {
        if (a == null) return false;
        if (b == null) return true;
        if (!a.hasAccuracy()) return false;
        if (!b.hasAccuracy()) return true;
        return a.getAccuracy() < b.getAccuracy();
    }
}
