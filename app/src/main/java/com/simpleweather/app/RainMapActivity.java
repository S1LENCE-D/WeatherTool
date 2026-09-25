package com.simpleweather.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

/**
 * 二级页面：降雨图（雷达回波）。
 *
 * <p>v10.1：WebView 的配置与白名单已抽到 {@link RainMapView}（悬浮底栏模式下降雨图会作为
 * 内嵌页复用同一份实现），本类只负责「窗口 / 系统栏 / 生命周期」这些宿主事务。
 *
 * <p>注意：悬浮底栏开启时，主页不再进入本 Activity，而是把降雨图作为内嵌页滑入
 * （底栏得以常驻）。两条路径都能用，行为与配置一致。
 */
public class RainMapActivity extends Activity {

    private RainMapView rain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashCatcher.install(this);
        setContentView(R.layout.activity_rain_map);
        // 主题化：深色/浅色（沉浸式：状态栏透明，背景延伸）
        boolean dark = Theme.isDark(this);
        getWindow().setStatusBarColor(0x00000000);
        getWindow().getDecorView().setSystemUiVisibility(
                (dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        final View rootMapV = findViewById(R.id.rootMap);
        rootMapV.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top = 0, bottom = 0;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets si =
                            insets.getInsets(WindowInsets.Type.systemBars());
                    top = si.top;
                    bottom = si.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(0, top, 0, bottom);
                return insets;
            }
        });
        RainMapView.applyTheme(this, rootMapV);
        overridePendingTransition(R.anim.rain_enter, R.anim.rain_exit);

        Intent it = getIntent();
        double lat = it.getDoubleExtra("lat", 35.0);
        double lng = it.getDoubleExtra("lng", 105.0);
        rain = RainMapView.attach(this, rootMapV, lat, lng, new Runnable() {
            @Override public void run() { finish(); }
        });
    }

    @Override
    public void onBackPressed() {
        if (rain != null && rain.canGoBack()) {
            rain.goBack(); // 白名单内的返回栈
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (rain != null) {
            rain.destroy();
            rain = null;
        }
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.rain_exit);
    }
}
