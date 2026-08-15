package com.simpleweather.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * v9.88：后台常驻开关管理（KeepAliveService 的调度入口）。
 * 开启：启动前台常驻服务（进程保活，划掉任务后推送/刷新照常）；
 * 关闭：停止服务并移除常驻通知。
 * 开机 / 冷启动 / 覆盖安装 / 划掉任务均自动恢复（ensureRunning）。
 */
public final class KeepAliveManager {

    private static final String PREFS = "keepalive_prefs";
    private static final String KEY_ENABLED = "enabled";

    private KeepAliveManager() { }

    public static boolean enabled(Context ctx) {
        // v9.88.1：默认开启（后台常驻是推送/刷新可靠性的基础）；
        // 用户手动关闭过则保持关闭（尊重选择）。
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, true);
    }

    /** 开关：开启即启动前台常驻服务，关闭即停止 */
    public static void setEnabled(Context ctx, boolean on) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, on).apply();
        if (on) start(ctx);
        else stop(ctx);
    }

    /** 应用启动 / 开机 / 更新 / 划掉任务：开关开着则恢复常驻服务（幂等） */
    public static void ensureRunning(Context ctx) {
        if (enabled(ctx)) start(ctx);
    }

    /** v9.87test：启动常驻服务（普通服务，无通知栏常驻，配合 15 分钟闹钟保活） */
    public static void start(Context ctx) {
        Intent it = new Intent(ctx, KeepAliveService.class);
        ctx.startService(it);
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, KeepAliveService.class));
    }
}
