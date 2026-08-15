package com.simpleweather.app;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * 字体工具：Google Sans（数字/英文）+ Material Icons（天气/图标字形，谷歌 Material 风格）。
 * 中文自动回退系统字体。
 */
public final class Fonts {
    private static Typeface regular, medium, icons;
    private static boolean loaded = false;

    public static void load(Context ctx) {
        if (loaded) return;
        try {
            regular = Typeface.createFromAsset(ctx.getAssets(), "fonts/GoogleSans-Regular.ttf");
            medium = Typeface.createFromAsset(ctx.getAssets(), "fonts/GoogleSans-Medium.ttf");
        } catch (Exception e) {
            regular = Typeface.DEFAULT;
            medium = Typeface.DEFAULT_BOLD;
        }
        try {
            icons = Typeface.createFromAsset(ctx.getAssets(), "fonts/MaterialIcons-Regular.ttf");
        } catch (Exception e) {
            icons = Typeface.DEFAULT;
        }
        loaded = true;
    }

    public static Typeface regular() {
        return regular != null ? regular : Typeface.DEFAULT;
    }

    public static Typeface medium() {
        return medium != null ? medium : Typeface.DEFAULT_BOLD;
    }

    /** Material Icons 字形字体（PUA 码点） */
    public static Typeface icons() {
        return icons != null ? icons : Typeface.DEFAULT;
    }

    /** 递归应用字体：粗体文本走 Medium，其余走 Regular。
     *  v9.57：跳过已设为 Material Icons 字体的控件——图标字形在 PUA 码位，
     *  若被覆盖成 Google Sans 会渲染成方块（更多信息图标曾因此异常）。 */
    public static void apply(View root) {
        if (root == null) return;
        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            if (tv.getTypeface() == icons) return;   // 图标字形控件不覆盖
            boolean bold = tv.getTypeface() != null && tv.getTypeface().isBold();
            tv.setTypeface(bold ? medium() : regular());
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) apply(vg.getChildAt(i));
        }
    }

    /**
     * 混排文本：iconChar 部分用 Material Icons 字体渲染，其余文字走系统字体。
     * 用于「图标 + 天气描述」这类一行文案。
     */
    public static SpannableString mixIcon(String iconChar, String text) {
        String all = iconChar + "  " + text;
        SpannableString ss = new SpannableString(all);
        ss.setSpan(new IconTypefaceSpan(icons()), 0, iconChar.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ss;
    }

    /**
     * 多段混排：每段为 {图标码点, 文字}，段间以 sep 连接。
     * 用于「图标 体感 °  ·  图标 湿度 %  ·  图标 风速」这类统计行。
     */
    public static SpannableString mixAll(String[][] pairs, String sep) {
        StringBuilder sb = new StringBuilder();
        int n = pairs.length;
        int[] starts = new int[n];
        int[] lens = new int[n];
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(sep);
            starts[i] = sb.length();
            sb.append(pairs[i][0]).append(' ').append(pairs[i][1]);
            lens[i] = pairs[i][0].length();
        }
        SpannableString ss = new SpannableString(sb.toString());
        for (int i = 0; i < n; i++) {
            if (lens[i] > 0) {
                ss.setSpan(new IconTypefaceSpan(icons()), starts[i], starts[i] + lens[i],
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return ss;
    }

    /** 简单 TypefaceSpan 变体：直接持有 Typeface */
    static class IconTypefaceSpan extends TypefaceSpan {
        private final Typeface tf;
        IconTypefaceSpan(Typeface typeface) {
            super("");
            tf = typeface;
        }
        @Override
        public void updateDrawState(android.text.TextPaint ds) {
            ds.setTypeface(tf);
        }
        @Override
        public void updateMeasureState(android.text.TextPaint paint) {
            paint.setTypeface(tf);
        }
    }
}
