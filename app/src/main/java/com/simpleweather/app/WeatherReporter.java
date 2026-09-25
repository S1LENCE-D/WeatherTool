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
    // v10.1：最近一次「成功送达简报」的时间戳（设置页自检展示）
    public static final String KEY_LAST_OK_TS = "last_ok_ts";

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
     * 注册每日闹钟：一次性「精确闹钟 + AllowWhileIdle」，每次触发后由
     * {@link AlarmReceiver} 重排下一天，等效每日循环。
     *
     * <p><b>v10.1 修正</b>：旧注释称「setAlarmClock 免权限」，这在 Android 12+ 是<b>错的</b>——
     * 官方文档明确：`setExact()` / `setExactAndAllowWhileIdle()` / `setAlarmClock()`
     * 三者都需要 SCHEDULE_EXACT_ALARM，缺权限一律抛 SecurityException。
     * 旧链路在缺权限时会一路降级到 setInexactRepeating（**不精确**的每日重复闹钟），
     * 表现就是「到点了没响 / 响得不准」。
     *
     * <p>现在的降级顺序（每一级都明确说清代价）：
     * <ol>
     *   <li>有精确闹钟权限 → {@code setExactAndAllowWhileIdle}（准点、Doze 可唤醒）；</li>
     *   <li>无精确闹钟权限 → {@code setAndAllowWhileIdle}（**可能被系统小幅推迟**，
     *       但仍是 doze-tolerant 的一次性闹钟，比 setInexactRepeating 可靠）
     *       —— 同时应在 App 内引导用户去开启「闹钟与提醒」权限；</li>
     *   <li>任何异常 → {@code setInexactRepeating} 兜底（最差情况，聊胜于无）。</li>
     * </ol>
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

        // Android 12+ 才有「精确闹钟权限」概念（API 31 起可直连调用，无需反射）
        if (Build.VERSION.SDK_INT >= 31 && !canExactAlarms(am)) {
            Diag.i("schedule: 无精确闹钟权限，降级为 setAndAllowWhileIdle（时刻可能被推迟）");
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                return;
            } catch (Exception e) {
                Diag.i("schedule: setAndAllowWhileIdle 失败 " + e);
            }
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } catch (SecurityException e) {
            // 权限被系统/用户拒绝（可能发生在 canScheduleExactAlarms() 之后）
            Diag.i("schedule: setExactAndAllowWhileIdle 被拒，降级 setAndAllowWhileIdle");
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
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

    /** 精确闹钟权限是否可用（Android 12+ 才需要；更低版本恒 true） */
    public static boolean canExactAlarms(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am != null && canExactAlarms(am);
    }

    private static boolean canExactAlarms(AlarmManager am) {
        try {
            return am.canScheduleExactAlarms();
        } catch (Throwable t) {
            return false;   // 厂商实现异常时按「无权限」处理（走降级路径，不会崩）
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
     *
     * <p>v10.1：标记改由 {@link ReportRunner} 在**通知真正送出之后**才写；手动「立即播报」
     * 走的也是 ReportRunner，因此同样会写标记（心跳补发据此跳过）。定时闹钟本身不看标记，
     * 到点仍会照常推送。
     */
    public static boolean catchUpReport(Context ctx) {   // true=今日已确认送达
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_ENABLED, false)) return false;              // 开关关闭：不补
        String today = todayKey();
        if (today.equals(sp.getString(KEY_LAST_REPORT_DATE, ""))) return true;  // 今天已送达
        Calendar plan = Calendar.getInstance();
        plan.set(Calendar.HOUR_OF_DAY, sp.getInt(KEY_HOUR, 8));
        plan.set(Calendar.MINUTE, sp.getInt(KEY_MINUTE, 0));
        plan.set(Calendar.SECOND, 0);
        plan.set(Calendar.MILLISECOND, 0);
        if (System.currentTimeMillis() < plan.getTimeInMillis()) return false;   // 未到点
        // v10.1：直接在本线程完成（调用方是心跳接收器的 goAsync 线程），不再启动服务；
        // 且「今日已送达」标记由 ReportRunner 在通知真正送出后才写 —— 失败还能下一轮再补。
        return ReportRunner.run(ctx);
    }

    /** v10.1：记录「简报成功送达」的时间戳（设置页自检用） */
    public static void recordOk(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_OK_TS, System.currentTimeMillis()).apply();
    }

    /** 最近一次成功送达简报的时间戳（0=从未成功） */
    public static long lastOkTs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_OK_TS, 0L);
    }

    /** 今日是否已送达简报 */
    public static boolean reportedToday(Context ctx) {
        return todayKey().equals(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_REPORT_DATE, ""));
    }
}
