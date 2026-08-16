package com.simpleweather.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 二级页面：降雨图（雷达回波）。
 * 直接以 WebView 打开 MSN 天气降雨图页（含雷达、降水图层），
 * 并用经纬度 in-lat,lng 精确定位到当前位置。
 *
 * 网络安全（双层白名单）：
 *  - 主导航（shouldOverrideUrlLoading）：仅允许 msn.com / msn.cn 的天气页路径，
 *    用户无法跳转到任何其他网站；
 *  - 子资源（shouldInterceptRequest）：放宽到微软生态域名（api/assets/广告/统计），
 *    保证页面数据、图片、广告 SDK 正常加载，避免资源被拦导致渲染阻塞。
 *
 * 渲染优化：硬件层、DOM/App 缓存、渲染优先级 HIGH、双指缩放、WebView 数据目录隔离。
 */
public class RainMapActivity extends Activity {

    /** MSN 降雨图页（国内自动跳转 www.msn.cn，白名单已覆盖） */
    private static final String MSN_RAIN_URL =
            "https://www.msn.com/zh-cn/weather/maps/precipitation/in-%f,%f";

    /** 子资源白名单：微软生态 + 页面所需 CDN/广告域（按域尾缀匹配，含子域） */
    private static final String[] RESOURCE_HOST_SUFFIX = {
            "msn.com", "msn.cn",          // MSN 本体（页面/接口/静态资源）
            "akamaized.net",              // MSN 图片/地图图块 CDN
            "microsoft.com",              // 微软 SDK / 遥测
            "live.com",                   // 账号体系
            "bing.com",                   // Bing 分析/搜索组件
            "msedge.net",                 // Edge 静态资源
            "azureedge.net",              // Azure CDN
            "btloader.com",               // 广告加载器（被拦会拖慢首屏）
    };

    private FrameLayout container;
    private TextView statusText, backBtn, refreshBtn;
    private ProgressBar loadingBar;
    private WebView web;

    private double lat = 35.0, lng = 105.0;
    private boolean destroyed = false;

    /** 轻量提取 host（避免 Uri.parse 逐资源开销） */
    private static String hostOf(String url) {
        int i = url.indexOf("://");
        int s = i >= 0 ? i + 3 : 0;
        int e = url.indexOf('/', s);
        if (e < 0) e = url.length();
        String h = url.substring(s, e);
        int c = h.indexOf(':');
        if (c > 0) h = h.substring(0, c);
        return h;
    }

    /** 轻量提取路径 */
    private static String pathOf(String url) {
        int i = url.indexOf("://");
        int s = i >= 0 ? i + 3 : 0;
        int e = url.indexOf('/', s);
        return e < 0 ? "" : url.substring(e);
    }

    /** 主导航白名单：仅 msn 系天气页（用户无法跳去其他网站） */
    private boolean allowNavigation(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        if (u.startsWith("about:") || u.startsWith("blob:")
                || u.startsWith("data:") || u.startsWith("javascript:")) {
            return true;
        }
        String host = hostOf(u);
        boolean msn = host.equals("msn.com") || host.endsWith(".msn.com")
                || host.equals("msn.cn") || host.endsWith(".msn.cn");
        if (!msn) return false;
        String path = pathOf(u);
        return path.isEmpty() || path.equals("/")
                || path.startsWith("/zh-cn/weather") || path.startsWith("/weather");
    }

