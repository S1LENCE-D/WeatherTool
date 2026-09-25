package com.simpleweather.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;

/**
 * 单日小组件（默认 2x2，可拉伸）。
 *
 * <p>v10.1：与多日组件共用 {@code R.layout.widget} 与同一套渲染代码，
 * 差异只在设计基准尺寸（2x2 基准，主块给大温度）。尺寸变化时用
 * {@link #onAppWidgetOptionsChanged} 立刻重排（先用缓存，不等网络）。
 */
public class WeatherWidgetProvider2x2 extends WeatherWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) refresh(context, mgr, id, false);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr,
                                          int id, Bundle newOptions) {
        refreshOnResize(context, mgr, id, false);
    }
}
