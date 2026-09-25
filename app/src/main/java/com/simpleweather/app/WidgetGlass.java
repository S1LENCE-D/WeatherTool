package com.simpleweather.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v10.1：桌面小组件的毛玻璃背景。
 *
 * <p><b>为什么不是"把身后的壁纸模糊"</b>：小组件由 launcher 进程渲染，RemoteViews
 * 既拿不到自己所在的屏幕坐标，也没有任何模糊 API，无法采样它背后真正的内容。
 * 因此这里的做法是：由 App 自己生成一张「模糊底图」交给小组件铺底 —— 观感上
 * 就是一块磨砂玻璃，且深浅主题、晴天雨天各有呼应。
 *
 * <p>底子来源：① 用户在 App 里设置的自定义壁纸（若有）；② 否则按天气码/时段
 * 取主题渐变色。随后 缩到极小 → 三次 box blur（中心极限定理，近似高斯）→
 * 放大铺满 → 圆角裁切 → 蒙版压暗/提亮 + 顶部柔光。
 *
 * <p>两点工程约束：
 * <ul>
 *   <li>位图规模压到 {@link #MAX_PX} 以内：RemoteViews 里的 Bitmap 要走 binder，
 *       太大直接 TransactionTooLargeException；反正内容是模糊的，放大无损观感。</li>
 *   <li>边线、高光这类"锐利"元素不画进位图（放大会糊），交给
 *       {@code w_glass_edge_*} 形状 drawable 以全分辨率压在顶层。</li>
 * </ul>
 *
 * <p><b>透明度</b>：整块玻璃按 {@link #plateAlpha} 铺到透明画布上，桌面从下方透出来 ——
 * 要的是"半透明磨砂玻璃"，不是一块盖住桌面的实色板。蒙版（压暗/提亮）画在玻璃
 * <i>内部</i>，只影响玻璃自身的深浅、不额外增加不透明度，所以"提高对比度"和
 * "保持通透"并不冲突。深色玻璃压在很亮的壁纸上是最吃亏的组合，故深色比浅色略加密。
 */
public final class WidgetGlass {

    /** 位图最长边上限（px）。160×160×4B ≈ 100KB，远低于 binder 上限 */
    private static final int MAX_PX = 160;
    /** 模糊底图相对成品的长边比例（先缩到这么小再糊，等价于大半径高斯） */
    private static final int SRC_PX = 48;
    /** 圆角半径（dp），与 w_glass_edge_* 保持一致 */
    private static final float CORNER_DP = 18f;
    /** 底子平均亮度高于此值 → 认为底子偏亮，改用浅色玻璃 + 深色文字 */
    private static final float LIGHT_FRAME_LUMA = 0.55f;

    /** 渲染结果：位图 + 该用深字还是浅字 */
    public static final class Result {
        public final Bitmap bitmap;
        public final boolean lightFrame;
        public final float meanLuma;

        Result(Bitmap b, boolean light, float luma) {
            bitmap = b;
            lightFrame = light;
            meanLuma = luma;
        }
    }

    private static final LinkedHashMap<String, Result> CACHE =
            new LinkedHashMap<String, Result>(4, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Result> e) {
                    return size() > 6;
                }
            };

    private WidgetGlass() { }

    /**
     * 取（或生成）本次渲染用的毛玻璃底图。
     *
     * @param wDp  小组件当前宽度(dp)
     * @param hDp  小组件当前高度(dp)
     * @param code WMO 天气码（决定渐变底色）
     * @param day  是否白天
     * @param dark 是否深色主题
     * @return 永不为 null；生成失败时返回 null 由调用方回退静态 drawable
     */
    public static Result get(Context ctx, int wDp, int hDp, int code, boolean day,
                             boolean dark) {
        if (wDp <= 0 || hDp <= 0) return null;
        String wall = Theme.m3BgPath(ctx);
        long wallStamp = 0L;
        if (wall != null) {
            File f = new File(wall);
            wallStamp = f.exists() ? f.lastModified() : -1L;
        }
        int hour = Calendar_hour();
        String key = wDp + "x" + hDp + "|" + code + "|" + day + "|" + dark + "|"
                + wallStamp + "|" + hour / 3;
        synchronized (CACHE) {
            Result hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        Result r = build(ctx, wDp, hDp, code, day, dark, wall);
        if (r != null) {
            synchronized (CACHE) {
                CACHE.put(key, r);
            }
        }
        return r;
    }

    private static int Calendar_hour() {
        // 用系统时间的小时数即可（不引用 Calendar 之外的时区逻辑）
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
    }

    private static Result build(Context ctx, int wDp, int hDp, int code, boolean day,
                                boolean dark, String wallPath) {
        try {
            // 成品尺寸：保持宽高比，长边不超过 MAX_PX
            float ar = (float) hDp / (float) wDp;
            int bw, bh;
            if (wDp >= hDp) {
                bw = MAX_PX;
                bh = Math.max(8, Math.round(MAX_PX * ar));
            } else {
                bh = MAX_PX;
                bw = Math.max(8, Math.round(MAX_PX / ar));
            }

            // 底子小图（缩到极小，之后的模糊都是在这里做的，代价极低）
            float sar = (float) bh / (float) bw;
            int sw, sh;
            if (bw >= bh) {
                sw = SRC_PX;
                sh = Math.max(6, Math.round(SRC_PX * sar));
            } else {
                sh = SRC_PX;
                sw = Math.max(6, Math.round(SRC_PX / sar));
            }
            int[] src = createSource(ctx, sw, sh, code, day, dark, wallPath);
            if (src == null) return null;

            // 三次 box blur ≈ 高斯（半径随尺寸略增，模糊量感一致）
            int radius = Math.max(2, Math.min(6, Math.round(Math.min(sw, sh) / 9f)));
            int[] blurred = src.clone();
            for (int i = 0; i < 3; i++) {
                boxBlurH(blurred, sw, sh, radius);
                boxBlurV(blurred, sw, sh, radius);
            }

            // 平均亮度 → 决定玻璃朝向（亮底配深字）
            long lum = 0;
            for (int p : blurred) {
                int a = (p >>> 24) & 0xFF;
                int r = (p >> 16) & 0xFF, g = (p >> 8) & 0xFF, b = p & 0xFF;
                lum += (r * 54 + g * 183 + b * 19) >> 8;   // ≈ 0.21R+0.72G+0.07B
                if (a == 0) lum += 128;                    // 透明像素按中性灰计
            }
            float mean = (float) lum / (float) (blurred.length * 255);
            boolean lightFrame = mean > LIGHT_FRAME_LUMA;

            Bitmap smallBlur = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            smallBlur.setPixels(blurred, 0, sw, 0, 0, sw, sh);

            // ① 先在不透明画布上合成「玻璃内容」：模糊底 + 轻度蒙版 + 柔光 + 暗角。
            //    蒙版只负责保证文字对比，不再压成实色——玻璃的透明感由下一步统一给。
            Bitmap content = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas cc = new Canvas(content);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
            p.setShader(new BitmapShader(smallBlur, Shader.TileMode.CLAMP,
                    Shader.TileMode.CLAMP));
            cc.drawRect(0, 0, bw, bh, p);
            p.setShader(null);

            int scrimTop = lightFrame ? 0x73FFFFFF : 0x66000000;
            int scrimBottom = lightFrame ? 0xA6FFFFFF : 0xA6000000;
            p.setShader(new LinearGradient(0, 0, 0, bh, scrimTop, scrimBottom,
                    Shader.TileMode.CLAMP));
            cc.drawRect(0, 0, bw, bh, p);

            // 顶部柔光：玻璃上缘反光（宽频变化，放大后依然柔和）
            p.setShader(new LinearGradient(0, 0, 0, bh * 0.5f,
                    lightFrame ? 0x40FFFFFF : 0x2EFFFFFF, 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            cc.drawRect(0, 0, bw, bh, p);
            p.setShader(null);

            // 底部暗角：玻璃厚度感
            p.setShader(new LinearGradient(0, bh * 0.55f, 0, bh,
                    0x00000000, lightFrame ? 0x14000000 : 0x1F000000,
                    Shader.TileMode.CLAMP));
            cc.drawRect(0, 0, bw, bh, p);
            p.setShader(null);

            // ② 整块铺到透明画布上，alpha 决定桌面能透出多少 —— 要的就是"半透明磨砂玻璃"，
            //    而不是一块盖住桌面的实色板。
            int plate = plateAlpha(ctx);
            Bitmap out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            float corner = CORNER_DP * (float) bw / (float) wDp;
            Path clip = new Path();
            clip.addRoundRect(new RectF(0, 0, bw, bh), corner, corner, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            p.setAlpha(plate);
            c.drawBitmap(content, 0, 0, p);
            p.setAlpha(255);
            c.restore();
            content.recycle();
            smallBlur.recycle();

            return new Result(out, lightFrame, mean);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 玻璃透明度：以应用内毛玻璃档位为基准，深色玻璃在亮壁纸上更吃亏，略加密一档 */
    private static int plateAlpha(Context ctx) {
        int base = Theme.glassAlpha(ctx);           // 深色 155 / 浅色 180
        return Math.min(255, base + (Theme.isDark(ctx) ? 20 : 0));
    }

    /** 底子：优先自定义壁纸，其次按天气/时段生成渐变；返回 ARGB 数组 */
    private static int[] createSource(Context ctx, int sw, int sh, int code, boolean day,
                                      boolean dark, String wallPath) {
        Bitmap wall = decodeWallpaper(wallPath, sw, sh);
        if (wall != null) {
            int[] px = new int[sw * sh];
            Bitmap scaled = Bitmap.createScaledBitmap(wall, sw, sh, true);
            scaled.getPixels(px, 0, sw, 0, 0, sw, sh);
            if (scaled != wall) scaled.recycle();
            wall.recycle();
            return px;
        }
        int[] pal = Theme.paletteHour(ctx, code,
                java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY));
        if (pal == null || pal.length < 2) pal = Theme.palette(ctx, code, day);
        if (pal == null || pal.length < 2) return null;
        // paletteHour 给的是 顶/中/底 三档，按档位插值（与 App 内背景同源）
        int n = pal.length;
        int[] px = new int[sw * sh];
        for (int y = 0; y < sh; y++) {
            float t = sh <= 1 ? 0f : (float) y / (sh - 1);
            for (int x = 0; x < sw; x++) {
                // 斜向渐变，再叠一点横向变化避免死板
                float tx = sw <= 1 ? 0f : (float) x / (sw - 1);
                float m = clamp01(t * 0.75f + tx * 0.25f);
                float seg = m * (n - 1);
                int i0 = (int) seg;
                if (i0 > n - 2) i0 = n - 2;
                if (i0 < 0) i0 = 0;
                px[y * sw + x] = mix(pal[i0], pal[i0 + 1], seg - i0);
            }
        }
        return px;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** 读自定义壁纸并按目标比例居中裁切（失败返回 null，回退渐变底） */
    private static Bitmap decodeWallpaper(String path, int sw, int sh) {
        if (path == null) return null;
        try {
            File f = new File(path);
            if (!f.exists() || f.length() == 0) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= sw * 2
                    && o.outHeight / (sample * 2) >= sh * 2) {
                sample *= 2;                       // 采样到略大于目标的规模即可
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(path, o2);
            if (bmp == null) return null;
            // 居中裁切到目标宽高比
            float want = (float) sw / (float) sh;
            float have = (float) bmp.getWidth() / (float) bmp.getHeight();
            int cw = bmp.getWidth(), ch = bmp.getHeight();
            if (have > want) cw = Math.max(1, Math.round(ch * want));
            else ch = Math.max(1, Math.round(cw / want));
            int x = (bmp.getWidth() - cw) / 2, y = (bmp.getHeight() - ch) / 2;
            return Bitmap.createBitmap(bmp, x, y, cw, ch);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int mix(int c0, int c1, float t) {
        int a = (int) (((c0 >>> 24) & 0xFF) + (((c1 >>> 24) & 0xFF) - ((c0 >>> 24) & 0xFF)) * t);
        int r = (int) (((c0 >> 16) & 0xFF) + (((c1 >> 16) & 0xFF) - ((c0 >> 16) & 0xFF)) * t);
        int g = (int) (((c0 >> 8) & 0xFF) + (((c1 >> 8) & 0xFF) - ((c0 >> 8) & 0xFF)) * t);
        int b = (int) ((c0 & 0xFF) + ((c1 & 0xFF) - (c0 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ---------- box blur（滑动窗口，O(n)，三次近似高斯）----------

    private static void boxBlurH(int[] px, int w, int h, int r) {
        int[] line = new int[w];
        for (int y = 0; y < h; y++) {
            int base = y * w;
            System.arraycopy(px, base, line, 0, w);
            int sumA = 0, sumR = 0, sumG = 0, sumB = 0;
            for (int i = -r; i <= r; i++) {
                int p = line[Math.max(0, Math.min(w - 1, i))];
                sumA += (p >>> 24) & 0xFF; sumR += (p >> 16) & 0xFF;
                sumG += (p >> 8) & 0xFF;   sumB += p & 0xFF;
            }
            int div = 2 * r + 1;
            for (int x = 0; x < w; x++) {
                px[base + x] = ((sumA / div) << 24) | ((sumR / div) << 16)
                        | ((sumG / div) << 8) | (sumB / div);
                int outP = line[Math.max(0, Math.min(w - 1, x - r))];
                int inP = line[Math.max(0, Math.min(w - 1, x + r + 1))];
                sumA += ((inP >>> 24) & 0xFF) - ((outP >>> 24) & 0xFF);
                sumR += ((inP >> 16) & 0xFF) - ((outP >> 16) & 0xFF);
                sumG += ((inP >> 8) & 0xFF) - ((outP >> 8) & 0xFF);
                sumB += (inP & 0xFF) - (outP & 0xFF);
            }
        }
    }

    private static void boxBlurV(int[] px, int w, int h, int r) {
        int[] col = new int[h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) col[y] = px[y * w + x];
            int sumA = 0, sumR = 0, sumG = 0, sumB = 0;
            for (int i = -r; i <= r; i++) {
                int p = col[Math.max(0, Math.min(h - 1, i))];
                sumA += (p >>> 24) & 0xFF; sumR += (p >> 16) & 0xFF;
                sumG += (p >> 8) & 0xFF;   sumB += p & 0xFF;
            }
            int div = 2 * r + 1;
            for (int y = 0; y < h; y++) {
                px[y * w + x] = ((sumA / div) << 24) | ((sumR / div) << 16)
                        | ((sumG / div) << 8) | (sumB / div);
                int outP = col[Math.max(0, Math.min(h - 1, y - r))];
                int inP = col[Math.max(0, Math.min(h - 1, y + r + 1))];
                sumA += ((inP >>> 24) & 0xFF) - ((outP >>> 24) & 0xFF);
                sumR += ((inP >> 16) & 0xFF) - ((outP >> 16) & 0xFF);
                sumG += ((inP >> 8) & 0xFF) - ((outP >> 8) & 0xFF);
                sumB += (inP & 0xFF) - (outP & 0xFF);
            }
        }
    }
}
