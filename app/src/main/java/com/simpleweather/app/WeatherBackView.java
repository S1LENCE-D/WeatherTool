package com.simpleweather.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 天气呼应动态背景（叠加在渐变底色之上）：
 *  晴       - 太阳光晕 + 漂浮光斑
 *  晴夜     - 星空闪烁 + 月亮
 *  多云/阴  - 云朵缓慢漂移
 *  雾       - 雾带流动
 *  雨       - 暗云 + 雨丝斜落
 *  雪       - 暗云 + 雪花飘落（左右摇摆）
 *  雷暴     - 暗云 + 雨丝 + 随机闪电
 * 60fps Handler 驱动，粒子预分配，无每帧对象创建。
 */
public class WeatherBackView extends View {

    private static final int MODE_SUN = 0, MODE_NIGHT = 1, MODE_CLOUDY = 2,
            MODE_OVERCAST = 3, MODE_FOG = 4, MODE_RAIN = 5, MODE_SNOW = 6, MODE_STORM = 7;

    private static final int MAX = 96;
    private int mode = MODE_SUN;
    private boolean day = true;
    private boolean lightPalette = false;   // 浅色主题：粒子改深蓝灰，保证可见

    public void setLightPalette(boolean light) { lightPalette = light; }

    /** 云/雪粒子基色（浅色主题深蓝灰，深色主题白） */
    private int cloud(int alpha) {
        int base = lightPalette ? 0x4A5A6A : 0xFFFFFF;
        return (alpha << 24) | base;
    }

    private int rain() {
        return lightPalette ? 0xB33A4A5A : 0x73FFFFFF;
    }

    private int snow() {
        return lightPalette ? 0xE64A5A6A : 0xCCFFFFFF;
    }

    private int moon() {
        return lightPalette ? 0xFF7C8FA6 : 0xFFF5F0E1;
    }

