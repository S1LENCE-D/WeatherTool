package com.simpleweather.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * v9.79：自绘滚轮选择器（M3 风格，替代 ▲▼ 步进）：
 * - 显示 5 行（中心高亮 + 上下各 2 行淡出缩小），垂直拖动换值；
 * - 松手回中动画（120ms）；值在 min..max 间循环滚动（小时 0-23 / 分钟 0-59）；
 * - 深/浅主题自动适配，中心行有浅色高亮底。
 */
public class WheelView extends View {

    public interface OnValueChangedListener {
        void onValueChanged(WheelView w, int value);
    }

    private int min = 0, max = 23, value = 0;
    private float itemH;
    private float offset = 0f;      // 内容位移 px（正 = 内容下移）
    private float lastY = 0f;
    private ValueAnimator snap;
    private OnValueChangedListener listener;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public WheelView(Context c) { this(c, null); }

    public WheelView(Context c, AttributeSet a) { this(c, a, 0); }

    public WheelView(Context c, AttributeSet a, int style) {
        super(c, a, style);
        density = c.getResources().getDisplayMetrics().density;
        // 大字版：行高随 fontScale 联动（字号被放大时行高同步放大，避免文字挤压重叠）
        itemH = 40 * density * Math.max(1f, c.getResources().getConfiguration().fontScale);
        boolean dark = Theme.isDark(c);
        activeColor = dark ? 0xFFFFFFFF : 0xFF1F2A36;
        textColor = dark ? 0x59FFFFFF : 0x556B7280;
        centerBg = dark ? 0x14FFFFFF : 0x0F2F6FEB;
    }

    private int activeColor;
    private int textColor;
    private int centerBg;

    public void setRange(int min, int max, int value) {
        this.min = min;
        this.max = max;
        this.value = value;
        invalidate();
    }

    public int getValue() { return value; }

    public void setOnValueChangedListener(OnValueChangedListener l) { listener = l; }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) (76 * density), (int) (itemH * 5));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastY = ev.getY();
                if (snap != null) snap.cancel();
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE: {
                float dy = ev.getY() - lastY;
                lastY = ev.getY();
                offset += dy;
                int step = (int) (offset / itemH);
                if (step != 0) {
                    offset -= step * itemH;
                    // 手指下滑 -> 内容下移 -> 中心显示更小的值（符合滚轮直觉）
                    value = wrap(value - step);
                    if (listener != null) listener.onValueChanged(this, value);
                }
                invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                snapBack();
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return true;
    }

    /** min..max 循环取模 */
    private int wrap(int v) {
        int n = max - min + 1;
        v = (v - min) % n;
        if (v < 0) v += n;
        return v + min;
    }

    private void snapBack() {
        if (snap != null) snap.cancel();
        final float from = offset;
        snap = ValueAnimator.ofFloat(from, 0f);
        snap.setDuration(120);
        snap.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                offset = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        snap.start();
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        float cy = getHeight() / 2f;

        // 中心行高亮底（M3 tonal 风格）
        bgPaint.setColor(centerBg);
        RectF bg = new RectF(0, cy - itemH / 2f, getWidth(), cy + itemH / 2f);
        cv.drawRoundRect(bg, 12 * density, 12 * density, bgPaint);

        for (int i = -2; i <= 2; i++) {
            int v = wrap(value + i);
            float y = cy + i * itemH + offset;   // offset>0 内容下移
            int dist = Math.abs(i);
            float scale = 1f - dist * 0.24f;
            if (dist == 0) {
                paint.setColor(activeColor);
                paint.setTextSize(30 * density);
            } else if (dist == 1) {
                paint.setColor(blend(activeColor, textColor, 0.55f));
                paint.setTextSize(21 * density);
            } else {
                paint.setColor(textColor);
                paint.setTextSize(16 * density);
            }
            paint.setFakeBoldText(dist == 0);
            String s = String.format("%02d", v);
            float w = paint.measureText(s);
            cv.drawText(s, (getWidth() - w) / 2f, y + paint.getTextSize() * 0.36f, paint);
        }
    }

    private static int blend(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }
}