    /** 子资源白名单：微软生态域名放行，其余（外链图片/追踪器等）拦截 */
    private boolean allowResource(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase();
        if (u.startsWith("about:") || u.startsWith("blob:")
                || u.startsWith("data:") || u.startsWith("javascript:")
                || u.startsWith("http://localhost") || u.startsWith("https://localhost")) {
            return true;
        }
        String host = hostOf(u);
        for (String suf : RESOURCE_HOST_SUFFIX) {
            if (host.equals(suf) || host.endsWith("." + suf)) return true;
        }
        return false;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashCatcher.install(this);
        setContentView(R.layout.activity_rain_map);
        // 主题化：深色/浅色（v9.8 沉浸式：状态栏透明，背景延伸）
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
        findViewById(R.id.rootMap).setBackgroundColor(dark ? 0xFF111318 : 0xFFFDFCFF);
        int tp = dark ? 0xFFFFFFFF : 0xFF1F2A36;
        int ts = dark ? 0xAAFFFFFF : 0x8A5C6B7A;
        int th = dark ? 0x77FFFFFF : 0x595C6B7A;
        ((TextView) findViewById(R.id.backBtn)).setTextColor(tp);
        ((TextView) findViewById(R.id.mapTitle)).setTextColor(tp);
        ((TextView) findViewById(R.id.refreshBtn)).setTextColor(tp);
        ((TextView) findViewById(R.id.statusText)).setTextColor(ts);
        ((TextView) findViewById(R.id.mapFoot)).setTextColor(th);
        if (Build.VERSION.SDK_INT >= 21) {
            android.widget.ProgressBar pb = (android.widget.ProgressBar) findViewById(R.id.loadingBar);
            if (pb != null) pb.setIndeterminateTintList(
                    android.content.res.ColorStateList.valueOf(Theme.accent(this)));
        }
        overridePendingTransition(R.anim.rain_enter, R.anim.rain_exit);

        // WebView 数据目录隔离（Android 9+，减少与系统其他 WebView 实例冲突）
        if (Build.VERSION.SDK_INT >= 28) {
            try { WebView.setDataDirectorySuffix("weathertool"); } catch (Exception ignored) { }
        }

        Intent it = getIntent();
        lat = it.getDoubleExtra("lat", lat);
        lng = it.getDoubleExtra("lng", lng);

        container = findViewById(R.id.mapContainer);
        statusText = findViewById(R.id.statusText);
        loadingBar = findViewById(R.id.loadingBar);
        backBtn = findViewById(R.id.backBtn);
        refreshBtn = findViewById(R.id.refreshBtn);

        Fonts.load(this);
        Fonts.apply(findViewById(android.R.id.content));

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { finish(); }
        });
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (web != null) web.reload();
            }
        });

        // WebView：渲染优化 + 双层白名单
        web = new WebView(this);
        web.setBackgroundColor(Theme.isDark(this) ? 0xFF0D0F14 : 0xFFFDFCFF);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);   // 硬件加速渲染
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                        // localStorage
        s.setDatabaseEnabled(true);                          // WebSQL
        // v9.90：AppCache 系列 API 已从 API 37 的 android.jar 移除（现代 WebView 自带缓存），不再设置
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setLoadsImagesAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);   // v9.73 修订：保留缓存——MSN 云图依赖 cookie 会话，WebView 自带 LRU 淘汰
        try { s.setRenderPriority(WebSettings.RenderPriority.HIGH); } catch (Exception ignored) { }
        s.setUserAgentString(s.getUserAgentString()
                + " WeatherTool/9.62 (com.simpleweather.app)");

        web.setWebViewClient(new WebViewClient() {
            /** 主导航：非 MSN 天气页一律拦截（含外链跳转） */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl() == null ? "" : request.getUrl().toString();
                if (!allowNavigation(url)) {
                    if (!destroyed) {
                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText("已拦截外部链接，仅允许访问 MSN 天气降雨图页");
                    }
                    return true; // 阻止加载
                }
                return false;    // 白名单内继续在 WebView 中加载
            }

            /** 子资源：非白名单请求返回空响应；白名单直接放行（return null 最快） */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl() == null ? "" : request.getUrl().toString();
                if (!allowResource(url)) {
                    return new WebResourceResponse("text/plain", "utf-8", null);
                }
                return null;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (destroyed) return;
                loadingBar.setVisibility(View.GONE);
                statusText.setVisibility(View.GONE);
            }
        });

        container.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        String target = String.format(java.util.Locale.US, MSN_RAIN_URL, lat, lng);
        statusText.setText("正在加载 MSN 天气降雨图…");
        web.loadUrl(target);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack(); // 白名单内的返回栈
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (web != null) {
            container.removeView(web);
            web.destroy();
            web = null;
        }
        // v9.73 修订：不删 webcache——MSN 云图 cookie/会话缓存需保留，体积由 WebView 自管理
        super.onDestroy();
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.rain_exit);
    }
}
