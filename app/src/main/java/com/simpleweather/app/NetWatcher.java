package com.simpleweather.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** v9.49/9.51：断网检测与置顶提示。
 *  NetworkCallback 实时监听「可上网」状态（NET_CAPABILITY_VALIDATED 为准，
 *  Wi-Fi 连着但无外网同样判定为断网）：
 *  断网持续满 2 秒才弹窗（防闪断误报）；弹窗带全屏半透明遮罩，
 *  拦截主页一切滑动/点击（不允许操作主页），卡片居中；
 *  有悬浮窗权限则系统级全局置顶，无权限则应用内全屏遮罩；
 *  卡片带「去设置」直达 Wi-Fi 设置，带「✕」手动关闭；网络恢复自动消失。 */
public class NetWatcher {
    public interface Listener {
        void onOffline();
        void onOnline();
    }

    private final Activity act;
    private final Context ctx;
    private final Listener listener;
    private ConnectivityManager cm;
    private NetworkCallback cb;
    private boolean online = true;
    private boolean started = false;

    private View overlay;   // 系统级悬浮窗（全屏遮罩 + 居中卡片）
    private View banner;    // 应用内全屏遮罩（无悬浮窗权限时）
    private View cardView;  // v9.60：居中卡片引用（弹出/关闭动画用）
    private boolean animatingOut = false;   // 关闭动画进行中，防重复移除
    private WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** v9.51：断网持续 2 秒才提示，2 秒内恢复则取消 */
    private final Runnable offlineTask = new Runnable() {
        @Override
        public void run() {
            showOfflineUi();
            if (listener != null) listener.onOffline();
        }
    };

