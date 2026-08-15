package com.simpleweather.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

/**
 * v9.60：罗盘风向标——自绘渐变圆盘 + 北/东/南/西 + 旋转指针。
 * 指针绕圆心精确旋转，箭头指向风的来向（dirDeg=0 时指北/正上方）。
 */
public class CompassView extends View {

    private float dirDeg = 0f;
    private int discCenter = 0xFF2A3448;   // 圆盘中心色（径向渐变）
    private int discEdge = 0xFF1A2332;     // 圆盘边缘色
    private int borderColor = 0xFF33415C;
    private int textColor = 0xFF9AA0A8;
    private int arrowColor = 0xFF10B981;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CompassView(Context c) {
        super(c);
        init();
    }

    public CompassView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 10f, getResources().getDisplayMetrics()));
        arrowPaint.setStyle(Paint.Style.FILL);
    }

    /** 设置风向（度，0=北，顺时针） */
    public void setDirection(float deg) {
        dirDeg = deg;
        invalidate();
    }

    /** 设置配色：圆盘中心 / 圆盘边缘 / 边框 / 方位字 / 指针 */
    public void setColors(int discCenter, int discEdge, int border, int text, int arrow) {
        this.discCenter = discCenter;
        this.discEdge = discEdge;
        this.borderColor = border;
        this.textColor = text;
        this.arrowColor = arrow;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - dp(1);

        // 圆盘：径向渐变（中心亮、边缘深），比纯色更现代
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx, cy, r, discCenter, discEdge,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(borderColor);
        canvas.drawCircle(cx, cy, r, paint);

        // 方位文字（textPaint 基线居中处理）
        textPaint.setColor(textColor);
        float lr = r - dp(11);
        float base = textPaint.getTextSize() / 3f;
        canvas.drawText("北", cx, cy - lr + base, textPaint);
        canvas.drawText("南", cx, cy + lr + base, textPaint);
        canvas.drawText("东", cx + lr, cy + base, textPaint);
        canvas.drawText("西", cx - lr, cy + base, textPaint);

        // 指针：绕圆心旋转，箭头尖端指向风的来向
        canvas.save();
        canvas.rotate(dirDeg, cx, cy);
        float tip = r - dp(14);
        float tail = r - dp(24);
        float half = dp(6.5f);
        Path p = new Path();
        p.moveTo(cx, cy - tip);            // 尖端
        p.lineTo(cx - half, cy + tail);    // 左尾
        p.lineTo(cx, cy + tail - dp(5));   // 尾部凹口
        p.lineTo(cx + half, cy + tail);    // 右尾
        p.close();
        arrowPaint.setColor(arrowColor);
        canvas.drawPath(p, arrowPaint);
        // 中心轴点
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(borderColor);
        canvas.drawCircle(cx, cy, dp(2.5f), paint);
        canvas.restore();
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
