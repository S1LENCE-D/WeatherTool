package com.simpleweather.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;

/**
 * v9.21：2x2 小组件（当日实时天气）。
 * 固定使用 widget_2x2 布局，不随尺寸切换布局（拉伸只放大内容区域）。
 */
public class WeatherWidgetProvider2x2 extends WeatherWidgetProvider {

    private static final int LAYOUT = R.layout.widget_2x2;

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) refresh(context, mgr, id, LAYOUT);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr,
                                          int id, Bundle newOptions) {
        refresh(context, mgr, id, LAYOUT);
    }
}
