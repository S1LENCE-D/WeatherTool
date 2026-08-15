package com.simpleweather.app;

import android.app.Application;
import android.content.res.Resources;
import android.util.DisplayMetrics;

/**
 * 115% 界面大小分支专用（v9.91P）：固定把 density / scaledDensity / densityDpi
 * 统一放大 1.15 倍，使所有 dp 布局与 sp 文字整体放大 115%。
 *
 * 由构建脚本临时注入源码树，构建完成后移除，原版源码零残留。
 * 幂等：首次调用记录系统原始值，之后按「原始 × 1.15」重设；
 * 资源因配置变化重建后 density 回落原始值，下次 getResources() 自动重新应用。
 */
public class ScaleApp extends Application {

    private static final float SCALE = 1.15f;

    private static float sOrigDensity = 0f;
    private static float sOrigScaled = 0f;
    private static int sOrigDpi = 0;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /** 把 resources 的 metrics 应用为「原始 × 1.15」，幂等 */
    public static void apply(Resources res) {
        if (res == null) return;
        DisplayMetrics dm = res.getDisplayMetrics();
        if (sOrigDensity <= 0f) {
            sOrigDensity = dm.density;
            sOrigScaled = dm.scaledDensity;
            sOrigDpi = dm.densityDpi;
        }
        dm.density = sOrigDensity * SCALE;
        dm.scaledDensity = sOrigScaled * SCALE;
        dm.densityDpi = Math.round(sOrigDpi * SCALE);
    }
}
