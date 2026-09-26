package com.simpleweather.app;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;

/**
 * v10.1.3：<b>后台统一流程</b>（15 分钟一轮，三条触发路径共用）。
 *
 * <p>顺序（每一步都写诊断日志，便于上机核对到底走到哪一步）：
 * <pre>
 *   ① 自启动触发：AlarmManager 心跳 / JobScheduler 任务 / 可选常驻服务（三者任一即可）
 *   ② 定位      ：Locator.locateFast()；手动城市直接用其坐标；无权限或失败退回上次成功坐标
 *   ③ 刷新数据  ：WeatherCenter.fetchWeather()（写缓存；内部一并执行自定义提醒的阈值判定）
 *   ④ 判定该不该通知：定时播报（到点且当天未送达）／新增预警（黄色及以上）／自定义阈值命中
 *   ⑤ 通知      ：命中才发；不命中就静默结束（无通知、无痕迹）
 *   ⑥ 收尾      ：刷新桌面小组件
 * </pre>
 *
 * <p>每一步单独 try：任何一环抛异常都不能影响后面的通知路径 ——
 * 这正是 v10.1.1 的教训（当时小组件渲染异常把"定时播报"一起拖没了）。
 */
public final class BackgroundTasks {

    /** 两条触发路径最短间隔：三条路径都指到这里，靠它去重，避免同一轮通知发两遍 */
    private static final long MIN_INTERVAL_MS = 10L * 60 * 1000;
    private static final String PREFS = "bg_prefs";
    private static final String KEY_LAST_RUN = "last_run";

    private BackgroundTasks() { }

    /** 跑一轮后台流程（调用方须在子线程；本方法不向外抛异常） */
    public static void run(Context context) {
        if (context == null) return;
        // 去重：闹钟 / JobScheduler / 常驻服务三条路径可能前后脚触发，10 分钟内只跑一轮。
        // 时间戳落在 SharedPreferences 里而不是内存变量 —— 进程被杀后重启也不会漏判。
        try {
            android.content.SharedPreferences sp =
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long last = sp.getLong(KEY_LAST_RUN, 0L);
            long now = System.currentTimeMillis();
            // 快速检查模式下门槛降到 20 秒：既不挡 30 秒节奏，又能拦住同一次触发被多路径重复执行
            long minInterval = CacheRefresher.fastCheck(context) ? 20_000L : MIN_INTERVAL_MS;
            if (last != 0 && now - last < minInterval) {
                Diag.i("BG流程: 距上次仅 " + ((now - last) / 1000) + "s，本轮跳过（多路径去重）");
                return;
            }
            sp.edit().putLong(KEY_LAST_RUN, now).apply();
        } catch (Throwable ignored) { }
        Diag.i("BG流程: 开始（15 分钟周期）");

        // ---------- ② 定位 ----------
        double lat = 0, lng = 0;
        String city = null;
        boolean located = false;
        try {
            if (WeatherReporter.hasManualCity(context)) {
                lat = WeatherReporter.manualLat(context);
                lng = WeatherReporter.manualLng(context);
                city = WeatherReporter.manualCityName(context);
                located = true;
                Diag.i("BG流程: 使用手动城市 " + city);
            } else {
                Locator locator = new Locator(context);
                if (locator.hasPermission()) {
                    Location loc = locator.locateFast();
                    if (loc != null) {
                        lat = loc.getLatitude();
                        lng = loc.getLongitude();
                        city = WeatherReporter.city(context);
                        double plat = WeatherReporter.lat(context);
                        double plng = WeatherReporter.lng(context);
                        if (city == null || city.isEmpty() || distKm(lat, lng, plat, plng) > 50) {
                            city = WeatherApi.reverseCity(lat, lng);   // 换地区了，重新反查
                        }
                        located = true;
                        Diag.i("BG流程: 定位成功 " + lat + "," + lng + " city=" + city);
                    } else {
                        Diag.i("BG流程: 定位返回空，改用上次坐标");
                    }
                } else {
                    Diag.i("BG流程: 无定位权限，改用上次坐标");
                }
            }
        } catch (Throwable t) {
            Diag.i("BG流程: 定位异常 " + t);
        }
        if (!located) {
            try {
                WeatherCache.Data d = WeatherCache.load(context);
                if (d != null) {
                    lat = d.lat;
                    lng = d.lng;
                    city = d.city;
                    Diag.i("BG流程: 退回缓存坐标 " + lat + "," + lng);
                }
            } catch (Throwable ignored) { }
        }

        // ---------- ③ 刷新数据（内部同时执行自定义提醒阈值判定） ----------
        boolean refreshed = false;
        try {
            if (lat != 0 || lng != 0) {
                refreshed = WeatherCenter.get().fetchWeather(context, lat, lng, city) != null;
                if (located && !WeatherReporter.hasManualCity(context)) {
                    WeatherReporter.saveLocation(context, lat, lng, city);
                }
            }
        } catch (Throwable t) {
            Diag.i("BG流程: 刷新异常 " + t);
        }
        Diag.i("BG流程: 数据刷新 " + (refreshed ? "成功" : "未成功，本轮用缓存继续"));

        // ---------- ④ 判定是否该通知　⑤ 命中才通知 ----------
        try {
            boolean should = WeatherReporter.shouldReportNow(context);
            Diag.i("BG流程: 是否该发定时播报=" + should);
            if (should) {
                WeatherReporter.catchUpReport(context);
            }
        } catch (Throwable t) {
            Diag.i("BG流程: 播报判定异常 " + t);
        }
        try {
            // 仅在"有新增的黄色及以上预警"时推送（内部自带当日去重）
            AlertChecker.check(context);
        } catch (Throwable ignored) { }
        // 自定义提醒（温度/湿度/紫外线阈值）：在 fetchWeather 内完成判定与发送，自带 1 小时冷却

        if (Build.VERSION.SDK_INT >= 33) {
            try {
                if (context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    Diag.i("BG流程: 通知权限未授予，命中的通知会被系统丢弃");
                }
            } catch (Throwable ignored) { }
        }

        // ---------- ⑥ 收尾 ----------
        // 文档 §6-2「定期检查并重启已死亡的服务」：常驻服务开了但没在跑就拉一把
        // （后台启动前台服务会被系统拒绝，start() 内部吞掉异常，不影响本流程）
        try {
            if (WeatherKeepAliveService.enabled(context) && !WeatherKeepAliveService.isRunning()) {
                WeatherKeepAliveService.start(context);
            }
        } catch (Throwable ignored) { }
        try {
            WeatherWidgetProvider.updateAll(context);
        } catch (Throwable ignored) { }
        Diag.i("BG流程: 结束");
    }

    /** 两点间距离（km）；用于判断是否换了地区（>50km 重新反查城市） */
    private static double distKm(double lat1, double lng1, double lat2, double lng2) {
        if (lat2 == 0 && lng2 == 0) return 999;
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    /** 注册 15 分钟系统任务（persisted：重启后由系统自动恢复） */
    public static void ensureJob(Context ctx) {
        WeatherJobService.ensureScheduled(ctx);
    }

    /** 按开关同步常驻服务（前台服务；Android 12+ 不允许开机自启，只能应用内拉起） */
    public static void syncKeepAlive(Context ctx) {
        if (WeatherKeepAliveService.enabled(ctx)) WeatherKeepAliveService.start(ctx);
        else WeatherKeepAliveService.stop(ctx);
    }
}
