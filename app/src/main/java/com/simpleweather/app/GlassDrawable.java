package com.simpleweather.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * 手搓毛玻璃背景 Drawable：
 * 持有「背景模糊快照」小图（全屏 1/N 尺寸），按视图在窗口中的坐标裁切对应区域，
 * 绘制时拉伸放大 —— 天然高斯模糊质感，内存占用小（1/N^2）。
 * 圆角裁剪 + 一层玻璃高光白；滚动时调用 setWindowPos 即可跟随，无需重建。
 */
public class GlassDrawable extends Drawable {

    private final Bitmap bmp;          // 全屏模糊快照（小尺寸）
    private final int scale;           // 快照相对真实坐标的缩放倍率（如 4）
    private final Rect src = new Rect();
    private final RectF tmp = new RectF();
    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader highlightShader;
    private final float corner;        // 圆角半径（px）
    private int winX = 0, winY = 0;

    public GlassDrawable(Bitmap fullBmp, int windowX, int windowY, float cornerPx,
                          int scaleFactor, int highlightColor, int borderColor) {
        bmp = fullBmp;
        scale = Math.max(scaleFactor, 1);
        winX = windowX;
        winY = windowY;
        corner = cornerPx;
        paint.setAlpha(190);            // 半透明：下层粒子动画可朦胧透出
        highlight.setColor(highlightColor); // 玻璃高光白（浅色主题下调淡）
        border.setStyle(Paint.Style.STROKE);
        border.setColor(borderColor);
        onBoundsChange(getBounds());
    }

    /** 滚动/布局后更新视图在窗口中的位置（快照裁切偏移） */
    public void setWindowPos(int x, int y) {
        if (winX != x || winY != y) {
            winX = x;
            winY = y;
            onBoundsChange(getBounds());
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        int l = winX / scale, t = winY / scale;
        int r = (winX + bounds.width()) / scale;
        int b = (winY + bounds.height()) / scale;
        l = Math.max(0, Math.min(l, bmp.getWidth() - 1));
        t = Math.max(0, Math.min(t, bmp.getHeight() - 1));
        r = Math.max(l + 1, Math.min(r, bmp.getWidth()));
        b = Math.max(t + 1, Math.min(b, bmp.getHeight()));
        src.set(l, t, r, b);
        // v9.87：液态玻璃——顶部柔和高光渐变（玻璃反光，随尺寸重建）
        int w = bounds.width(), h = bounds.height();
        if (w > 0 && h > 0) {
            float glowH = Math.min(corner * 3f, h * 0.55f);
            highlightShader = new LinearGradient(
                    0, bounds.top, 0, bounds.top + glowH,
                    0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty() || bmp == null) return;
        c.save();
        tmp.set(b);
        path.reset();
        path.addRoundRect(tmp, corner, corner, Path.Direction.CW);
        c.clipPath(path);
        c.drawBitmap(bmp, src, b, paint);
        highlight.setShader(highlightShader);
        c.drawRect(b, highlight);
        highlight.setShader(null);
        c.restore();
        // v9.87：极细圆角描边（向内偏移半个描边宽度，避免被圆角裁掉）
        float sw = Math.max(0.75f, corner / 20f);
        float inset = sw / 2f;
        border.setStrokeWidth(sw);
        c.drawRoundRect(b.left + inset, b.top + inset,
                b.right - inset, b.bottom - inset,
                corner - inset, corner - inset, border);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
