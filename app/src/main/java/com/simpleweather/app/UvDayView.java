package com.simpleweather.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.Calendar;

/**
 * v9.68：今日 UV 走势图（24 小时柱状时间线，参考 UVLens 的每日 UV 预报）。
 * 三态：拉取中（loading）/ 拉取失败 / 有数据。柱按 UV 档位着色，
 * 当前小时柱描边高亮；底部时刻按数据真实小时标注。
 */
public class UvDayView extends View {

    private double[] uv;
    private int[] hour;
    private int nowIdx = -1;
    private boolean loading = false;
    private boolean failed = false;
    private int[] levelColors = {
            0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFFF44336, 0xFF9C27B0};
    private int textColor = 0xFF9AA0A8;
    private int accent = 0xFF0061A4;
    private final float density;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txtPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public UvDayView(Context context) {
        this(context, null);
    }

    public UvDayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        txtPaint.setTextSize(11f * density);
        txtPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        linePaint.setStrokeWidth(density);
    }

    /** 数据就绪（或已缓存）：设置 UV 数组与对应小时，自动定位当前小时 */
    public void setData(double[] uv, int[] hour, int[] levelColors,
                        int textColor, int accent) {
        this.loading = false;
        this.failed = false;
        this.uv = uv;
        this.hour = hour;
        if (levelColors != null) this.levelColors = levelColors;
        this.textColor = textColor;
        this.accent = accent;
        computeNowIdx();
        invalidate();
    }

    /** 后台刷新完成后仅更新数据（颜色配置保持不变） */
    public void applyData(double[] uv, int[] hour) {
        this.loading = false;
        this.failed = false;
        this.uv = uv;
        this.hour = hour;
        computeNowIdx();
        invalidate();
    }

    /** 拉取中状态（打开弹窗时数据未就绪） */
    public void setLoading(boolean l) {
        this.loading = l;
        this.failed = false;
        invalidate();
    }

    /** 拉取失败 */
    public void setFailed() {
        this.failed = true;
        this.loading = false;
        invalidate();
    }

    private void computeNowIdx() {
        nowIdx = -1;
        if (hour == null) return;
        int hh = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        for (int i = 0; i < hour.length; i++) {
            if (hour[i] == hh) { nowIdx = i; break; }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, (int) (84f * density));
    }

    /** 与 MainActivity.uvLevel 一致的档位划分（0-2/3-5/6-7/8-10/11+） */
    private static int uvLevel(double v) {
        if (v >= 11) return 4;
        if (v >= 8) return 3;
        if (v >= 6) return 2;
        if (v >= 3) return 1;
        return 0;
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();

        // 三态提示
        if (loading) {
            txtPaint.setColor(textColor);
            txtPaint.setTextAlign(Paint.Align.LEFT);
            c.drawText("正在拉取今日 UV 数据…", dp(10f), h / 2f, txtPaint);
            return;
        }
        if (failed) {
            txtPaint.setColor(textColor);
            txtPaint.setTextAlign(Paint.Align.LEFT);
            c.drawText("UV 数据拉取失败，请点右上角刷新重试", dp(10f), h / 2f, txtPaint);
            return;
        }
        if (uv == null || uv.length == 0) {
            txtPaint.setColor(textColor);
            txtPaint.setTextAlign(Paint.Align.LEFT);
            c.drawText("暂无逐小时 UV 数据", dp(10f), h / 2f, txtPaint);
            return;
        }

        int chartH = (int) dp(60f);
        int left = (int) dp(6f);
        int right = (int) dp(6f);
        float inner = w - left - right;

        double max = 1;
        for (double v : uv) max = Math.max(max, v);

        int n = uv.length;
        float bw = inner / n;
        for (int i = 0; i < n; i++) {
            float bh = (float) (uv[i] / max * (chartH - dp(6f)));
            float x = left + i * bw + bw * 0.16f;
            float bw2 = bw * 0.68f;

            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(levelColors[uvLevel(uv[i])]);
            barPaint.setAlpha(185);
            c.drawRoundRect(x, chartH - bh, x + bw2, chartH, dp(2f), dp(2f), barPaint);

            if (i == nowIdx) {
                barPaint.setAlpha(255);
                barPaint.setStyle(Paint.Style.STROKE);
                barPaint.setStrokeWidth(dp(1.5f));
                barPaint.setColor(accent);
                c.drawRoundRect(x - dp(1f), chartH - bh - dp(2f),
                        x + bw2 + dp(1f), chartH + dp(2f),
                        dp(3f), dp(3f), barPaint);
            }
        }

        // 基线
        linePaint.setColor(textColor);
        linePaint.setAlpha(90);
        c.drawLine(left, chartH + dp(1f), w - right, chartH + dp(1f), linePaint);

        // 底部时刻标注：按数据真实小时（首 / 1/3 / 2/3 / 末）
        txtPaint.setColor(textColor);
        txtPaint.setTextAlign(Paint.Align.LEFT);
        if (hour != null && hour.length == n) {
            int[] idxs = {0, n / 3, n * 2 / 3, n - 1};
            for (int idx : idxs) {
                float x = left + idx * bw;
                c.drawText(hour[idx] + "时", x, h - dp(4f), txtPaint);
            }
        }
    }
}
