package com.simpleweather.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * v10.1：底部悬浮栏的「液态玻璃」背景。
 *
 * <p>本仓库不使用任何第三方依赖，故直接复用已有的 {@link GlassDrawable}
 * （背景模糊快照按窗口坐标裁切 + 顶部柔光 + 细描边），在其上补两件事，
 * 让玻璃"浮"起来、有厚度：
 * <ol>
 *   <li>{@link #getOutline}：给出圆角轮廓，View 的 elevation 才能投出圆角阴影，
 *       悬浮栏才有离开内容层的层次感（纯色面板由 GradientDrawable 自带轮廓）；</li>
 *   <li>{@code draw} 末尾叠一道底部内阴影 —— 玻璃下缘的厚度暗示，
 *       配合父类顶部的柔光高光，形成「上亮下暗」的玻片感。</li>
 * </ol>
 *
 * <p>参考做法（GitHub 上面向 View 体系的液态玻璃组件，如 QWEA0/Liquid-Glass-Android，
 * 以及 KernelSU 管理器一类的玻璃底栏）共性都是「背景模糊 + 边缘高光/折射 + 圆角浮层」，
 * 差别只在是否做实时背景采样与折射；本应用的前景内容始终盖在自绘背景之上，
 * 用背景快照即可得到等价观感，且省电。
 */
public class LiquidBarDrawable extends GlassDrawable {

    private final float radius;
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF tmp = new RectF();
    private Shader shadeShader;

    public LiquidBarDrawable(Bitmap fullBmp, int windowX, int windowY, float cornerPx,
                             int scaleFactor, int highlightColor, int borderColor) {
        super(fullBmp, windowX, windowY, cornerPx, scaleFactor, highlightColor, borderColor);
        radius = cornerPx;
    }

    /** 圆角轮廓：有了它，setElevation 才能投出圆角阴影 */
    @Override
    public void getOutline(Outline outline) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        outline.setRoundRect(b, radius);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        int w = bounds.width(), h = bounds.height();
        if (w > 0 && h > 0) {
            // 底部内阴影：从下缘往上约 38% 高度渐隐
            float top = bounds.top + h * 0.62f;
            shadeShader = new LinearGradient(0, top, 0, bounds.bottom,
                    0x00000000, 0x1A000000, Shader.TileMode.CLAMP);
        }
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);   // 模糊底 + 顶部柔光 + 细描边
        Rect b = getBounds();
        if (b.isEmpty() || shadeShader == null) return;
        c.save();
        tmp.set(b);
        clip.reset();
        clip.addRoundRect(tmp, radius, radius, Path.Direction.CW);
        c.clipPath(clip);
        shade.setShader(shadeShader);
        c.drawRect(b, shade);
        shade.setShader(null);
        c.restore();
    }
}
