package com.simpleweather.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;

/**
 * v9.21：2x4 小组件（今日概览 + 未来 5 日预报）。
 * 固定使用 widget_2x4 布局，不随尺寸切换布局（拉伸只放大内容区域）。
 */
public class WeatherWidgetProvider2x4 extends WeatherWidgetProvider {

    private static final int LAYOUT = R.layout.widget_2x4;

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