    private final Random rnd = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            invalidate();
            handler.postDelayed(this, 16);
        }
    };

    // 粒子：x, y, 速度, 尺寸, 相位（闪烁/摇摆）
    private final float[] px = new float[MAX], py = new float[MAX], pv = new float[MAX],
            pw = new float[MAX], pph = new float[MAX];
    private int pCount = 0;

    // 云朵（v9.37：容量 6->10，v9.36 雷暴云 8 朵曾越界崩溃）
    private final float[] cX = new float[10], cY = new float[10], cS = new float[10], cV = new float[10];
    private int cloudCount = 0;
    private int cloudColor = 0x59FFFFFF;   // v9.35：云朵基色（initClouds 写入，drawClouds 使用）
    // v9.38：云朵——连续贝塞尔轮廓（Material 云形）+ 径向渐变柔边，复用对象避免每帧分配
    private final Path cloudPath = new Path();
    private android.graphics.RadialGradient cloudRg = null;
    // v9.37.1：月亮月牙 Path（主圆 - 缺口圆）与月面渐变（initParticles 预构建）
    private final Path moonA = new Path(), moonB = new Path(), moonPath = new Path();
    private android.graphics.RadialGradient moonRg = null;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float w = 1080f, h = 1920f;
    private float flash = 0f;          // 闪电强度
    private long lastFlashMs = 0;      // v9.35：上次闪电时间（冷却用，避免闪烁过频）
    private float sunX, sunY, sunR;    // 太阳/月亮位置
    private long t0 = System.currentTimeMillis();
    // 预创建渐变（避免每帧 new 对象，降低 GC 与渲染风险）
    private android.graphics.RadialGradient sunRg = null, nightRg = null;
    private boolean stopped = false;   // 绘制异常后停止特效，避免连环崩溃

    public WeatherBackView(Context c) {
        this(c, null);
    }

    /** 布局 inflate 必需的 (Context, AttributeSet) 构造 */
    public WeatherBackView(Context c, AttributeSet attrs) {
        super(c, attrs);
        setClickable(false);
    }

    /** 按 WMO 天气码切换背景氛围 */
    public void setWeather(int code, boolean isDay) {
        day = isDay;
        int m;
        if (code <= 1) m = day ? MODE_SUN : MODE_NIGHT;
        else if (code == 2) m = MODE_CLOUDY;
        else if (code == 3) m = MODE_OVERCAST;
        else if (code >= 45 && code <= 48) m = MODE_FOG;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) m = MODE_SNOW;
        else if (code >= 95) m = MODE_STORM;
        else if (code >= 51 && code <= 82) m = MODE_RAIN;
        else m = day ? MODE_SUN : MODE_NIGHT;
        if (m == mode) return;
        mode = m;
        initParticles();
    }

    @Override
    protected void onSizeChanged(int nw, int nh, int ow, int oh) {
        super.onSizeChanged(nw, nh, ow, oh);
        w = Math.max(nw, 1); h = Math.max(nh, 1);
        initParticles();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    private void initParticles() {
        pCount = 0;
        cloudCount = 0;
        flash = 0f;
        sunX = w * 0.82f; sunY = h * 0.16f; sunR = Math.min(w, h) * 0.10f;
        // 预创建光晕渐变（按当前尺寸）
        try {
            sunRg = new android.graphics.RadialGradient(sunX, sunY, sunR * 6f,
                    new int[]{0x66FFD54F, 0x22FFD54F, 0x00FFD54F}, null,
                    android.graphics.Shader.TileMode.CLAMP);
            nightRg = new android.graphics.RadialGradient(sunX, sunY, sunR * 5f,
                    new int[]{0x44FFFFFF, 0x11FFFFFF, 0x00FFFFFF}, null,
                    android.graphics.Shader.TileMode.CLAMP);
            moonRg = new android.graphics.RadialGradient(sunX - sunR * 0.2f, sunY - sunR * 0.2f,
                    sunR * 1.2f, new int[]{0xFFFDF9EC, 0xFFE9DFC6}, null,
                    android.graphics.Shader.TileMode.CLAMP);
        } catch (Exception e) {
            sunRg = null; nightRg = null; moonRg = null;
        }
        // v9.37.1：预构建月牙 Path（主圆 - 左偏缺口圆 = 右下月牙），边缘为双圆弧
        moonA.reset();
        moonA.addCircle(sunX, sunY, sunR, Path.Direction.CW);
        moonB.reset();
        moonB.addCircle(sunX - sunR * 0.30f, sunY - sunR * 0.26f, sunR * 0.94f, Path.Direction.CW);
        moonPath.reset();
        moonPath.op(moonA, moonB, Path.Op.DIFFERENCE);
        buildCloudShape();
        switch (mode) {
            case MODE_SUN:      initSun(); break;
            case MODE_NIGHT:    initNight(); break;
            // v9.36：云的数量按天气类型差异化——多云少而亮，阴/雷暴多而暗
            case MODE_CLOUDY:   initClouds(3, cloud(0x99)); break;
            case MODE_OVERCAST: initClouds(7, cloud(0x66)); break;
            case MODE_FOG:      initFog(); break;
            case MODE_RAIN:     initClouds(5, cloud(0x55)); initRain(); break;
            case MODE_SNOW:     initClouds(4, cloud(0x55)); initSnow(); break;
            case MODE_STORM:    initClouds(8, cloud(0x40)); initRain(); break;
        }
    }

    private void initSun() {
        // 3 个漂浮光斑（缓慢浮动的柔和光晕）
        pCount = 3;
        for (int i = 0; i < pCount; i++) {
            px[i] = w * (0.12f + 0.3f * i);
            py[i] = h * (0.35f + 0.25f * i);
            pv[i] = 10f + rnd.nextFloat() * 8f;
            pw[i] = Math.min(w, h) * (0.10f + 0.05f * i);
            pph[i] = rnd.nextFloat() * 6.28f;
        }
    }

    private void initNight() {
        pCount = 46;
        for (int i = 0; i < pCount; i++) {
            px[i] = rnd.nextFloat() * w;
            py[i] = rnd.nextFloat() * h * 0.8f;
            pw[i] = 1f + rnd.nextFloat() * 2.2f;
            pph[i] = rnd.nextFloat() * 6.28f;
            pv[i] = 0.6f + rnd.nextFloat() * 1.4f;   // 闪烁速度
        }
    }

    private void initClouds(int n, int alphaColor) {
        if (n > 10) n = 10;   // v9.37：防御性截断，防止越界
        cloudCount = n;
        cloudColor = alphaColor;
        for (int i = 0; i < n; i++) {
            cX[i] = rnd.nextFloat() * w;
            // v9.36：云带下移收窄——与主页温度大字同处一横列（约屏高 20%~30%）
            cY[i] = h * (0.20f + 0.10f * rnd.nextFloat());
            cS[i] = 0.6f + rnd.nextFloat() * 0.9f;
            cV[i] = 6f + rnd.nextFloat() * 10f;
            cS[i] *= (w / 1080f);
        }
        // v9.38：云主体渐变（中心实、边缘柔化无硬边）——随 cloudColor 重建一次
        try {
            int a = (cloudColor >>> 24) & 0xFF;
            int rgb = cloudColor & 0x00FFFFFF;
            int mid = ((int) (a * 0.45f) << 24) | rgb;
            cloudRg = new android.graphics.RadialGradient(0f, -12f, 100f,
                    new int[]{cloudColor, mid, rgb}, new float[]{0f, 0.65f, 1f},
                    android.graphics.Shader.TileMode.CLAMP);
        } catch (Exception e) {
            cloudRg = null;
        }
    }

    private void initFog() {
        pCount = 4;
        for (int i = 0; i < pCount; i++) {
            px[i] = rnd.nextFloat() * w;
            py[i] = h * (0.15f + 0.25f * i);
            pv[i] = 8f + rnd.nextFloat() * 12f;
            pw[i] = w * (0.5f + 0.4f * rnd.nextFloat());
            pph[i] = 0.05f + 0.05f * rnd.nextFloat();
        }
    }

    private void initRain() {
        int n = 52;
        for (int i = pCount; i < pCount + n && i < MAX; i++) {
            px[i] = rnd.nextFloat() * (w + 120f) - 60f;
            py[i] = rnd.nextFloat() * h;
            pv[i] = 700f + rnd.nextFloat() * 420f;   // 下落速度 px/s
            pw[i] = 14f + rnd.nextFloat() * 18f;     // 雨丝长度
            pph[i] = rnd.nextFloat() * 6.28f;
        }
        pCount = Math.min(pCount + n, MAX);
    }

    private void initSnow() {
        int n = 46;
        for (int i = pCount; i < pCount + n && i < MAX; i++) {
            px[i] = rnd.nextFloat() * w;
            py[i] = rnd.nextFloat() * h;
            pv[i] = 40f + rnd.nextFloat() * 55f;
            pw[i] = 2.2f + rnd.nextFloat() * 3.0f;
            pph[i] = rnd.nextFloat() * 6.28f;
        }
        pCount = Math.min(pCount + n, MAX);
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        if (stopped) return;
        try {
            drawFrame(cv);
        } catch (Throwable t) {
            // 渲染异常：停止特效动画（保留背景渐变），避免连环崩溃
            stopped = true;
            handler.removeCallbacks(ticker);
        }
    }

    private void drawFrame(Canvas cv) {
        long now = System.currentTimeMillis();
        float dt = Math.min((now - t0) / 1000f, 0.1f);  // 帧间隔（秒），上限 100ms
        t0 = now;
        float t = now / 1000f;

        switch (mode) {
            case MODE_SUN:      drawSun(cv, t); break;
            case MODE_NIGHT:    drawNight(cv, dt, t); break;
            case MODE_CLOUDY:
            case MODE_OVERCAST: drawClouds(cv, dt); break;
            case MODE_FOG:      drawFog(cv, dt); break;
            case MODE_RAIN:     drawClouds(cv, dt); drawRain(cv, dt); break;
            case MODE_SNOW:     drawClouds(cv, dt); drawSnow(cv, dt); break;
            case MODE_STORM:    drawClouds(cv, dt); drawRain(cv, dt); drawLightning(cv, dt); break;
        }
    }

    // ---------- 晴：太阳光晕 + 光斑 ----------
    private void drawSun(Canvas cv, float t) {
        // 主光晕（预创建渐变）
        if (sunRg != null) {
            p.setShader(sunRg);
            cv.drawCircle(sunX, sunY, sunR * 6f, p);
            p.setShader(null);
        }
        // 太阳本体
        p.setColor(0xFFFFE082);
        cv.drawCircle(sunX, sunY, sunR, p);
        p.setColor(0xFFFFF9C4);
        cv.drawCircle(sunX, sunY, sunR * 0.78f, p);
        // 放射光线（8 条，缓慢摆动）
        p.setColor(0x2EFFE082);
        p.setStrokeWidth(sunR * 0.16f);
        p.setStrokeCap(Paint.Cap.ROUND);
        cv.save();
        cv.translate(sunX, sunY);
        cv.rotate((float) Math.sin(t * 0.25f) * 6f);
        for (int i = 0; i < 8; i++) {
            cv.drawLine(0, -sunR * 1.8f, 0, -sunR * 2.7f, p);
            cv.rotate(45f);
        }
        cv.restore();
        p.setStrokeCap(Paint.Cap.BUTT);
        // 漂浮光斑
        p.setColor(0x30FFF59D);
        for (int i = 0; i < pCount; i++) {
            float y = py[i] + (float) Math.sin(t * 0.5f + pph[i]) * 18f;
            cv.drawCircle(px[i], y, pw[i], p);
        }
    }

    // ---------- 夜：星星 + 月亮 ----------
    private void drawNight(Canvas cv, float dt, float t) {
        // 月亮光晕（预创建渐变）
        if (nightRg != null) {
            p.setShader(nightRg);
            cv.drawCircle(sunX, sunY, sunR * 5f, p);
            p.setShader(null);
        }
        // v9.37.1：月牙——先整 Path 填底色（抗锯齿边缘），再裁剪画月面细节
        p.setColor(moon());
        cv.drawPath(moonPath, p);
        cv.save();
        try {
            cv.clipPath(moonPath);
        } catch (Exception e) {
            cv.restore();
            drawStars(cv, t);
            return;
        }
        // 月面渐变（左上受光、右下偏暗的柔和过渡）
        if (moonRg != null) {
            p.setShader(moonRg);
            cv.drawCircle(sunX, sunY, sunR * 1.02f, p);
            p.setShader(null);
        } else {
            p.setColor(moon());
            cv.drawCircle(sunX, sunY, sunR * 1.02f, p);
        }
        // 环形山（暗色小圆，超出月牙部分被裁剪）
        p.setColor(0x33857A66);
        cv.drawCircle(sunX + sunR * 0.28f, sunY + sunR * 0.26f, sunR * 0.13f, p);
        cv.drawCircle(sunX + sunR * 0.44f, sunY - sunR * 0.02f, sunR * 0.09f, p);
        cv.drawCircle(sunX + sunR * 0.12f, sunY + sunR * 0.52f, sunR * 0.07f, p);
        cv.drawCircle(sunX + sunR * 0.55f, sunY + sunR * 0.30f, sunR * 0.06f, p);
        // 亮坑（受光面反光）
        p.setColor(0x2EFFFFFF);
        cv.drawCircle(sunX + sunR * 0.34f, sunY + sunR * 0.12f, sunR * 0.05f, p);
        cv.drawCircle(sunX + sunR * 0.20f, sunY + sunR * 0.38f, sunR * 0.04f, p);
        cv.restore();
        // 星星闪烁
        drawStars(cv, t);
    }

    /** 星星闪烁（独立方法，供夜模式调用） */
    private void drawStars(Canvas cv, float t) {
        for (int i = 0; i < pCount; i++) {
            float a = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(t * pv[i] + pph[i]));
            p.setColor(Color.argb((int) (a * 255), 255, 255, 255));
            cv.drawCircle(px[i], py[i], pw[i], p);
        }
    }

    // ---------- 云朵漂移 ----------
    private void drawClouds(Canvas cv, float dt) {
        for (int i = 0; i < cloudCount; i++) {
            cX[i] += cV[i] * dt;
            if (cX[i] > w + 170f * cS[i]) cX[i] = -170f * cS[i];
            float x = cX[i], y = cY[i], s = cS[i];
            // v9.38：连续贝塞尔轮廓（Material 云形）+ 径向渐变柔边——无圆拼接、无硬边。
            // 渐变与轮廓均为相对坐标，经 canvas 变换定位到每朵云，全程零对象创建。
            cv.save();
            cv.translate(x, y);
            cv.scale(s, s);
            if (cloudRg != null) {
                p.setShader(cloudRg);
                cv.drawPath(cloudPath, p);
                p.setShader(null);
            } else {
                p.setColor(cloudColor);
                cv.drawPath(cloudPath, p);
            }
            cv.restore();
        }
    }

    /** 云轮廓：标准 Material "cloud"（24x24 网格）放大并居中，x 向拉宽 1.2 倍 */
    private void buildCloudShape() {
        cloudPath.rewind();
        cloudPath.moveTo(cx(19.35f), cy(10.04f));
        cloudPath.cubicTo(cx(18.67f), cy(6.59f), cx(15.64f), cy(4f), cx(12f), cy(4f));
        cloudPath.cubicTo(cx(9.11f), cy(4f), cx(6.6f), cy(5.64f), cx(5.35f), cy(8.04f));
        cloudPath.cubicTo(cx(2.34f), cy(8.36f), cx(0f), cy(10.91f), cx(0f), cy(14f));
        cloudPath.cubicTo(cx(0f), cy(17.31f), cx(2.69f), cy(20f), cx(6f), cy(20f));
        cloudPath.lineTo(cx(19f), cy(20f));
        cloudPath.cubicTo(cx(21.76f), cy(20f), cx(24f), cy(17.76f), cx(24f), cy(15f));
        cloudPath.cubicTo(cx(24f), cy(12.36f), cx(21.95f), cy(10.22f), cx(19.35f), cy(10.04f));
        cloudPath.close();
    }

    /** 云轮廓横坐标：Material 24 网格 -> 相对云中心（5 倍缩放 + 1.2 横向拉宽） */
    private float cx(float v) { return v * 6f - 72f; }

    /** 云轮廓纵坐标：Material 24 网格 -> 相对云中心（5 倍缩放） */
    private float cy(float v) { return v * 5f - 60f; }



    // ---------- 雾带 ----------
    private void drawFog(Canvas cv, float dt) {
        for (int i = 0; i < pCount; i++) {
            px[i] += pv[i] * dt;
            if (px[i] > w + pw[i]) px[i] = -pw[i];
            p.setColor(0x2EFFFFFF);
            cv.drawOval(px[i] - pw[i] / 2, py[i], px[i] + pw[i] / 2, py[i] + h * 0.03f, p);
        }
    }

    // ---------- 雨丝 ----------
    private void drawRain(Canvas cv, float dt) {
        p.setColor(rain());
        p.setStrokeWidth(1.6f);
        for (int i = 0; i < pCount; i++) {
            py[i] += pv[i] * dt;
            px[i] += pv[i] * dt * 0.18f;   // 斜向
            if (py[i] > h + 30f) {
                py[i] = -30f;
                px[i] = rnd.nextFloat() * (w + 120f) - 60f;
            }
            cv.drawLine(px[i], py[i], px[i] - pw[i] * 0.35f, py[i] - pw[i], p);
        }
    }

    // ---------- 雪花 ----------
    private void drawSnow(Canvas cv, float dt) {
        p.setColor(snow());
        for (int i = 0; i < pCount; i++) {
            py[i] += pv[i] * dt;
            px[i] += (float) Math.sin(nowPhase(i) * 2.2f) * 26f * dt;
            if (py[i] > h + 10f) { py[i] = -10f; px[i] = rnd.nextFloat() * w; }
            cv.drawCircle(px[i], py[i], pw[i], p);
        }
    }

    private float nowPhase(int i) {
        return (System.currentTimeMillis() / 1000f) + pph[i];
    }

    // ---------- 闪电 ----------
    private void drawLightning(Canvas cv, float dt) {
        // v9.35：概率 0.012->0.004（平均 4 秒一次）+ 3.2 秒最小间隔，避免闪屏过频
        if (flash <= 0f) {
            long now = System.currentTimeMillis();
            if (now - lastFlashMs > 3200 && rnd.nextFloat() < 0.004f) {
                flash = 0.85f;
                lastFlashMs = now;
            }
        }
        if (flash > 0f) {
            flash *= 0.86f;
            p.setColor(Color.argb((int) (flash * 110f), 255, 245, 210));
            cv.drawRect(0, 0, w, h, p);
            if (flash < 0.03f) flash = 0f;
        }
    }
}
