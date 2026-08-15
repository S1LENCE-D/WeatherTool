package com.simpleweather.app;

import android.util.Log;

/**
 * v9.87-diag：后台推送链路诊断打点。
 * 统一 TAG=WeatherDiag，logcat 过滤即可观测全链路：
 *   adb logcat -s WeatherDiag
 * 定位完问题后可整体移除（仅 Log.i，无任何功能副作用）。
 */
public final class Diag {
    public static final String TAG = "WeatherDiag";
    private static boolean on = true;

    private Diag() { }

    public static void i(String msg) {
        if (on) Log.i(TAG, msg);
    }
}
