package com.simpleweather.app;

/**
 * 小组件尺寸档：把「当前可用尺寸(dp)」翻译成一整套协调的排版参数。
 *
 * <p>设计原则（v10.1）：尺寸只决定「显示多少内容」和「整体缩放多少」，
 * 内容的相对次序、对齐、行距比例始终一致——拉伸时不会出现某一块被拉扁、
 * 某一块被挤掉的情况。阈值都是「先算余量，再决定加不加下一块」的贪心阶梯：
 * 头 → 主块 → 描述 → 详情行一 → 详情行二 → 日出日落。
 * 余量还有富余就依次喂给：主块放大（{@link #heroBoost}）、块间距（{@link #gapDp}），
 * 尽量把整个界面铺满，不留下空荡荡的一片。
 *
 * <p><b>行高必须按真实值估</b>：行高不只是"字号 × 1.2"，还要算上 TextView 的
 * font padding（默认 includeFontPadding=true，上下各约 0.15em）、控件自身的
 * marginTop、以及图标比文字高的情况。v10.1 前两版就是估小了约 20%，
 * 导致 2x2 / 3x2 / 4x2 这类"矮格子"把塞不下的块也排进去，底部内容被裁掉。
 * 另外文字还会跟着用户「系统字体大小」放大，所以估算统一乘 {@code fontScale}。
 *
 * <p>本类刻意不引用任何 Android API（只做整数/浮点运算），因此可以脱离
 * 构建环境单独用 javac 编译，在一张尺寸矩阵上离线校验各档位是否合理。
 */
public final class WidgetSize {

    /** 尺寸档：按短边划分，供需要粗粒度分支时使用 */
    public static final int TIER_TINY = 0, TIER_SMALL = 1, TIER_MEDIUM = 2,
            TIER_LARGE = 3, TIER_XL = 4;

    /**
     * 多日预报块总开关。
     * v10.1 应用户要求：4x4（多日组件默认尺寸）不再显示多日预报，故整块关闭；
     * 布局与渲染代码都还在，改回 true 即可恢复（行数、行距由下方阶梯决定）。
     */
    public static final boolean ROWS_ENABLED = false;

    // ---- 设计基准：取"内容刚好铺满"的尺寸，超出部分用于放大 ----
    /** 单日（2x2 起）布局的设计基准短边 */
    private static final float BASE_HERO = 110f;
    /** 多日布局的设计基准短边：预报摘除后内容变少，基准相应下调，让 4x4 能放大铺满 */
    private static final float BASE_LIST = 190f;
    // 放大上限不宜太高：字号涨得比空间快，会把详情行挤没（中部尺寸尤其明显）
    private static final float SCALE_MIN_HERO = 0.90f, SCALE_MAX_HERO = 1.35f;
    private static final float SCALE_MIN_LIST = 0.85f, SCALE_MAX_LIST = 1.90f;

    // ---- 各块在 scale=1、fontScale=1 时占用的高度(dp，含行高、font padding、marginTop）----
    private static final float PAD_HERO = 10f, PAD_LIST = 13f;
    /**
     * 四周安全区（dp）：在基础内边距之上再留一圈，保证任何尺寸、任何 ROM 下
     * 内容都不会贴边或被吃掉半个字。宽度/高度预算都用加过安全区的 padDp 计算，
     * 所以它同时作用于横向与纵向。
     */
    private static final float SAFE_PAD_DP = 2.5f;
    private static final float H_HEADER = 18f;      // 城市(12sp) / 更新时间(10sp)
    private static final float H_HERO = 44f;        // 单日：32sp 粗体温度 + 26dp 图标
    private static final float H_HERO_LIST = 30f;   // 多日：18sp 温度 + 20dp 图标（+ 描述同行时更高）
    private static final float H_DESC = 18f;        // 描述(11sp) + 升降
    private static final float H_EXTRA = 20f;       // 详情行（10sp + 14dp 图标）
    private static final float H_SUN = 20f;         // 日出日落(10sp) + 分隔线
    private static final float ROW_MIN = 26f, ROW_MAX = 34f;   // 预报行高下限/上限
    private static final float GAP_MAX = 7f;        // 块间最大额外留白（单侧）
    /** 主块最大可放大比例（余量富余时把大温度/图标做大，比留白好看） */
    private static final float HERO_BOOST_MAX = 1.0f;
    /** 竖向/横向安全系数：估算与实际有出入时，宁可少放一块 */
    private static final float V_SAFETY = 0.97f, W_SAFETY = 1.08f;
    /** 系统字体缩放的可用区间（超出按边界钳制） */
    private static final float FS_MIN = 0.85f, FS_MAX = 1.45f;

