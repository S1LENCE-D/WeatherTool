package com.simpleweather.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/**
 * v9.87：设置面板横向分页容器。
 * 项目不使用 Gradle / AndroidX，framework 里没有 ViewPager，故基于
 * HorizontalScrollView 实现「分页滑动 + 松手吸附」，并标记滚动状态
 * 供子项在滑动过程中抑制点击（防误触 3a）。
 */
public class SettingsPager extends HorizontalScrollView {

    public interface OnPageChangeListener {
        void onPage(int index);
    }

    private LinearLayout content;
    private int pageCount = 0;
    private int pageWidth = 0;
    private int current = 0;
    private boolean scrolling = false;
    private OnPageChangeListener pageListener;

    public SettingsPager(Context c) { this(c, null); }

    public SettingsPager(Context c, AttributeSet a) {
        super(c, a);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
        content = new LinearLayout(c);
        content.setOrientation(LinearLayout.HORIZONTAL);
        addView(content, new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public void setOnPageChangeListener(OnPageChangeListener l) {
        pageListener = l;
    }

    /** 每页宽度（= 面板可视宽度） */
    public void setPageWidth(int w) {
        pageWidth = w;
    }

    public void addPage(android.view.View page) {
        content.addView(page, new LinearLayout.LayoutParams(
                pageWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
        pageCount++;
    }

    public int getPageCount() { return pageCount; }

    public int getCurrentPage() { return current; }

    /** 是否正在横向滚动（供子项防误触判断） */
    public boolean isScrolling() { return scrolling; }

    public void setCurrentPage(int idx, boolean smooth) {
        current = Math.max(0, Math.min(idx, pageCount - 1));
        final int x = current * pageWidth;
        if (smooth) smoothScrollTo(x, 0);
        else scrollTo(x, 0);
        notifyPage();
    }

    private final Runnable stopScroll = new Runnable() {
        @Override public void run() { scrolling = false; }
    };

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        scrolling = true;
        removeCallbacks(stopScroll);
        postDelayed(stopScroll, 120);
        if (pageWidth > 0) {
            int idx = Math.round((float) l / pageWidth);
            idx = Math.max(0, Math.min(idx, pageCount - 1));
            if (idx != current) {
                current = idx;
                notifyPage();
            }
        }
    }

    private void notifyPage() {
        if (pageListener != null) pageListener.onPage(current);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getActionMasked() == MotionEvent.ACTION_UP
                || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            // 松手吸附到最近一页
            final int x = getScrollX();
            final int idx = pageWidth > 0 ? Math.round((float) x / pageWidth) : 0;
            post(new Runnable() {
                @Override public void run() { setCurrentPage(idx, true); }
            });
        }
        return super.onTouchEvent(ev);
    }
}
