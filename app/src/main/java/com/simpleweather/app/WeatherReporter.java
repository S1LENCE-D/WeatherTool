package com.simpleweather.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 定时天气通知：设置存储（SharedPreferences）+ AlarmManager 每日调度。
 */
public final class WeatherReporter {
    public static final String PREFS = "report_prefs";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_HOUR = "hour";
    public static final String KEY_MINUTE = "minute";
    public static final String KEY_LAT = "lat";
    public static final String KEY_LNG = "lng";
    public static final String KEY_CITY = "city";
    // v9.27：手动查询的其他城市（优先于自动定位）
    public static final String KEY_MANUAL_NAME = "manual_name";
    public static final String KEY_MANUAL_LAT = "manual_lat";
    public static final String KEY_MANUAL_LNG = "manual_lng";
    // v9.88.3：最近一次定时播报日期（yyyyMMdd），用于错过补播报去重
    public static final String KEY_LAST_REPORT_DATE = "last_report_date";

    private WeatherReporter() { }

    /** 渲染天气时同步保存当前定位与城市（供定时通知用） */
    public static void saveLocation(Context ctx, double lat, double lng, String city) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_LAT, (float) lat)
                .putFloat(KEY_LNG, (float) lng)
                .putString(KEY_CITY, city == null ? "" : city)
                .apply();
    }

    public static double lat(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getFloat(KEY_LAT, 39.9f);
    }

    public static double lng(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getFloat(KEY_LNG, 116.4f);
    }

    public static String city(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CITY, "");
    }

    // ============ v9.27：手动城市（查询其他城市） ============

    /** 记录手动选择的城市（App 主页 / 小组件 / 定时通知统一走它，不再自动定位） */
    public static void setManualCity(Context ctx, String name, double lat, double lng) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_MANUAL_NAME, name == null ? "" : name)
                .putFloat(KEY_MANUAL_LAT, (float) lat)
                .putFloat(KEY_MANUAL_LNG, (float) lng)
                .apply();
    }

    /** 清除手动城市，恢复自动定位 */
    public static void clearManualCity(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY_MANUAL_NAME)
                .remove(KEY_MANUAL_LAT)
                .remove(KEY_MANUAL_LNG)
                .apply();
    }

    public static boolean hasManualCity(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .contains(KEY_MANUAL_LAT);
    }

    public static String manualCityName(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_MANUAL_NAME, "");
    }

    public static double manualLat(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getFloat(KEY_MANUAL_LAT, 0f);
    }

    public static double manualLng(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getFloat(KEY_MANUAL_LNG, 0f);
    }

    public static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    /** 开启/关闭 + 更新每日调度 */
    public static void setEnabled(Context ctx, boolean on, int hour, int minute) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, on)
                .putInt(KEY_HOUR, hour)
                .putInt(KEY_MINUTE, minute)
                .apply();
        if (on) schedule(ctx, hour, minute);
        else cancel(ctx);
    }

    /** 应用启动 / 开机后恢复调度（自启动生效点） */
    public static void ensureScheduled(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (sp.getBoolean(KEY_ENABLED, false)) {
            schedule(ctx, sp.getInt(KEY_HOUR, 8), sp.getInt(KEY_MINUTE, 0));
        }
    }

    /**
     * v9.87：注册每日闹钟。改为一次性「精确闹钟 + AllowWhileIdle」——
     * setInexactRepeating 在 Doze/省电下会被系统大幅延迟甚至跳过（后台不通知的主因）；
     * 每次触发后由 AlarmReceiver 重新调度下一天，等效每日循环。
     * 权限链：setExactAndAllowWhileIdle（Android 6+ 免权限，12+ 需 SCHEDULE_EXACT_ALARM）
     *  → 无权限回退 setAlarmClock（免权限、Doze 必达）
     *  → 异常兜底 setInexactRepeating（老系统容错）。
     */
    public static void schedule(Context ctx, int hour, int minute) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent it = new Intent(ctx, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) {
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        long trigger = c.getTimeInMillis();

        // Android 12+：检查精确闹钟权限（反射调用，避免低版本编译问题）
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                java.lang.reflect.Method m =
                        AlarmManager.class.getMethod("canScheduleExactAlarms");
                boolean ok = (Boolean) m.invoke(am);
                if (!ok) {
                    // 无精确闹钟权限：setAlarmClock 免权限、Doze 下必达
                    am.setAlarmClock(new AlarmManager.AlarmClockInfo(trigger, null), pi);
                    return;
                }
            } catch (Exception ignored) { }
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } catch (SecurityException e) {
            // Android 12+ 权限被拒（未声明/被关闭）：setAlarmClock 兜底
            try {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(trigger, null), pi);
            } catch (Exception e2) {
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger,
                        AlarmManager.INTERVAL_DAY, pi);
            }
        } catch (Exception e) {
            // 极老系统/厂商兼容：退化为重复闹钟
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, trigger,
                    AlarmManager.INTERVAL_DAY, pi);
        }
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent it = new Intent(ctx, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    /** v9.88.3：记录「今日定时播报已执行」标记（定时触发与补播报共用，防重复） */
    public static void markReported(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_REPORT_DATE, todayKey())
                .apply();
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /**
     * v9.88.3：错过补播报——后台心跳（每 15 分钟）调用。
     * 已过今日计划时刻 && 今天定时播报尚未执行 → 补一次播报并立即写标记，
     * 之后的心跳检测到「已播报」自动跳过。
     * 手动「立即播报」不写标记（不吞定时播报，宁多勿漏）。
     */
    public static void maybeCatchUpReport(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_ENABLED, false)) return;            // 开关关闭：不补
        String today = todayKey();
        if (today.equals(sp.getString(KEY_LAST_REPORT_DATE, ""))) return;  // 已播报：跳过
        Calendar plan = Calendar.getInstance();
        plan.set(Calendar.HOUR_OF_DAY, sp.getInt(KEY_HOUR, 8));
        plan.set(Calendar.MINUTE, sp.getInt(KEY_MINUTE, 0));
        plan.set(Calendar.SECOND, 0);
        plan.set(Calendar.MILLISECOND, 0);
        if (System.currentTimeMillis() < plan.getTimeInMillis()) return;   // 未到点：不补
        // 到点未播：走与 AlarmReceiver 相同链路（前台服务启动凭证，播报后自停）
        Intent svc = new Intent(ctx, SpeakService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc);
        else ctx.startService(svc);
        sp.edit().putString(KEY_LAST_REPORT_DATE, today).apply();   // 先写标记防心跳重复补
    }
}