    // ---- 详情项在 scale=1、fontScale=1 时的估算宽度(dp)；按优先级排列，装不下就砍尾 ----
    private static final float[] NEED_EXTRA_A = {40f, 34f, 46f};   // 体感 / 湿度 / 风
    private static final float[] NEED_EXTRA_B = {34f, 38f};        // 云量 / UV
    private static final float ITEM_GAP = 7f;
    /** 预报行宽度低于此值（相对系数）时进入紧凑模式：隐藏降水概率列 */
    private static final float ROW_WIDE = 150f;
    /** 第二详情行（云量/UV）的最小高度门槛：矮了整行不放，避免被裁 */
    private static final float B_MIN_HERO = 190f, B_MIN_LIST = 200f;
    /** 日出日落的最小高度门槛与最小宽度 */
    private static final float SUN_MIN_HERO = 215f, SUN_MIN_LIST = 200f, SUN_MIN_W = 150f;

    /** 主块基准尺寸（单一出处，Provider 直接引用，避免两处各写一份） */
    public static final float ICON_HERO = 26f, ICON_LIST = 20f;
    public static final float TEMP_HERO = 32f, TEMP_LIST = 18f;
    /** 温度串宽度的粗略估算（em）："-26°" 一类的上限，仅用于给"放大"设软上限 */
    private static final float TEMP_EM = 1.5f;

    /** 主块基准：图标(dp) / 温度(sp) */
    public static float iconBase(boolean listMode) { return listMode ? ICON_LIST : ICON_HERO; }
    public static float tempBase(boolean listMode) { return listMode ? TEMP_LIST : TEMP_HERO; }

    /** 当前取向下的可用尺寸(dp) */
    public final int w, h;
    /** 统一缩放系数：字号、图标、内边距、行距全部由它派生 */
    public final float scale;
    public final int tier;
    /** 根内边距(dp) */
    public final int padDp;
    /** 块间额外留白(dp，单侧)，用于吸收尺寸余量 */
    public final int gapDp;
    /** 主块放大比例 0~{@link #HERO_BOOST_MAX}：余量富余时把温度/图标做大 */
    public final float heroBoost;
    /** 详情行一（体感/湿度/风）可见项数 0~3 */
    public final int slotsA;
    /** 详情行二（云量/UV）可见项数 0~2 */
    public final int slotsB;
    public final boolean sunMoon;
    /** 预报行数 0~5（{@link #ROWS_ENABLED} 关闭时为 0） */
    public final int rows;
    /** 预报行紧凑模式：宽度紧张，隐藏降水概率列并收紧字号 */
    public final boolean rowsCompact;
    /** 预报行额外上下内边距(dp) */
    public final int rowPadDp;

    private WidgetSize(int w, int h, float scale, int tier, int padDp, int gapDp,
                       float heroBoost, int slotsA, int slotsB, boolean sunMoon,
                       int rows, boolean rowsCompact, int rowPadDp) {
        this.w = w; this.h = h; this.scale = scale; this.tier = tier;
        this.padDp = padDp; this.gapDp = gapDp; this.heroBoost = heroBoost;
        this.slotsA = slotsA; this.slotsB = slotsB; this.sunMoon = sunMoon;
        this.rows = rows; this.rowsCompact = rowsCompact; this.rowPadDp = rowPadDp;
    }

    public boolean extrasA() { return slotsA > 0; }
    public boolean extrasB() { return slotsB > 0; }

