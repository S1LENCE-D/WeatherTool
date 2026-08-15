package com.simpleweather.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * v9.39：月相自绘 View。
 * phase ∈ [0,1)：0=新月 0.25=上弦 0.5=满月 0.75=下弦。
 * 画法：暗圆盘为底，亮面 Path = 半圆 ± 椭圆（分段 4 区间），
 * 细月牙用 EVEN_ODD 挖空同侧椭圆，保证面积比例连续正确。
 */
public class MoonPhaseView extends View {

    private double phase = 0.25;   // 0..1
    private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF full = new RectF();
    private final RectF ell = new RectF();

    public MoonPhaseView(Context c) { super(c); init(); }
    public MoonPhaseView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        darkPaint.setColor(0xFF39404D);
        lightPaint.setColor(0xFFF4E8C8);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(2f);
        rimPaint.setColor(0x553F4B5C);
    }

    public void setPhase(double p) {
        phase = ((p % 1.0) + 1.0) % 1.0;
        invalidate();
    }

    /** 主题自适应配色：暗面 / 亮面 / 描边 */
    public void setColors(int darkColor, int lightColor, int rimColor) {
        darkPaint.setColor(darkColor);
        lightPaint.setColor(lightColor);
        rimPaint.setColor(rimColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float R = Math.min(w, h) / 2f - 6f;
        if (R <= 4f) return;

        canvas.drawCircle(cx, cy, R + 2f, rimPaint);
        canvas.drawCircle(cx, cy, R, darkPaint);

        full.set(cx - R, cy - R, cx + R, cy + R);
        double ang = 2 * Math.PI * phase;
        float a = (float) (Math.abs(Math.cos(ang)) * R);   // 边界椭圆半宽

        Path light = new Path();
        light.setFillType(Path.FillType.WINDING);
        if (phase < 0.25) {
            // 新月 -> 上弦：亮 = 右半圆 - 右椭圆
            light.addArc(full, -90, 180);
            ell.set(cx - a, cy - R, cx + a, cy + R);
            light.addArc(ell, -90, 180);
            light.setFillType(Path.FillType.EVEN_ODD);
        } else if (phase < 0.5) {
            // 上弦 -> 满月：亮 = 右半圆 + 左椭圆
            light.addArc(full, -90, 180);
            ell.set(cx - a, cy - R, cx + a, cy + R);
            light.addArc(ell, 90, 180);
        } else if (phase < 0.75) {
            // 满月 -> 下弦：亮 = 左半圆 + 右椭圆
            light.addArc(full, 90, 180);
            ell.set(cx - a, cy - R, cx + a, cy + R);
            light.addArc(ell, -90, 180);
        } else {
            // 下弦 -> 新月：亮 = 左半圆 - 左椭圆
            light.addArc(full, 90, 180);
            ell.set(cx - a, cy - R, cx + a, cy + R);
            light.addArc(ell, 90, 180);
            light.setFillType(Path.FillType.EVEN_ODD);
        }
        canvas.drawPath(light, lightPaint);
    }
}