    public NetWatcher(Activity activity, Listener l) {
        act = activity;
        ctx = activity.getApplicationContext();
        listener = l;
        wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    public void start() {
        if (started) return;
        started = true;
        cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        cb = new NetworkCallback();
        try {
            // API 24+：默认网络回调，注册后立即回调一次当前状态
            cm.registerDefaultNetworkCallback(cb);
        } catch (Exception e) {
            try {
                NetworkRequest req = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
                cm.registerNetworkCallback(req, cb);
            } catch (Exception ignored) { }
        }
    }

    public void stop() {
        if (!started) return;
        started = false;
        ui.removeCallbacks(offlineTask);
        try {
            if (cm != null && cb != null) cm.unregisterNetworkCallback(cb);
        } catch (Exception ignored) { }
        removeAll();
    }

    public boolean isOffline() { return !online; }

    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) { }
        @Override
        public void onLost(Network network) { setOnline(false); }
        @Override
        public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
            // VALIDATED：该网络已通过系统验证、确实能上网
            boolean ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            setOnline(ok);
        }
    }

    private void setOnline(final boolean ok) {
        if (online == ok) return;   // 状态未变化不打扰
        online = ok;
        ui.removeCallbacks(offlineTask);
        if (online) {
            removeAll();
            if (listener != null) listener.onOnline();
        } else {
            // v9.51：断网持续 2 秒才提示（防短暂切换误报）
            ui.postDelayed(offlineTask, 2000);
        }
    }

    private void showOfflineUi() {
        if (canOverlay()) {
            if (!showOverlay()) showBanner();   // 悬浮窗失败则横幅兜底
        } else {
            showBanner();
        }
    }

    private boolean canOverlay() {
        try { return Settings.canDrawOverlays(ctx); } catch (Exception e) { return false; }
    }

    // ---------- 系统级悬浮窗（全局置顶 + 全屏遮罩） ----------
    private boolean showOverlay() {
        try {
            if (overlay != null) return true;
            View wrap = buildOverlayView();
            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            wm.addView(wrap, lp);
            overlay = wrap;
            playIn(wrap);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 应用内全屏遮罩（无悬浮窗权限时） ----------
    private void showBanner() {
        try {
            if (banner != null) return;
            View wrap = buildOverlayView();
            FrameLayout root = (FrameLayout) act.findViewById(android.R.id.content);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            root.addView(wrap, lp);
            banner = wrap;
            playIn(wrap);
        } catch (Exception ignored) { }
    }

    /** v9.60：关闭动画——卡片下移淡出 + 遮罩淡出，动画结束才真正移除（防闪退/残留） */
    private void removeAll() {
        if (animatingOut) return;
        final View o = overlay, b = banner;
        if (o == null && b == null) return;
        overlay = null;
        banner = null;
        animatingOut = true;
        final Runnable detach = new Runnable() {
            @Override
            public void run() {
                try { if (o != null) wm.removeView(o); } catch (Exception ignored) { }
                try {
                    if (b != null) {
                        FrameLayout root = (FrameLayout) act.findViewById(android.R.id.content);
                        root.removeView(b);
                    }
                } catch (Exception ignored) { }
                animatingOut = false;
            }
        };
        if (cardView != null) {
            cardView.animate().alpha(0f).translationY(dp(18))
                    .setDuration(200).setInterpolator(new DecelerateInterpolator());
        }
        if (o != null) o.animate().alpha(0f).setDuration(240).withEndAction(detach);
        else if (b != null) b.animate().alpha(0f).setDuration(240).withEndAction(detach);
    }

    /** v9.60：弹出动画——遮罩淡入，卡片自下方上浮淡入（卡片后落定，节奏更自然） */
    private void playIn(final View wrap) {
        wrap.setAlpha(0f);
        if (cardView != null) {
            cardView.setAlpha(0f);
            cardView.setTranslationY(dp(26));
        }
        wrap.post(new Runnable() {
            @Override
            public void run() {
                wrap.animate().alpha(1f).setDuration(240)
                        .setInterpolator(new DecelerateInterpolator());
                if (cardView != null) {
                    cardView.animate().alpha(1f).translationY(0f).setDuration(340)
                            .setInterpolator(new DecelerateInterpolator());
                }
            }
        });
    }

    /** v9.51：全屏半透明遮罩 + 居中提示卡片（遮罩拦截主页一切滑动/点击） */
    private View buildOverlayView() {
        FrameLayout wrap = new FrameLayout(ctx);
        wrap.setBackgroundColor(0x99000000);   // 半透明遮罩
        View card = buildCard();
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;
        clp.leftMargin = dp(26);
        clp.rightMargin = dp(26);
        wrap.addView(card, clp);
        cardView = card;   // v9.60：供弹出/关闭动画使用
        return wrap;
    }

    /** 构建提示卡片：警告图标 + 说明 + 「去设置」按钮 + 关闭（v9.73：Material Icons 字形） */
    private View buildCard() {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1F2933);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x553B82F6);
        card.setBackground(bg);

        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        TextView warn = new TextView(ctx);
        warn.setText(Fonts.mixIcon("\uE002", "网络已断开"));   // v9.73：Material Icons warning 字形
        warn.setTextColor(0xFFFFFFFF);
        warn.setTextSize(16f);
        warn.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lpW = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row1.addView(warn, lpW);

        TextView close = new TextView(ctx);
        close.setText("\uE5CD");                       // v9.73：Material Icons close 字形
        close.setTypeface(Fonts.icons());
        close.setTextColor(0xAAFFFFFF);
        close.setTextSize(16f);
        close.setPadding(dp(10), 0, 0, 0);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { removeAll(); }
        });
        row1.addView(close);
        card.addView(row1);

        TextView desc = new TextView(ctx);
        desc.setText("天气与预警暂不可用，请检查网络连接");
        desc.setTextColor(0xAAFFFFFF);
        desc.setTextSize(12.5f);
        desc.setPadding(0, dp(6), 0, dp(10));
        card.addView(desc);

        Button go = new Button(ctx);
        go.setText("去设置网络  ›");
        go.setTextColor(0xFFFFFFFF);
        go.setTextSize(14f);
        go.setAllCaps(false);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(0xFF3B82F6);
        btnBg.setCornerRadius(dp(11));
        go.setBackground(btnBg);
        go.setMinHeight(dp(40));
        go.setPadding(dp(18), 0, dp(18), 0);
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);   // v9.50：直达 Wi-Fi 设置
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                } catch (Exception ignored) { }
            }
        });
        card.addView(go);
        Fonts.apply(card);   // v9.57：强制 Google Sans
        return card;
    }

    private int dp(float v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density + 0.5f);
    }
}
