package com.simpleweather.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * v9.104.2：自绘壁纸裁剪视图——图片可拖动/双指缩放/旋转，固定屏幕比例裁剪框，
 * 框外半透明遮罩 + 三等分网格参考线。
 */
public class CropView extends View {

    private Bitmap src;
    private final Matrix matrix = new Matrix();
    private final Matrix inv = new Matrix();
    private final RectF cropRect = new RectF();
    private final Paint maskPaint = new Paint();
    private final Paint linePaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final ScaleGestureDetector scaleDetector;
    private float lastX, lastY;
    private boolean firstLayout = true;

    public CropView(Context c, AttributeSet a) {
        super(c, a);
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                matrix.postScale(d.getScaleFactor(), d.getScaleFactor(),
                        d.getFocusX(), d.getFocusY());
                clamp();
                invalidate();
                return true;
            }
        });
        maskPaint.setColor(0x99000000);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1.5f));
        linePaint.setColor(0xFFFFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(0.7f));
        gridPaint.setColor(0x66FFFFFF);
    }

    public CropView(Context c) {
        this(c, null);
    }

    public Bitmap getSrc() {
        return src;
    }

    public void setImage(Bitmap bm) {
        src = bm;
        firstLayout = true;
        requestLayout();
        invalidate();
    }

    public void rotate() {
        matrix.postRotate(90f, cropRect.centerX(), cropRect.centerY());
        clamp();
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density + 0.5f;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (firstLayout && src != null && getWidth() > 0 && getHeight() > 0) {
            firstLayout = false;
            float cw = getWidth() - dp(32);
            float ch = cw * ((float) getHeight() / getWidth());
            if (ch > getHeight() - dp(150)) {
                ch = getHeight() - dp(150);
                cw = ch * ((float) getWidth() / getHeight());
            }
            cropRect.set((getWidth() - cw) / 2f, (getHeight() - ch) / 2f,
                    (getWidth() + cw) / 2f, (getHeight() + ch) / 2f);
            // 初始：图片 cover 裁剪框
            float s = Math.max(cropRect.width() / src.getWidth(),
                    cropRect.height() / src.getHeight());
            matrix.reset();
            matrix.postScale(s, s);
            matrix.postTranslate(cropRect.centerX() - src.getWidth() * s / 2f,
                    cropRect.centerY() - src.getHeight() * s / 2f);
            clamp();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (src == null) return;
        canvas.drawBitmap(src, matrix, null);
        // 框外遮罩
        canvas.drawRect(0, 0, getWidth(), cropRect.top, maskPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), maskPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, maskPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, maskPaint);
        // 裁剪框
        canvas.drawRect(cropRect, linePaint);
        // 三等分网格
        float w3 = cropRect.width() / 3f, h3 = cropRect.height() / 3f;
        for (int i = 1; i < 3; i++) {
            canvas.drawLine(cropRect.left + w3 * i, cropRect.top,
                    cropRect.left + w3 * i, cropRect.bottom, gridPaint);
            canvas.drawLine(cropRect.left, cropRect.top + h3 * i,
                    cropRect.right, cropRect.top + h3 * i, gridPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    float dx = e.getX() - lastX, dy = e.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    clamp();
                    invalidate();
                }
                lastX = e.getX();
                lastY = e.getY();
                break;
        }
        return true;
    }

    /** 限制图片包围盒至少覆盖裁剪框，且不能拖出太远 */
    private void clamp() {
        if (src == null || getWidth() == 0) return;
        RectF r = new RectF(0, 0, src.getWidth(), src.getHeight());
        matrix.mapRect(r);
        if (r.width() < cropRect.width()) {
            float s = cropRect.width() / r.width();
            matrix.postScale(s, s, cropRect.centerX(), cropRect.centerY());
            r.set(0, 0, src.getWidth(), src.getHeight());
            matrix.mapRect(r);
        }
        if (r.height() < cropRect.height()) {
            float s = cropRect.height() / r.height();
            matrix.postScale(s, s, cropRect.centerX(), cropRect.centerY());
            r.set(0, 0, src.getWidth(), src.getHeight());
            matrix.mapRect(r);
        }
        float dx = 0, dy = 0;
        if (r.left > cropRect.left) dx = cropRect.left - r.left;
        else if (r.right < cropRect.right) dx = cropRect.right - r.right;
        if (r.top > cropRect.top) dy = cropRect.top - r.top;
        else if (r.bottom < cropRect.bottom) dy = cropRect.bottom - r.bottom;
        if (dx != 0 || dy != 0) matrix.postTranslate(dx, dy);
    }

    /** 按当前矩阵与裁剪框裁切图片，返回裁剪结果（失败返回 null） */
    public Bitmap doCrop() {
        if (src == null || !matrix.invert(inv)) return null;
        float[] pts = {cropRect.left, cropRect.top, cropRect.right, cropRect.bottom};
        inv.mapPoints(pts);
        float x = Math.max(0, Math.min(pts[0], pts[2]));
        float y = Math.max(0, Math.min(pts[1], pts[3]));
        float w = Math.abs(pts[2] - pts[0]);
        float h = Math.abs(pts[3] - pts[1]);
        x = Math.min(x, src.getWidth() - 1f);
        y = Math.min(y, src.getHeight() - 1f);
        w = Math.min(w, src.getWidth() - x);
        h = Math.min(h, src.getHeight() - y);
        if (w < 1 || h < 1) return null;
        try {
            return Bitmap.createBitmap(src, Math.round(x), Math.round(y),
                    Math.round(w), Math.round(h));
        } catch (Throwable t) {
            return null;
        }
    }
}
