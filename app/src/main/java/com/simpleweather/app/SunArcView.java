package com.simpleweather.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * 日出日落弧线：半圆弧 + 太阳当前位置（Material 风格细线）
 */
public class SunArcView extends View {

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int sunriseMin = 6 * 60;      // 默认 06:00
    private int sunsetMin = 18 * 60;      // 默认 18:00
    private float ratio = 0.5f;           // 太阳在弧上的位置 0~1
    private boolean sunUp = true;         // 白天还是夜间

    public SunArcView(Context context) {
        super(context);
        init();
    }

    public SunArcView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        arcPaint.setColor(0xFFB3C7E8);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(3));
        arcPaint.setStrokeCap(Paint.Cap.ROUND);

        sunPaint.setColor(0xFFFFC94D);
        sunPaint.setStyle(Paint.Style.FILL);

        glowPaint.setColor(0x55FFC94D);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    /** 传入 "05:32" / "19:08" */
    public void setTimes(String sunrise, String sunset) {
        sunriseMin = parse(sunrise, 6 * 60);
        sunsetMin = parse(sunset, 18 * 60);
        updateRatio(true);
    }

    /** 主题自适应配色：弧线 / 太阳 / 光晕 */
    public void setColors(int arcColor, int sunColor, int glowColor) {
        arcPaint.setColor(arcColor);
        sunPaint.setColor(sunColor);
        glowPaint.setColor(glowColor);
        invalidate();
    }

    private static int parse(String s, int def) {
        try {
            String[] p = s.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) {
            return def;
        }
    }

    private void updateRatio(boolean animate) {
        Calendar c = Calendar.getInstance(TimeZone.getDefault());
        int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int span = sunsetMin - sunriseMin;
        if (span <= 0) span = 12 * 60;
        float target = (now - sunriseMin) / (float) span;
        if (target < 0f) target = 0f;
        if (target > 1f) target = 1f;
        sunUp = now >= sunriseMin && now <= sunsetMin;
        animateRatio(target, animate);
    }

    /** 太阳位置平滑移动 */
    private void animateRatio(final float target, boolean animate) {
        if (!animate) {
            ratio = target;
            invalidate();
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(ratio, target);
        va.setDuration(700);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                ratio = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        va.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        // v9.87：安全边界——太阳光晕(dp16)+弧线宽，避免日出日落端点太阳被组件边界裁切
        float pad = dp(20);
        float cx = w / 2f;
        float cy = h - pad;              // 弧心贴安全区底部
        float r = Math.min(w / 2f - pad, h - 2f * pad);
        if (r <= 0) return;

        // 半圆弧（左 -> 右）
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 180f, 180f, false, arcPaint);

        // 太阳位置
        float ang = (float) Math.PI * (1f - ratio);
        float sx = cx + r * (float) Math.cos(ang);
        float sy = cy - r * (float) Math.sin(ang);

        int alpha = sunUp ? 255 : 120;
        glowPaint.setAlpha((int) (0x55 * alpha / 255));
        sunPaint.setAlpha(alpha);

        canvas.drawCircle(sx, sy, dp(16), glowPaint);
        canvas.drawCircle(sx, sy, dp(7), sunPaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
