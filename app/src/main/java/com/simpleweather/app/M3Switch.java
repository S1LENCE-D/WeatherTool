package com.simpleweather.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * v9.75：自绘 Material 3 Switch（无第三方依赖，Android 7+ 统一 M3 外观）。
 * 视觉尺寸：track 52x32dp（M3 新规范），thumb 20dp，点击区 48x32dp。
 * 配色：checked → track 主色 / thumb onPrimary；
 *       unchecked → track surfaceVariant / thumb surfaceContainerHighest。
 * 深/浅主题自动适配，切换带 160ms 位移动画。
 */
public class M3Switch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(M3Switch sw, boolean checked);
    }

    private boolean checked = false;
    private float progress = 0f;   // 0 = off, 1 = on
    private ValueAnimator anim;
    private OnCheckedChangeListener listener;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    public M3Switch(Context c) { this(c, null); }

    public M3Switch(Context c, AttributeSet a) { this(c, a, 0); }

    public M3Switch(Context c, AttributeSet a, int style) {
        super(c, a, style);
        dp = c.getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) { toggle(); }
        });
    }

    public boolean isChecked() { return checked; }

    public void setChecked(boolean c) {
        if (c == checked) {
            setProgress(c ? 1f : 0f);
            return;
        }
        checked = c;
        animateTo(c ? 1f : 0f);
        if (listener != null) listener.onCheckedChanged(this, checked);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) { listener = l; }

    private void toggle() { setChecked(!checked); }

    private void setProgress(float p) {
        progress = p;
        invalidate();
    }

    private void animateTo(float target) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(progress, target);
        anim.setDuration(160);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                setProgress((Float) va.getAnimatedValue());
            }
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int w, int h) {
        // v9.77：点击区加宽到与 track 同宽（52dp），避免左右绘制被 View 边界裁剪
        setMeasuredDimension((int) (52 * dp), (int) (32 * dp));
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        boolean dark = Theme.isDark(getContext());
        int trackOff = dark ? 0xFF49454F : 0xFFCAC4D0;   // surfaceVariant
        int trackOn = Theme.accent(getContext());        // primary
        int thumbOff = dark ? 0xFF2B2D33 : 0xFFFDFCFF;   // surfaceContainerHighest
        int thumbOn = dark ? 0xFF1B1B1F : 0xFFFFFFFF;    // onPrimary

        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float trackW = 52 * dp, trackH = 32 * dp;
        float left = cx - trackW / 2f, top = cy - trackH / 2f;
        RectF rect = new RectF(left, top, left + trackW, top + trackH);

        trackPaint.setColor(lerp(trackOff, trackOn, progress));
        cv.drawRoundRect(rect, trackH / 2f, trackH / 2f, trackPaint);

        // 位移区间 = track 宽 - thumb 直径 - 两侧边距(2dp)，thumb 圆心随 progress 移动
        float travel = trackW - 20 * dp - 4 * dp;
        float thumbX = left + 2 * dp + 10 * dp + travel * progress;

        thumbShadow.setColor(lerp(0x33000000, 0x1F000000, progress));
        cv.drawCircle(thumbX, cy, 10.5f * dp, thumbShadow);
        thumbPaint.setColor(lerp(thumbOff, thumbOn, progress));
        cv.drawCircle(thumbX, cy, 9.2f * dp, thumbPaint);
    }

    private static int lerp(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        return Color.rgb(Math.round(ar + (br - ar) * t),
                Math.round(ag + (bg - ag) * t),
                Math.round(ab + (bb - ab) * t));
    }
}
