package com.simpleweather.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 降雨图（MSN 雷达回波）页面的 WebView 组件。
 *
 * <p><b>v10.1 抽出</b>：原先这套配置只存在于 {@link RainMapActivity}。
 * 悬浮底栏模式下降雨图要作为**内嵌页**留在主界面里（底栏才能常驻、页面才能横向滑入滑出），
 * 于是把「WebView 配置 + 双层白名单 + 加载」整体抽到这里，两种承载方式共用一份实现，
 * 避免两处各写一遍后慢慢跑偏。
 *
 * <p>网络安全（双层白名单）：
 * <ul>
 *   <li>主导航：仅允许 msn.com / msn.cn 的天气页路径，用户无法跳转到其他网站；</li>
 *   <li>子资源：放宽到微软生态域名（api/assets/广告/统计），保证页面正常渲染。</li>
 * </ul>
 *
 * <p>渲染优化：硬件层、DOM/App 缓存、渲染优先级 HIGH、双指缩放、WebView 数据目录隔离。
 */
public final class RainMapView {

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

    private final Activity act;
    private final TextView statusText;
    private final ProgressBar loadingBar;
    private WebView web;
    private volatile boolean destroyed = false;

    private RainMapView(Activity act, View root, double lat, double lng,
                        final Runnable onBack) {
        this.act = act;
        final TextView backBtn = (TextView) root.findViewById(R.id.backBtn);
        TextView refreshBtn = (TextView) root.findViewById(R.id.refreshBtn);
        statusText = (TextView) root.findViewById(R.id.statusText);
        loadingBar = (ProgressBar) root.findViewById(R.id.loadingBar);
        final FrameLayout container = (FrameLayout) root.findViewById(R.id.mapContainer);

        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (onBack != null) onBack.run();
            }
        });
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (web != null) web.reload();
            }
        });

        // WebView 数据目录隔离（Android 9+，减少与系统其他 WebView 实例冲突）
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try { WebView.setDataDirectorySuffix("weathertool"); } catch (Exception ignored) { }
        }

        web = new WebView(act);
        web.setBackgroundColor(Theme.isDark(act) ? 0xFF0D0F14 : 0xFFFDFCFF);
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
        s.setCacheMode(WebSettings.LOAD_DEFAULT);   // MSN 云图依赖 cookie 会话，WebView 自带 LRU 淘汰
        try { s.setRenderPriority(WebSettings.RenderPriority.HIGH); } catch (Exception ignored) { }
        // 平板/桌面 UA 无 Mobile 标记——MSN 会按桌面版渲染（瓦片大、资源多、载入慢）
        String ua = s.getUserAgentString();
        if (!ua.contains("Mobile")) {
            ua = ua.replaceFirst("\\) AppleWebKit", "; Mobile) AppleWebKit");
        }
        s.setUserAgentString(ua + " WeatherTool/9.62 (com.simpleweather.app)");

        web.setWebViewClient(new WebViewClient() {
            /** 主导航：非 MSN 天气页一律拦截（含外链跳转） */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl() == null ? "" : request.getUrl().toString();
                if (!allowNavigation(url)) {
                    if (!destroyed && statusText != null) {
                        statusText.setVisibility(View.VISIBLE);
                        statusText.setText("已拦截外部链接，仅允许访问 MSN 天气降雨图页");
                    }
                    return true;
                }
                return false;
            }

            /** 子资源：非白名单请求返回空响应；白名单直接放行（return null 最快） */
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                                                              WebResourceRequest request) {
                String url = request.getUrl() == null ? "" : request.getUrl().toString();
                if (!allowResource(url)) {
                    return new WebResourceResponse("text/plain", "utf-8", null);
                }
                return null;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (destroyed) return;
                if (loadingBar != null) loadingBar.setVisibility(View.GONE);
                if (statusText != null) statusText.setVisibility(View.GONE);
            }
        });

        container.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        if (statusText != null) statusText.setText("正在加载 MSN 天气降雨图…");
        web.loadUrl(String.format(java.util.Locale.US, MSN_RAIN_URL, lat, lng));
    }

    /**
     * 把 {@code activity_rain_map.xml} 根视图配置成一个可用的降雨图页面。
     *
     * @param root   已 inflate 的 {@code activity_rain_map} 根视图（Activity 或内嵌页容器都可）
     * @param onBack 点左上角返回时的动作（Activity 里是 finish()，内嵌页里是滑出关闭）
     */
    @SuppressLint("SetJavaScriptEnabled")
    public static RainMapView attach(Activity act, View root, double lat, double lng,
                                     Runnable onBack) {
        return new RainMapView(act, root, lat, lng, onBack);
    }

    /** 按主题给页面文字/进度条上色（Activity 与内嵌页共用） */
    public static void applyTheme(Activity act, View root) {
        boolean dark = Theme.isDark(act);
        root.setBackgroundColor(dark ? 0xFF111318 : 0xFFFDFCFF);
        int tp = dark ? 0xFFFFFFFF : 0xFF1F2A36;
        int ts = dark ? 0xAAFFFFFF : 0x8A5C6B7A;
        int th = dark ? 0x77FFFFFF : 0x595C6B7A;
        TextView b = (TextView) root.findViewById(R.id.backBtn);
        TextView t = (TextView) root.findViewById(R.id.mapTitle);
        TextView r = (TextView) root.findViewById(R.id.refreshBtn);
        TextView st = (TextView) root.findViewById(R.id.statusText);
        TextView ft = (TextView) root.findViewById(R.id.mapFoot);
        if (b != null) b.setTextColor(tp);
        if (t != null) t.setTextColor(tp);
        if (r != null) r.setTextColor(tp);
        if (st != null) st.setTextColor(ts);
        if (ft != null) ft.setTextColor(th);
        ProgressBar pb = (ProgressBar) root.findViewById(R.id.loadingBar);
        if (pb != null) {
            pb.setIndeterminateTintList(ColorStateList.valueOf(Theme.accent(act)));
        }
    }

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
    private static boolean allowNavigation(String url) {
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
    private static boolean allowResource(String url) {
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

    public boolean canGoBack() {
        return web != null && !destroyed && web.canGoBack();
    }

    public void goBack() {
        if (web != null && !destroyed) web.goBack();
    }

    public void reload() {
        if (web != null && !destroyed) web.reload();
    }

    /**
     * v10.1：换了坐标时重新加载 —— **复用同一个 WebView**，不重建、不销毁。
     * 页面进出不再触发重新加载：只有定位/手动城市真的变了才走这里。
     */
    public void load(double lat, double lng) {
        if (web == null || destroyed) return;
        if (statusText != null) {
            statusText.setVisibility(View.VISIBLE);
            statusText.setText("正在加载 MSN 天气降雨图…");
        }
        if (loadingBar != null) loadingBar.setVisibility(View.VISIBLE);
        web.loadUrl(String.format(java.util.Locale.US, MSN_RAIN_URL, lat, lng));
    }

    /** 宿主销毁时释放 WebView（不删 webcache：MSN 依赖会话 cookie） */
    public void destroy() {
        destroyed = true;
        if (web != null && web.getParent() instanceof FrameLayout) {
            ((FrameLayout) web.getParent()).removeView(web);
        }
        if (web != null) {
            web.destroy();
            web = null;
        }
    }

    public WebView webView() {
        return web;
    }
}