    /**
     * 由 launcher 上报的尺寸选项算出排版参数。
     *
     * <p>横竖屏判定沿用 launcher 的约定：四个值全部有效、且明确「展开方向」
     * 是横向时（maxWidth &gt; maxHeight），取 宽=maxWidth / 高=minHeight；
     * 否则按竖屏取 宽=minWidth / 高=maxHeight。任一值缺失时回落到设计基准尺寸，
     * 保证初始渲染（部分 launcher 首次不给 options）也有合理排版。
     *
     * @param listMode  true=多日组件（基准 190dp），false=单日组件（基准 110dp）
     * @param fontScale 系统字体缩放（Configuration.fontScale），影响文字块的高度与宽度估算
     */
    public static WidgetSize compute(int minW, int minH, int maxW, int maxH,
                                     boolean listMode, float fontScale) {
        float base = listMode ? BASE_LIST : BASE_HERO;
        float fs = clamp(fontScale <= 0f ? 1f : fontScale, FS_MIN, FS_MAX);

        int w = 0, h = 0;
        if (minW > 0 && minH > 0) {
            if (maxW > minW && maxH > minH && maxW > maxH) {   // 横屏
                w = maxW;
                h = minH;
            } else {                                           // 竖屏
                w = minW;
                h = Math.max(maxH, minH);
            }
        }
        if (w <= 0) w = Math.round(base);
        if (h <= 0) h = Math.round(base);

        float scale = Math.min(w, h) / base;
        scale = clamp(scale, listMode ? SCALE_MIN_LIST : SCALE_MIN_HERO,
                listMode ? SCALE_MAX_LIST : SCALE_MAX_HERO);
        int padDp = Math.round((listMode ? PAD_LIST : PAD_HERO) * scale + SAFE_PAD_DP);
        // 安全区不得贪吃：最多占到短边的 1/8，否则小尺寸上内容会被挤没
        padDp = Math.min(padDp, Math.max(6, Math.min(w, h) / 8));

        // 竖向余量：从整块高度里依次扣除已确定要显示的部分
        float room = (h - 2f * padDp - 1f) * V_SAFETY;
        float textK = scale * fs;                       // 文字块的高度/宽度系数

        room -= H_HEADER * textK;                       // 头部（永远显示）
        room -= (listMode ? H_HERO_LIST : H_HERO) * textK;   // 主块（永远显示）
        room -= H_DESC * textK;                         // 描述（永远显示）

        float contentW = w - 2f * padDp - 2f;           // 横向可用宽度
        int slotsA = 0;
        if (room >= H_EXTRA * textK) {
            slotsA = fit(NEED_EXTRA_A, contentW, textK);
            if (slotsA > 0) room -= H_EXTRA * textK;
        }
        // 第二详情行：除了「装得下」，还要求整体足够高——矮尺寸下宁可整行不放，
        // 也不要出现半截被裁的「云量 / UV」
        int slotsB = 0;
        float bMinH = listMode ? B_MIN_LIST : B_MIN_HERO;
        if (slotsA > 0 && room >= H_EXTRA * textK && h >= bMinH) {
            slotsB = fit(NEED_EXTRA_B, contentW, textK);
            if (slotsB > 0) room -= H_EXTRA * textK;
        }
        // 日出日落：既要有竖向余量，也要有横向空间容纳整行文字
        boolean sunMoon = room >= H_SUN * textK && h >= (listMode ? SUN_MIN_LIST : SUN_MIN_HERO)
                && contentW >= SUN_MIN_W * textK;
        if (sunMoon) room -= H_SUN * textK;

        // 预报行（v10.1 应用户要求整块关闭，见 ROWS_ENABLED）
        int rows = 0;
        int rowPadDp = 0;
        if (ROWS_ENABLED && h >= 160 && room >= ROW_MIN * textK) {
            float rowFloor = ROW_MIN * textK;
            rows = (int) Math.floor(room / rowFloor);
            if (rows > 5) rows = 5;
            if (rows > 0) {
                float per = room / rows;                        // 摊满余量
                float rowH = clamp(per, rowFloor, ROW_MAX * textK);
                rowPadDp = Math.round((rowH - rowFloor) / 2f);
                room -= rows * rowH;
            }
        }

        // 余量富余：先把主块做大（大温度/大图标比留白好看），再摊成块间留白。
        // 但放大不能只吃高度——图标与温度文本还得放得下，否则高度余量白喂给放大、
        // 底部反而空（2x3/2x4 这类"窄而高"的格子尤其明显）。
        float boostMax = HERO_BOOST_MAX;
        float kByW = (contentW - 6f) / (iconBase(listMode) + tempBase(listMode) * fs * TEMP_EM);
        if (kByW < scale * (1f + boostMax)) {
            boostMax = Math.max(0f, kByW / scale - 1f);
        }
        float heroBoost = 0f;
        float heroH = (listMode ? H_HERO_LIST : H_HERO) * textK;
        if (room > heroH) {
            heroBoost = clamp(room / (heroH * 2f), 0f, boostMax);
            room -= heroH * heroBoost;
        }
        int gapDp = (int) clamp((float) Math.floor(room / 12f), 0f, GAP_MAX * scale);

        int shortSide = Math.min(w, h);
        int tier = shortSide < 130 ? TIER_TINY
                : shortSide < 180 ? TIER_SMALL
                : shortSide < 250 ? TIER_MEDIUM
                : shortSide < 340 ? TIER_LARGE : TIER_XL;

        boolean compact = contentW < ROW_WIDE * textK;
        return new WidgetSize(w, h, scale, tier, padDp, gapDp, heroBoost,
                slotsA, slotsB, sunMoon, rows, compact, rowPadDp);
    }

    /** 按优先级逐项装入，返回能装下的项数（装不下即停，不跳过） */
    private static int fit(float[] need, float budget, float textK) {
        float used = 0f;
        int n = 0;
        for (int i = 0; i < need.length; i++) {
            float wdt = need[i] * textK * W_SAFETY
                    + (n > 0 ? ITEM_GAP * textK * W_SAFETY : 0f);
            if (used + wdt > budget) break;
            used += wdt;
            n++;
        }
        return n;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** 便于日志/校验：一行摘要 */
    @Override
    public String toString() {
        return "WidgetSize{" + w + "x" + h + "dp scale=" + String.format("%.2f", scale)
                + " tier=" + tier + " pad=" + padDp + " gap=" + gapDp
                + " hero+" + String.format("%.2f", heroBoost)
                + " extrasA=" + slotsA + " extrasB=" + slotsB
                + " sun=" + sunMoon + " rows=" + rows
                + (rowsCompact ? "(compact)" : "") + " rowPad=" + rowPadDp + "}";
    }
}
