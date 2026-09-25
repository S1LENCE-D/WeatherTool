package com.simpleweather.app;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;

/**
 * 多日小组件（默认 4x4，可拉伸 2x2~8x8）。
 *
 * <p>v10.1：与单日组件共用 {@code R.layout.widget} 与同一套渲染代码，
 * 差异只在设计基准尺寸（4x4 基准，主块给紧凑概览）+ 预报行。
 * 行数由可用高度决定（2x2 收起、4x4 满五行、8x8 加大行距），
 * 见 {@link WidgetSize}。
 */
public class WeatherWidgetProvider2x4 extends WeatherWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) refresh(context, mgr, id, true);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr,
                                          int id, Bundle newOptions) {
        refreshOnResize(context, mgr, id, true);
    }
}
