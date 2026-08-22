package com.simpleweather.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * 统一通知中枢（v9.75 重写）：
 * 所有推送（定时天气简报 / 气象预警 / 服务常驻）的渠道创建、通知构建、发送/取消
 * 集中在此，避免各服务各自建渠道造成优先级不一致、渠道重复或遗漏。
 *
 * 渠道规划：
 *  - weather_report  天气简报：IMPORTANCE_DEFAULT（定时推送，常规提醒）
 *  - weather_alert   气象预警：IMPORTANCE_HIGH（黄色及以上，声音+震动）
 */
public final class Notifier {

    public static final String CH_REPORT = "weather_report";
    public static final String CH_ALERT = "weather_alert";
    /** v9.87：后台缓存更新占位（前台服务必需，IMPORTANCE_MIN 无声音无打扰） */
    public static final String CH_REFRESH = "weather_refresh";
    /** v9.87test：自定义气象提醒（温度 / 湿度 / 紫外线超阈值） */
    public static final String CH_CUSTOM = "weather_custom";

    public static final int ID_REPORT = 1001;   // 定时天气简报
    public static final int ID_ALERT = 2002;    // 预警提醒
    public static final int ID_FG = 3003;       // v9.87：前台服务占位通知（服务结束即移除）
    public static final int ID_CUSTOM = 4005;  // v9.87test：自定义气象提醒

    private Notifier() { }

    /** 创建全部通知渠道（幂等，各服务启动时调用一次） */
    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel report = new NotificationChannel(
                CH_REPORT, "天气简报", NotificationManager.IMPORTANCE_DEFAULT);
        report.setDescription("每天定时推送的天气简报通知");
        report.setShowBadge(true);
        nm.createNotificationChannel(report);

        NotificationChannel alert = new NotificationChannel(
                CH_ALERT, "气象预警", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("黄色及以上气象预警提醒");
        alert.enableVibration(true);
        alert.setShowBadge(true);
        nm.createNotificationChannel(alert);

        NotificationChannel refresh = new NotificationChannel(
                CH_REFRESH, "后台更新", NotificationManager.IMPORTANCE_MIN);
        refresh.setDescription("后台自动刷新天气数据时的低优先级占位");
        refresh.setShowBadge(false);
        nm.createNotificationChannel(refresh);

        NotificationChannel custom = new NotificationChannel(
                CH_CUSTOM, "自定义提醒", NotificationManager.IMPORTANCE_HIGH);
        custom.setDescription("温度 / 湿度 / 紫外线超出自定义阈值时的提醒");
        custom.enableVibration(true);
        custom.setShowBadge(true);
        nm.createNotificationChannel(custom);

    }

    /** v9.87：后台缓存更新占位通知（前台服务启动凭证，无声音无打扰） */
    public static Notification buildRefresh(Context c) {
        ensureChannels(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_REFRESH)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_cloud);
        b.setContentTitle("简洁天气");
        b.setContentText("正在更新天气数据…");
        b.setOngoing(true);
        b.setContentIntent(mainIntent(c));
        return b.build();
    }

    /** 构建统一 PendingIntent：点击通知回到主页 */
    private static PendingIntent mainIntent(Context c) {
        Intent it = new Intent(c, MainActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 天气简报通知（loading=true 为前台占位，false 为最终简报） */
    public static Notification buildReport(Context c, String text, boolean loading) {
        ensureChannels(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_REPORT)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_cloud);
        b.setContentTitle(loading ? "简洁天气" : "简洁天气 · 每日天气");
        b.setContentText(text);
        b.setStyle(new Notification.BigTextStyle().bigText(text));
        b.setContentIntent(mainIntent(c));
        b.setAutoCancel(!loading);
        if (!loading) b.setCategory(Notification.CATEGORY_ALARM);
        return b.build();
    }

    /** 发送天气简报（同 ID 更新，先显示占位再更新为最终内容） */
    public static void notifyReport(Context c, String text, boolean loading) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.notify(ID_REPORT, buildReport(c, text, loading));
    }

    /** 预警提醒通知：标题带等级前缀，正文列出全部生效预警，按等级着色 */
    public static void notifyAlert(Context c, String title, String body, int levelColor) {
        ensureChannels(c);
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        // v9.87-diag：Android 13+ 通知权限状态检查
        if (Build.VERSION.SDK_INT >= 33) {
            boolean perm = c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED;
            boolean enabled = nm.areNotificationsEnabled();
            Diag.i("notifyAlert: SDK>=33 POST_NOTIFICATIONS=" + perm
                    + " areNotificationsEnabled=" + enabled);
            if (!perm || !enabled) {
                Diag.i("notifyAlert: 通知权限未授予，通知将被系统丢弃!");
            }
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_ALERT)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_cloud);
        b.setContentTitle(title);
        b.setContentText(body);
        b.setStyle(new Notification.BigTextStyle().bigText(body));
        b.setContentIntent(mainIntent(c));
        b.setAutoCancel(true);
        b.setCategory(Notification.CATEGORY_ALARM);
        b.setPriority(Notification.PRIORITY_HIGH);
        if (levelColor != 0) b.setColor(levelColor);
        b.setDefaults(Notification.DEFAULT_ALL);
        nm.notify(ID_ALERT, b.build());
        Diag.i("notifyAlert: 已发送 id=" + ID_ALERT + " title=" + title);
    }

    /** v9.87test：自定义气象提醒通知（温度 / 湿度 / 紫外线超阈值） */
    public static void notifyCustomAlert(Context c, String body) {
        ensureChannels(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CH_CUSTOM)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_cloud);
        b.setContentTitle("简洁天气 · 气象提醒");
        b.setContentText(body);
        b.setStyle(new Notification.BigTextStyle().bigText(body));
        b.setContentIntent(mainIntent(c));
        b.setAutoCancel(true);
        b.setCategory(Notification.CATEGORY_ALARM);
        b.setPriority(Notification.PRIORITY_HIGH);
        b.setDefaults(Notification.DEFAULT_ALL);
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.notify(ID_CUSTOM, b.build());
    }

    public static void cancel(Context c, int id) {
        NotificationManager nm =
                (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(id);
    }
}
