package com.simpleweather.app;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/**
 * 可拖动、双指缩放的地图视图基类。
 * 世界坐标 = 瓦片坐标（每瓦片 256px，层级 tileZoom）。
 * 锚点 (centerLat, centerLng) 始终位于视图中心；panX/panY 为世界坐标偏移。
 */
public abstract class PanZoomView extends View {

    protected int tileZoom = 3;          // 瓦片层级（世界尺寸 = 256 << tileZoom）
    protected float scale = 1.5f;        // 额外缩放倍率
    protected float panX = 0f, panY = 0f;
    protected double centerLat = 35.0, centerLng = 105.0;

    private final ScaleGestureDetector sgd;
    private float lastX, lastY;

    public PanZoomView(Context c) {
        this(c, null);
    }

    public PanZoomView(Context c, AttributeSet a) {
        super(c, a);
        sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float fx = d.getFocusX(), fy = d.getFocusY();
                float wx = (fx - getWidth() / 2f) / scale - panX + centerWorldX();
                float wy = (fy - getHeight() / 2f) / scale - panY + centerWorldY();
                float ns = Math.max(1f, Math.min(48f, scale * d.getScaleFactor()));
                if (ns == scale) return true;
                scale = ns;
                panX = (fx - getWidth() / 2f) / scale - wx + centerWorldX();
                panY = (fy - getHeight() / 2f) / scale - wy + centerWorldY();
                invalidate();
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        sgd.onTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = ev.getX();
                lastY = ev.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (!sgd.isInProgress()) {
                    panX += (ev.getX() - lastX) / scale;
                    panY += (ev.getY() - lastY) / scale;
                    lastX = ev.getX();
                    lastY = ev.getY();
                    invalidate();
                }
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(getWidth() / 2f, getHeight() / 2f);
        canvas.scale(scale, scale);
        canvas.translate(-centerWorldX() + panX, -centerWorldY() + panY);
        onDrawWorld(canvas);
        canvas.restore();
    }

    /** 子类在世界坐标系中绘制内容 */
    protected abstract void onDrawWorld(Canvas canvas);

    // ============ 坐标换算 ============

    public static float lngToWorldX(double lng, int zoom) {
        return (float) ((lng + 180.0) / 360.0 * (256 << zoom));
    }

    public static float latToWorldY(double lat, int zoom) {
        double s = Math.sin(Math.toRadians(lat));
        if (s > 0.9999) s = 0.9999;
        if (s < -0.9999) s = -0.9999;
        return (float) ((0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * (256 << zoom));
    }

    protected float centerWorldX() {
        return lngToWorldX(centerLng, tileZoom);
    }

    protected float centerWorldY() {
        return latToWorldY(centerLat, tileZoom);
    }

    public float worldSize() {
        return 256 << tileZoom;
    }

    public void setCenter(double lat, double lng) {
        centerLat = lat;
        centerLng = lng;
        invalidate();
    }

    // ============ 可见范围（世界坐标） ============

    protected float visibleWorldLeft() {
        return (0f - getWidth() / 2f) / scale - panX + centerWorldX();
    }

    protected float visibleWorldTop() {
        return (0f - getHeight() / 2f) / scale - panY + centerWorldY();
    }

    protected float visibleWorldRight() {
        return (getWidth() - getWidth() / 2f) / scale - panX + centerWorldX();
    }

    protected float visibleWorldBottom() {
        return (getHeight() - getHeight() / 2f) / scale - panY + centerWorldY();
    }
}
