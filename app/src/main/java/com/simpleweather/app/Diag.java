package com.simpleweather.app;

import android.util.Log;

/**
 * 后台链路诊断打点（统一 TAG=WeatherDiag，`adb logcat -s WeatherDiag` 可观测全链路）。
 *
 * <p><b>v10.1 修正</b>：不再硬编码常开，而是跟随应用内「诊断日志」开关
 * （{@link LogFile#init} / {@link LogFile#setEnabled} 会同步本开关；该开关默认开启，
 * 用户关掉日志时打点同时关闭）。旧版 `on = true` 写死，release 包会持续往 logcat
 * 输出城市名、通知权限状态、预警条数等信息，用户既关不掉也不受设置约束。
 * 需要排查时：在设置里打开「诊断日志」即可（或临时把下面初值改成 true）。
 */
public final class Diag {
    public static final String TAG = "WeatherDiag";
    private static boolean on = false;

    private Diag() { }

    /** 由「诊断日志」开关统一驱动（LogFile.init / setEnabled 会调用） */
    public static void setEnabled(boolean v) {
        on = v;
    }

    public static boolean enabled() {
        return on;
    }

    public static void i(String msg) {
        if (on) Log.i(TAG, msg);
    }
}
