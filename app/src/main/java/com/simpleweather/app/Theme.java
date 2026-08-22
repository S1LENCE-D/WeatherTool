package com.simpleweather.app;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;

/**
 * 主题管理：深色 / 浅色 / 跟随系统（默认跟随系统）。
 * 所有界面颜色一律经由此处取用，保证双主题统一。
 *
 * v9.88.5：界面引擎已收敛——仅保留经典 Java View 界面（Compose 引擎相关代码已清除）。
 */
public class Theme {

    public static final String PREFS = "weather_theme";
    public static final String KEY = "mode";          // "system" | "dark" | "light"

    // ---------- v9.88.5：预警低饱和显示 ----------

    public static final String ALERT_MUTED_KEY = "alert_muted";

    /** 是否开启预警低饱和显示（默认关闭） */
    public static boolean alertMuted(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(ALERT_MUTED_KEY, false);
    }

    public static void setAlertMuted(Context c, boolean v) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(ALERT_MUTED_KEY, v).apply();
    }

    public static String mode(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "system");
    }

    public static void setMode(Context c, String m) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, m).apply();
    }

    /** 当前是否深色外观 */
    public static boolean isDark(Context c) {
        String m = mode(c);
        if ("dark".equals(m)) return true;
        if ("light".equals(m)) return false;
        int ui = c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return ui == Configuration.UI_MODE_NIGHT_YES;
    }

    // ---------- 文字与装饰色 ----------

    /** 主文字（深色主题=白，浅色主题=深蓝黑） */
    public static int textPrimary(Context c) {
        // v9.14 降低对比度：深色 95% 白 / 浅色 85% 深蓝黑
        return isDark(c) ? 0xF2FFFFFF : 0xD91F2A36;
    }

    /** 次要文字 */
    public static int textSecondary(Context c) {
        return isDark(c) ? 0xE68A8A8E : 0xD95C6B7A;
    }

    /** 提示文字 */
    public static int textHint(Context c) {
        return isDark(c) ? 0xFF55606B : 0xFF8B98A5;
    }

    /** 强调蓝（v9.62：Material 3 蓝种子 #0061A4，深色=浅蓝亮字） */
    public static int accent(Context c) {
        return isDark(c) ? 0xFFA2C8FF : 0xFF0061A4;
    }

    /** 强调蓝（亮版，温度条渐变色） */
    public static int accentLight(Context c) {
        return isDark(c) ? 0xFF8FC8FF : 0xFF8AB6F9;
    }

    // ---------- v9.62：Material 3 tonal 色板（蓝种子 #0061A4） ----------

    /** M3 surfaceContainerHigh：弹窗/面板表面 */
    public static int surfaceContainerHigh(Context c) {
        return isDark(c) ? 0xFF2B2D33 : 0xFFE6E9F0;
    }

    /** M3 surfaceContainerLow：建议卡等内嵌卡片表面 */
    public static int surfaceContainerLow(Context c) {
        return isDark(c) ? 0xFF1E2025 : 0xFFF6F7F9;
    }

    /** M3 primaryContainer：tonal 按钮/选中底 */
    public static int primaryContainer(Context c) {
        return isDark(c) ? 0xFF00497C : 0xFFD2E4FF;
    }

    /** M3 onPrimaryContainer：tonal 按钮/选中字 */
    public static int onPrimaryContainer(Context c) {
        return isDark(c) ? 0xFFA2C8FF : 0xFF001D36;
    }

    /** M3 outlineVariant：描边 */
    public static int outlineVariant(Context c) {
        return isDark(c) ? 0xFF44474E : 0xFFC4C6D0;
    }

    /** M3 涟漪色 */
    public static int ripple(Context c) {
        return isDark(c) ? 0x33A2C8FF : 0x330061A4;
    }

    // ---------- v9.90：设置页蓝灰（slate）色板 ----------

    /** 设置页背景（浅蓝灰 / 深蓝灰黑） */
    public static int setBg(Context c) { return isDark(c) ? 0xFF10151C : 0xFFEDF1F6; }

    /** 设置页选中高亮（中蓝灰 / 浅蓝灰） */
    public static int setCardSelected(Context c) { return isDark(c) ? 0xFF26303D : 0xFFDCE6F0; }

    /** 设置页强调（蓝灰，替代主界面亮蓝） */
    public static int setAccent(Context c) { return isDark(c) ? 0xFFA3BFE0 : 0xFF3E5C7A; }

    /** 设置页输入框底 */
    public static int setInputBg(Context c) { return isDark(c) ? 0xFF232C38 : 0xFFFFFFFF; }

    /** 设置页描边 / 分割线 */
    public static int setDivider(Context c) { return isDark(c) ? 0xFF2A3542 : 0xFFD5DEE8; }

    // ---------- 毛玻璃卡文字自适应（v9.55） ----------

    /** 感知亮度 0~1（299/587/114 加权） */
    public static float luma(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 255000f;
    }

    /** 亮背景（玻璃卡叠在亮渐变上）→ 深色字组；暗背景 → 浅色字组。
     *  次级/强调全部实色化，保证毛玻璃上对比度足够明显。
     *  强调色为 M3 深蓝。 */
    private static final int[] CARD_DARK_M3 = {0xFF1F2A36, 0xFF5C6B7A, 0xFF0061A4};
    private static final int[] CARD_LIGHT_M3 = {0xFFFFFFFF, 0xFFD9E2EC, 0xFFA2C8FF};

    /** 按卡片所在背景色返回 {primary, secondary, accent} */
    public static int[] cardTextColors(int bg) {
        return luma(bg) > 0.48f ? CARD_DARK_M3 : CARD_LIGHT_M3;
    }

    /** 分割线 */
    public static int divider(Context c) {
        return isDark(c) ? 0x1AFFFFFF : 0x14000000;
    }

    /** 温度条轨道底色 */
    public static int trackBg(Context c) {
        return isDark(c) ? 0xFFE8EDF5 : 0xFFE3EAF1;
    }

    // ---------- 毛玻璃 ----------

    /** 玻璃白罩透明度（v9.8 调低更通透：深色 155 / 浅色 180，背景更清晰） */
    public static int glassAlpha(Context c) {
        return isDark(c) ? 155 : 180;
    }

    /** 玻璃高光白（浅色主题下调淡） */
    public static int glassHighlight(Context c) {
        return isDark(c) ? 0x10FFFFFF : 0x08FFFFFF;
    }

    /** 玻璃描边（v9.87 调淡：液态玻璃更柔和，仅保留轻边界） */
    public static int glassBorder(Context c) {
        return isDark(c) ? 0x1FFFFFFF : 0x1A000000;
    }

    // ---------- 背景粒子 ----------

    /** 云/雪粒子主色 */
    public static int particleColor(Context c) {
        return isDark(c) ? 0xFFFFFFFF : 0xFF4A5A6A;
    }

    /** 雨滴色 */
    public static int rainColor(Context c) {
        return isDark(c) ? 0xFFFFFFFF : 0xFF3A4A5A;
    }

    /** 月亮色 */
    public static int moonColor(Context c) {
        return isDark(c) ? 0xFFF5F0E1 : 0xFF7C8FA6;
    }

    /** 状态栏浅色图标 flag（浅色主题下需要） */
    public static int statusBarLightFlag(Context c) {
        return isDark(c) ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    }

    // ---------- 背景渐变（WMO + 昼夜 + 明暗） ----------

            /** 按天气码 + 小时选择天空渐变。
     *  时间档位标志色（人工精调）：凌晨/清晨/上午/正午/下午/黄昏(晚霞)/夜晚 各一套
     *  深浅主题分别配色；天气只做明暗微调（乘系数），不动色相，保证颜色干净。 */
    public static int[] paletteHour(Context c, int code, int hour) {
        boolean dark = isDark(c);
        // 7 档时间 × 3 色（top/mid/bottom）—— 浅色
        final int[][][] LIGHT = {
            {{0xFF5A6E92, 0xFF7185A8, 0xFF8FA3C4}}, // 凌晨 0-4 暮蓝
            {{0xFF9FC4E4, 0xFFC4DCEE, 0xFFEAF2F9}}, // 清晨 5-7 暖晨曦
            {{0xFF6FAFE0, 0xFF9ACBF0, 0xFFC9E4F8}}, // 上午 8-10 清爽蓝
            {{0xFF3F8FD0, 0xFF6FB3E8, 0xFFA8D4F2}}, // 正午 11-13 湛蓝
            {{0xFF5AA0DC, 0xFF84BFEC, 0xFFB4D9F5}}, // 下午 14-16 温和蓝
            {{0xFF9A7BB8, 0xFFD98FA0, 0xFFF5C98A}}, // 黄昏 17-19 晚霞（紫粉金）
            {{0xFF4A5F84, 0xFF61779C, 0xFF8095B6}}, // 夜晚 20-23 深暮蓝
        };
        // 深色
        final int[][][] DARK = {
            {{0xFF0D1526, 0xFF152038, 0xFF1E2C49}}, // 凌晨
            {{0xFF1E2B45, 0xFF2C3D5E, 0xFF3D5277}}, // 清晨
            {{0xFF23355A, 0xFF32487A, 0xFF46609A}}, // 上午
            {{0xFF1E3D73, 0xFF2E5AA0, 0xFF4780C2}}, // 正午
            {{0xFF213C66, 0xFF30558C, 0xFF4773AC}}, // 下午
            {{0xFF3E2A4E, 0xFF6E4255, 0xFFA3644A}}, // 黄昏晚霞（暗紫暗粉暗橙）
            {{0xFF0A101F, 0xFF121B30, 0xFF1C2942}}, // 夜晚
        };
        int[] sel = (dark ? DARK : LIGHT)[slot(hour)][0];
        float k = weatherK(code);   // 天气只调明暗，色相不变
        return new int[]{
                scale(sel[0], k), scale(sel[1], k), scale(sel[2], k)};
    }

    /** 时间档位：0-4 凌晨 / 5-7 清晨 / 8-10 上午 / 11-13 正午 / 14-16 下午 / 17-19 黄昏 / 20-23 夜晚 */
    private static int slot(int hour) {
        if (hour < 5) return 0;
        if (hour < 8) return 1;
        if (hour < 11) return 2;
        if (hour < 14) return 3;
        if (hour < 17) return 4;
        if (hour < 20) return 5;
        return 6;
    }

    /** 天气明度系数：晴 1.0 / 多云 0.92 / 阴 0.84 / 雾 0.96 / 雨 0.80 / 雪 1.06 / 雷暴 0.72 */
    private static float weatherK(int code) {
        if (code <= 1) return 1.0f;
        if (code == 2) return 0.92f;
        if (code == 3) return 0.84f;
        if (code >= 45 && code <= 48) return 0.96f;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return 1.06f;
        if (code >= 95) return 0.72f;
        return 0.80f;   // 雨
    }

    /** RGB 等比缩放（只改明暗，色相/饱和度不变） */
    private static int scale(int color, float k) {
        int r = Math.min(255, (int) (Color.red(color) * k));
        int g = Math.min(255, (int) (Color.green(color) * k));
        int b = Math.min(255, (int) (Color.blue(color) * k));
        return Color.rgb(r, g, b);
    }

    /** 兼容旧签名（day=true 视为正午 12 点，false 视为夜晚 23 点） */
    public static int[] palette(Context c, int code, boolean day) {
        return paletteHour(c, code, day ? 12 : 23);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
