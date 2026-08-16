package com.simpleweather.app;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.AlarmManager;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 天气 App：自动定位 -> Open-Meteo 实时天气 / 日出日落 / 24h / 7天
 * 每 30 秒自动刷新；Material 3 风格手搓 UI + 全套动效。
 * v2.1：修复二级页闪退；新增定时天气通知（通知推送 + 开机自启动恢复）。
 */
public class MainActivity extends Activity {

    private static final int REQ_LOC = 100;
    private static final int REQ_NOTIF = 101;
    private static final int REQ_EXPORT_LOG = 102;   // v9.87-fix：SAF 导出诊断日志
    /** v9.73：权限引导弹窗引用与状态行（授权返回后 onResume 实时刷新） */
    private Dialog permGuide;
    private TextView[] permStates;
    /** v9.16：前台自动刷新间隔 120 秒（原 30 秒，省电） */
    private static final long REFRESH_MS = 120_000;
    /** v9.16：后台自动刷新间隔 1 小时（进程存活期间；被杀后回前台补刷兜底） */
    private static final long REFRESH_MS_BG = 60L * 60 * 1000;
    /** v9.16：回前台自动刷新的最小间隔（避免短暂切回就重复请求） */
    private static final long RESUME_THROTTLE_MS = 15_000;
    private static final int[] DAY_COLORS = {0xFF0061A4, 0xFF4D8FD9, 0xFF9AC3F2};
    private static final int[] NIGHT_COLORS = {0xFF111A2E, 0xFF1E3050, 0xFF2C4570};

    private LinearLayout contentRoot, hourlyRow, dailyList;
    private LinearLayout detailRow;   // v9.22：实时详情卡（湿度/风/云量/UV）
    private View rootFrame;
    private TextView cityText, tempText, descText, feelsText, sunTimeText, sourceText;
    private static double[] uvDay = null;      // v9.67：今日逐小时 UV（从当前小时起 24 个点）
    private static int[] uvDayHour = null;     // 对应的小时数（0-23）
    private static UvDayView activeUvDay = null;  // v9.68：UV 弹窗内走势图（后台刷新完成后更新）
    private static boolean uvDayFailed = false;   // 最近一次逐小时 UV 拉取是否失败
    private TextView titleHourlyTv, titleDailyTv;   // v9.56：预报标题随背景亮度自适应
    private TextView creditTv;                       // v9.57：底部制作者信息随背景亮度自适应
    private View alertBar, refreshBtn;
    private ImageView refreshIcon;
    private TextView refreshLabel, cityIconTv, gearIconTv;
    private WeatherBackView weatherBg;
    private TextView alertText, mapIcon, reportIcon;
    private TextView regionText, ipHintText;
    private WeatherApi.AlarmResult alertResult;
    private int alertMode = 0;   // 1=本地预警 0=暂无 -1=服务不可用
    private long lastAlertTs = 0;
    private String lastAlertCity = "";   // v9.44：最近一次预警请求的城市（同城双请求防抖）
    private boolean alertForceNext = false;   // v9.44：恢复自动定位后强制刷新预警（绕过节流）
    private String locChoice = "auto";   // v9.46/47：定位方式 "auto"/"gps"/"ip"（auto=GPS 可用优先，否则 IP）
    private boolean settingsInDetail = false;   // v9.89：设置是否处于二级页（返回键回一级）
    private boolean settingsAnimating = false;  // v9.87.2：设置页切换动画防抖
    private boolean lastLocModeIp = false;   // v9.47：最近一次定位是否 IP（GPS 监控自动切换判断用）
    private long lastGpsAutoTs = 0;   // v9.47：GPS 自动切换节流（60s）
    private LocationManager gpsMgr = null;      // v9.47：GPS 实时监控
    private LocationListener gpsListener = null;
    private android.location.GnssStatus.Callback gnssCallback = null;   // v9.87-fix：GNSS 卫星状态诊断
    private long lastGnssLogTs = 0;                    // 卫星统计日志节流
    private boolean gpsWatching = false;
    private NetWatcher netWatcher = null;       // v9.49：断网检测与置顶提示
    private boolean lastManual = false;   // 最近一次刷新是否手动（用于成功 Toast）
    private SunArcView sunArc;
    // v9.39：日落检测 -> 右半显示月相
    private View sunHalf, moonHalf, sunMoonRow;
    private MoonPhaseView moonView;
    private TextView moonText;
    private boolean moonShown = false;    // 当前卡片是否处于「日落+月相」模式
    private Locator locator;
    // v9.41：降雨图入口随定位模式显隐（手动城市隐藏）+ 预警请求竞态序号
    private View mapCardView;
    private boolean mapCardShown = true;
    private int alertSeq = 0;
    private boolean foreground = true;    // v9.16：前台 120s / 后台 1h 刷新切换

    // 毛玻璃：背景模糊快照 + 玻璃卡片集合
    private Bitmap glassCache;
    private View[] glassCards;
    private final Handler autoRefresh = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            startLoad(false);
            // v9.16：按前台/后台状态排下一次刷新（前台 120s，后台 1h）
            autoRefresh.postDelayed(this, foreground ? REFRESH_MS : REFRESH_MS_BG);
        }
    };

    private static boolean reopenSettings = false;   // 主题切换 recreate 后自动重开设置面板
    private boolean loading = false;
    private int loadGen = 0;   // v9.45：加载代际——新请求立即开始并作废旧线程结果（根治 loading 阻塞）
    private boolean rendered = false;
    private long lastFullRefreshTs = 0;   // v9.15：最近一次成功定位刷新的时间（回前台节流用）
    private float currentTemp = Float.NaN;
    private int[] bgColors = NIGHT_COLORS;

    // 城市名缓存（坐标位移 < 500m 不重复反查）
    private String cityName = null;
    private double lastLat, lastLng;
    private String regionProvince = null;   // 省名（预警精确匹配用）
    private String regionDistrict = null;   // 区县名（预警精确匹配用）
    private double curLat = 35.0, curLng = 105.0;   // 当前定位（供地图页使用）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // v9.90：Compose 引擎开关打开时，转发到 Compose 新界面（经典界面兜底）
        if (Theme.isCompose(this)) {
            startActivity(new Intent(this, ComposeWeatherActivity.class));
            finish();
            return;
        }
        // v9.87-diag：启动时报告推送链路关键权限状态
        try {
            StringBuilder sb = new StringBuilder("启动诊断: SDK=")
                    .append(Build.VERSION.SDK_INT).append(" API=")
                    .append(Build.VERSION.RELEASE);
            if (Build.VERSION.SDK_INT >= 33) {
                sb.append(" POST_NOTIF=").append(checkSelfPermission(
                        "android.permission.POST_NOTIFICATIONS")
                        == PackageManager.PERMISSION_GRANTED);
            }
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    java.lang.reflect.Method m = AlarmManager.class
                            .getMethod("canScheduleExactAlarms");
                    AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                    sb.append(" EXACT_ALARM=").append((Boolean) m.invoke(am));
                } catch (Exception e) {
                    sb.append(" EXACT_ALARM=err");
                }
            }
            android.os.PowerManager pm = (android.os.PowerManager)
                    getSystemService(POWER_SERVICE);
            if (pm != null && Build.VERSION.SDK_INT >= 23) {
                sb.append(" IGNORE_BATTERY=")
                        .append(pm.isIgnoringBatteryOptimizations(getPackageName()));
            }
            sb.append(" 预警开关=").append(AlertWatcher.enabled(this));
            Diag.i(sb.toString());
        } catch (Exception e) {
            Diag.i("启动诊断异常: " + e);
        }
        LogFile.init(this);   // v9.87-fix：定位诊断日志（Download/WeatherTool_log_*.log）
        if (!LogFile.state().startsWith("Download/")) {
            // 降级提示：Download 写入失败（移植 ROM MediaProvider 异常等），日志在私有目录
            final String st = LogFile.state();
            new android.os.Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    Toast.makeText(MainActivity.this,
                            "诊断日志写入受限，已存私有目录（设置里可导出）：\n" + st,
                            Toast.LENGTH_LONG).show();
                }
            }, 500);
        }
        CrashCatcher.install(this);
        setContentView(R.layout.activity_main);

        contentRoot = findViewById(R.id.contentRoot);
        applyThemeToTree(findViewById(android.R.id.content));
        hourlyRow = findViewById(R.id.hourlyRow);
        dailyList = findViewById(R.id.dailyList);
        detailRow = findViewById(R.id.detailRow);
        cityText = findViewById(R.id.cityText);
        titleHourlyTv = findViewById(R.id.titleHourly);
        titleDailyTv = findViewById(R.id.titleDaily);
        creditTv = findViewById(R.id.creditText);
        regionText = findViewById(R.id.regionText);
        ipHintText = findViewById(R.id.ipHintText);
        tempText = findViewById(R.id.tempText);
        descText = findViewById(R.id.descText);
        feelsText = findViewById(R.id.feelsText);
        sunTimeText = findViewById(R.id.sunTimeText);
        sourceText = findViewById(R.id.sourceText);
        sunArc = findViewById(R.id.sunArc);
        sunHalf = findViewById(R.id.sunHalf);
        moonHalf = findViewById(R.id.moonHalf);
        sunMoonRow = findViewById(R.id.sunMoonRow);
        moonView = findViewById(R.id.moonView);
        moonText = findViewById(R.id.moonText);
        refreshBtn = findViewById(R.id.refreshBtn);
        refreshIcon = findViewById(R.id.refreshIcon);
        refreshLabel = findViewById(R.id.refreshLabel);
        cityIconTv = findViewById(R.id.cityIcon);
        gearIconTv = findViewById(R.id.gearIcon);
        weatherBg = findViewById(R.id.weatherBg);
        mapIcon = findViewById(R.id.mapIcon);
        reportIcon = findViewById(R.id.reportIcon);
        locator = new Locator(this);
        netWatcher = new NetWatcher(this, null);   // v9.49：断网检测（提示 UI 内部自管）
        locChoice = getSharedPreferences("loc_pref", MODE_PRIVATE)
                .getString("loc_choice", "auto");
        if (locChoice == null || locChoice.isEmpty()) locChoice = "auto";

        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // v9.46：定位方式改在设置面板选择，刷新直接按所选方式重新定位
                startLoad(true);
            }
        });

        alertBar = findViewById(R.id.alertBar);
        alertText = findViewById(R.id.alertText);
        alertBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (alertMode == -1) {
                    // v9.41：服务不可用时点击预警条直接重试
                    loadAlerts(currentCityName());
                } else {
                    openAlertList();
                }
            }
        });

        // Google Sans 字体（数字/英文走 Google Sans；中文回退系统字体）
        Fonts.load(this);
        Fonts.apply(contentRoot);

        // Material Icons 图标字体（云图 / 通知入口）
        mapIcon.setTypeface(Fonts.icons());
        reportIcon.setTypeface(Fonts.icons());
        mapIcon.setText("\uE798");     // water_drop 降雨
        reportIcon.setText("\uE8B5");  // schedule
        weatherBg.setWeather(0, false);

        // 降雨图 / 云图入口
        mapCardView = findViewById(R.id.mapCard);
        // v9.41：手动城市模式隐藏降雨图入口（无动画），恢复自动定位时动画出现
        mapCardShown = !WeatherReporter.hasManualCity(this);
        mapCardView.setVisibility(mapCardShown ? View.VISIBLE : View.GONE);
        mapCardView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = new Intent(MainActivity.this, RainMapActivity.class);
                // v9.40：手动选择城市时传手动坐标，云图不再按自动定位显示
                if (WeatherReporter.hasManualCity(MainActivity.this)) {
                    it.putExtra("lat", WeatherReporter.manualLat(MainActivity.this));
                    it.putExtra("lng", WeatherReporter.manualLng(MainActivity.this));
                } else {
                    it.putExtra("lat", curLat);
                    it.putExtra("lng", curLng);
                }
                startActivity(it);
                overridePendingTransition(R.anim.rain_enter, R.anim.rain_exit);
            }
        });

        // 定时天气通知入口
        findViewById(R.id.reportCard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showReportDialog();
            }
        });
        refreshReportCard();

        rootFrame = findViewById(R.id.rootFrame);
        int[] initPal = Theme.paletteHour(this, 0,
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        setGradient(initPal);
        weatherBg.setLightPalette(bgBrightness(initPal));
        applyTopColors(bgBrightness(initPal));

        // v9.8 全面屏适配：沉浸式状态栏 + 刘海模式 + 安全区 inset 补偿
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0x00000000);
            getWindow().setNavigationBarColor(0x00000000);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        // v9.87：全面屏手势——内容延伸到手势条区域，底部不留白（insets 补偿在下方 listener）
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        // 顶部内容避开状态栏/刘海，底部避开手势导航条
        final View contentRootV = contentRoot;
        contentRootV.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top = 0, bottom = 0;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets si =
                            insets.getInsets(WindowInsets.Type.systemBars());
                    top = si.top;
                    bottom = si.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(dp(20), dp(14) + top, dp(20), dp(30) + bottom);
                return insets;
            }
        });
        // 玻璃更透后给顶部主文字加轻投影（仅深色模式；浅色模式深字亮底无需投影）
        if (Theme.isDark(this)) {
            cityText.setShadowLayer(5f, 0f, 2f, 0x59000000);
            tempText.setShadowLayer(8f, 0f, 3f, 0x59000000);
            descText.setShadowLayer(5f, 0f, 2f, 0x59000000);
            feelsText.setShadowLayer(5f, 0f, 2f, 0x59000000);
        }

        // 外观设置入口：毛玻璃透明度滑块
        final View gearBtn = findViewById(R.id.gearBtn);
        final TextView gearIcon = (TextView) findViewById(R.id.gearIcon);
        gearIcon.setTypeface(Fonts.icons());
        gearIcon.setText("\uE8B8");   // settings 齿轮
        gearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showSettingsDialog(); }
        });

        // v9.27：查询其他城市入口（放大镜）
        final View cityBtn = findViewById(R.id.cityBtn);
        final TextView cityIcon = (TextView) findViewById(R.id.cityIcon);
        cityIcon.setTypeface(Fonts.icons());
        cityIcon.setText("\uE8B6");   // search 放大镜
        cityBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { showCityDialog(); }
        });

        // v9.27：长按城市名恢复自动定位（仅手动城市模式下生效）
        cityText.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (WeatherReporter.hasManualCity(MainActivity.this)) {
                    WeatherReporter.clearManualCity(MainActivity.this);
                    Toast.makeText(MainActivity.this,
                            "已恢复自动定位", Toast.LENGTH_SHORT).show();
                    // v9.44：切换地区 -> 按设置中的定位方式重新定位并拉取预警
                    alertForceNext = true;
                    startLoad(true);
                } else {
                    Toast.makeText(MainActivity.this,
                            "当前就是自动定位", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        // 毛玻璃初始化：玻璃卡片集合 + 滚动跟随 + 每 4 秒刷新模糊快照
        glassCards = new View[]{
                findViewById(R.id.detailRow),
                findViewById(R.id.sunCard),
                findViewById(R.id.mapCard),
                findViewById(R.id.reportCard),
                findViewById(R.id.hourlyScroll),
                findViewById(R.id.dailyList)
        };
        final android.view.View scrollRoot = findViewById(R.id.scrollRoot);
        scrollRoot.getViewTreeObserver().addOnScrollChangedListener(
                new android.view.ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() { updateGlassPositions(); }
                });
        // v9.70：首次启动弹自绘权限引导（定位/通知/自启动 一次授权，仅弹一次）；
        // 首次的权限请求统一由引导弹窗「一键授权」发起，避免系统框与引导框叠加。
        SharedPreferences fp = getSharedPreferences("first_pref", MODE_PRIVATE);
        boolean firstRun = !fp.getBoolean("guided", false);
        if (firstRun) {
            fp.edit().putBoolean("guided", true).apply();
            contentRoot.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showPermissionGuide();
                }
            }, 900);
        } else {
            // 非首次启动：自动补齐缺失权限（系统对已拒绝项不会重复弹框）
            // 通知权限（Android 13+，用于天气/预警推送）
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
            }
            // 定位权限
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                // 先展示缓存（如有），再申请定位权限
                showCached();
                // v9.62：仅首次启动弹系统申请框；后续启动若权限被拒则 Toast 提示去设置开启（不重复骚扰弹窗）
                SharedPreferences lp = getSharedPreferences("loc_pref", MODE_PRIVATE);
                if (!lp.getBoolean("loc_perm_asked", false)) {
                    lp.edit().putBoolean("loc_perm_asked", true).apply();
                    requestPermissions(
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                } else {
                    Toast.makeText(this,
                            "定位权限未开启，已使用 IP 定位；如需 GPS 定位请到系统设置中开启",
                            Toast.LENGTH_LONG).show();
                }
            }
        }

        // 恢复/确保定时通知调度 + v9.70：恢复预警监控服务
        WeatherReporter.ensureScheduled(this);
        AlertWatcher.ensureRunning(this);
        // v9.87：恢复每小时后台缓存刷新（有成功缓存即开启）
        CacheRefresher.ensureRunning(this);
        // v9.88：恢复后台常驻服务（开关开着则进程保活）
        KeepAliveManager.ensureRunning(this);
        // v9.15：首次加载与每次回前台刷新统一由 onResume 触发，避免冷启动双请求
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        startGpsWatch();   // v9.47：自动模式下实时监听 GPS，可用即优先切换
        if (netWatcher != null) netWatcher.start();   // v9.49：断网监控
        if (reopenSettings) {
            reopenSettings = false;
            showSettingsDialog();       // 主题切换后自动重开，选中态保持
        }
        refreshPermStates();   // v9.73：从系统设置页/授权弹窗返回后，实时刷新权限引导状态
        autoRefresh.removeCallbacks(refreshTask);
        autoRefresh.postDelayed(refreshTask, REFRESH_MS);
        // v9.15：每次回到前台都重新定位 + 更新天气；
        // 15 秒内刚成功刷新过则跳过（防短暂切回/权限弹窗反复触发）
        // v9.18：不再以权限为前置条件——无权限时 Locator 自动走 IP 定位
        long now = System.currentTimeMillis();
        if (now - lastFullRefreshTs >= RESUME_THROTTLE_MS) {
            startLoad(false);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopGpsWatch();   // v9.47：后台停止 GPS 监听省电
        if (netWatcher != null) netWatcher.stop();    // v9.49：后台停止断网监控与提示
        // v9.16：后台改为 1 小时刷新一次（进程存活时；写入缓存，回来不空白）
        foreground = false;
        autoRefresh.removeCallbacks(refreshTask);
        autoRefresh.postDelayed(refreshTask, REFRESH_MS_BG);
    }

    /** v9.81：测试推送等待通知授权后自动重试 */
    private boolean pendingTestPush = false;

    @Override
    protected void onDestroy() {
        // v9.85：Activity 销毁（系统回收/主题切换 recreate）时清除自动刷新任务。
        // 否则 onStop 里排队的 1 小时后台刷新会随 Handler 继续空转：
        // 旧实例泄漏 + 对已销毁页面反复定位/拉取，属后台隐性运行。
        autoRefresh.removeCallbacks(refreshTask);
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_EXPORT_LOG && result == RESULT_OK && data != null && data.getData() != null) {
            // v9.87-fix：SAF 导出日志回调——写入用户选定位置
            boolean ok = LogFile.exportTo(this, data.getData());
            Toast.makeText(this,
                    ok ? "日志已导出" : "日志导出失败，可重试",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_LOC) {
            // v9.18：无论授予与否都继续定位——无权限时 Locator 自动改走 IP 定位（并 Toast 提示）
            // v9.62：首次弹窗被拒时也提示可去系统设置开启
            boolean granted = results != null && results.length > 0
                    && results[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                Toast.makeText(this,
                        "定位权限未授予，已使用 IP 定位；可到系统设置中开启精确定位",
                        Toast.LENGTH_LONG).show();
            }
            startLoad(false);
        } else if (code == REQ_NOTIF) {
            boolean granted = results != null && results.length > 0
                    && results[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this,
                    granted ? "通知权限已开启" : "通知权限未开启，可到系统设置中手动允许",
                    Toast.LENGTH_SHORT).show();
            // v9.81：用户在弹窗里点了「测试推送」后被拦去授权，授予后自动补发
            if (granted && pendingTestPush) {
                pendingTestPush = false;
                Toast.makeText(this, "正在推送天气通知…", Toast.LENGTH_SHORT).show();
                Intent svc1 = new Intent(this, SpeakService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc1);
                else startService(svc1);
            }
        }
        refreshPermStates();   // v9.73：授权结果返回后立即刷新引导弹窗状态行
    }

    // ============ 定时天气通知设置弹窗 ============

    private void showReportDialog() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(Theme.isDark(this)
                ? R.layout.dialog_report
                : R.layout.dialog_report_light);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            // v9.76：M3 Dialog 居中卡片宽度 = min(屏宽-48dp, 360dp)
            WindowManager.LayoutParams lp = d.getWindow().getAttributes();
            lp.width = (int) Math.min(
                    getResources().getDisplayMetrics().widthPixels - 48 * getResources().getDisplayMetrics().density,
                    360 * getResources().getDisplayMetrics().density);
            d.getWindow().setAttributes(lp);
        }
        Fonts.apply(d.findViewById(android.R.id.content));

        // v9.73：图标统一 Material Icons 字形（schedule / chevron_right / send / settings）
        TextView tIcon = (TextView) d.findViewById(R.id.reportTitleIcon);
        if (tIcon != null) { tIcon.setText("\uE8B5"); tIcon.setTypeface(Fonts.icons()); }
        TextView chev = (TextView) d.findViewById(R.id.reportTimeChevron);
        if (chev != null) { chev.setText("\uE5CC"); chev.setTypeface(Fonts.icons()); }
        TextView testIc = (TextView) d.findViewById(R.id.btnTestIcon);
        if (testIc != null) { testIc.setText("\uE163"); testIc.setTypeface(Fonts.icons()); }
        TextView autoIc = (TextView) d.findViewById(R.id.btnAutoStartIcon);
        if (autoIc != null) { autoIc.setText("\uE8B8"); autoIc.setTypeface(Fonts.icons()); }

        final SharedPreferences sp =
                getSharedPreferences(WeatherReporter.PREFS, MODE_PRIVATE);
        final int[] hour = {sp.getInt(WeatherReporter.KEY_HOUR, 8)};
        final int[] minute = {sp.getInt(WeatherReporter.KEY_MINUTE, 0)};

        final M3Switch sw = (M3Switch) d.findViewById(R.id.reportSwitch);
        final TextView timeText = (TextView) d.findViewById(R.id.reportTimeText);
        sw.setChecked(WeatherReporter.enabled(this));
        timeText.setText(String.format(Locale.US, "%02d:%02d", hour[0], minute[0]));

        sw.setOnCheckedChangeListener(new M3Switch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(M3Switch b, boolean on) {
                WeatherReporter.setEnabled(MainActivity.this, on, hour[0], minute[0]);
                Toast.makeText(MainActivity.this,
                        on ? "已开启：每天 " + String.format(Locale.US, "%02d:%02d",
                                hour[0], minute[0]) + " 推送天气通知"
                           : "已关闭定时天气通知",
                        Toast.LENGTH_SHORT).show();
                refreshReportCard();
            }
        });

        timeText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // v9.78：自绘 M3 时间选择器（替代系统 TimePickerDialog，样式统一）
                showTimePicker(hour[0], minute[0], new TimeSetListener() {
                    @Override
                    public void onTimeSet(int h, int m) {
                        hour[0] = h;
                        minute[0] = m;
                        timeText.setText(String.format(Locale.US, "%02d:%02d", h, m));
                        if (sw.isChecked()) {
                            WeatherReporter.setEnabled(
                                    MainActivity.this, true, h, m);
                        }
                        refreshReportCard();
                    }
                });
            }
        });

        // v9.73：M3 时间卡整卡可点，点击即弹时间选择器
        View timeCard = d.findViewById(R.id.timeCard);
        if (timeCard != null) {
            timeCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { timeText.performClick(); }
            });
        }

        d.findViewById(R.id.btnTest).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // v9.81：Android 13+ 无通知权限时通知不显示（表现为点了没反应），先引导授权
                if (Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    pendingTestPush = true;
                    Toast.makeText(MainActivity.this,
                            "请先允许通知权限，授予后将自动重试推送",
                            Toast.LENGTH_SHORT).show();
                    requestPermissions(new String[]{
                            "android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
                    return;
                }
                Toast.makeText(MainActivity.this, "正在推送天气通知…", Toast.LENGTH_SHORT).show();
                Intent svc = new Intent(MainActivity.this, SpeakService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
                else startService(svc);
            }
        });

        d.findViewById(R.id.btnAutoStart).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                openAutoStartSettings();
            }
        });

        d.show();
    }

    /** v9.87.3：设置弹窗返回处理——二级页动画回一级，一级页关闭 */
    private void handleSettingsBack(final Dialog d, final LinearLayout listPage,
                                    final LinearLayout detailPage) {
        if (settingsInDetail) {
            animatePageSwitch(listPage, detailPage, false, new Runnable() {
                @Override public void run() {
                    detailPage.removeAllViews();
                    settingsInDetail = false;
                }
            });
        } else {
            d.dismiss();
        }
    }

    // ============ 气象预警 ============

    /** v9.41：预警拉取（手动模式立即置加载中并强制重拉；请求序号防切城竞态；
     *  不可用状态点击预警条可直接重试）。
     *  v9.44：同城 1.5s 内双请求防抖（pickCity 立即拉 + startLoad 兜底）；
     *  恢复自动定位后 force 强制重拉并按新定位重置状态。 */
    private void loadAlerts(final String city) {
        final boolean manual = WeatherReporter.hasManualCity(this);
        String ck = city == null ? "" : city.trim();
        int sp = ck.indexOf(" · ");
        if (sp > 0) ck = ck.substring(0, sp);
        final String ckf = ck;
        final String prov = manual ? "" : regionProvince;
        final String dist = manual ? "" : regionDistrict;
        final long now = System.currentTimeMillis();
        final boolean force = alertForceNext;
        alertForceNext = false;
        if (manual && ckf.equals(lastAlertCity) && now - lastAlertTs < 1500) return;
        if (!manual && now - lastAlertTs < 120_000 && !force) return;
        lastAlertTs = now;
        lastAlertCity = ckf;

        final int seq = ++alertSeq;
        // 手动切城 / 恢复自动定位后：立即置「加载中」，避免旧「不可用 / 旧城市」状态残留
        if (manual || force) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    alertMode = 2;
                    alertBar.setVisibility(View.VISIBLE);
                    alertBar.setBackgroundResource(R.drawable.bg_alert_gray);
                    alertText.setSingleLine(false);
                    alertText.setText("正在获取「" + ckf + "」气象预警…");
                }
            });
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final WeatherApi.AlarmResult r = WeatherApi.fetchAlarms(ckf, prov, dist);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != alertSeq) return;   // 已被更新的切换请求取代
                        if (r == null) {
                            alertMode = -1;
                            alertResult = null;
                            alertBar.setVisibility(View.VISIBLE);
                            alertBar.setBackgroundResource(R.drawable.bg_alert_gray);
                            alertText.setSingleLine(true);
                            alertText.setText("预警服务暂不可用 · 点击重试");
                            return;
                        }
                        alertResult = r;
                        if (!r.local.isEmpty()) {
                            alertMode = 1;
                            alertBar.setVisibility(View.VISIBLE);
                            alertBar.setBackgroundResource(R.drawable.bg_alert);
                            // v9.41：等级色圆点 + 条数 + 首条标题（单行省略，保住「详情 ›」）
                            String first = r.local.get(0)[0] == null ? "" : r.local.get(0)[0];
                            android.text.SpannableStringBuilder sb =
                                    new android.text.SpannableStringBuilder();
                            sb.append("\u25CF ");   // ● 等级色点
                            sb.setSpan(new android.text.style.ForegroundColorSpan(
                                            alertLevelColor(first)),
                                    0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            sb.append(String.valueOf(r.local.size())).append(" 条气象预警 · ").append(first);
                            alertText.setSingleLine(true);
                            alertText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                            alertText.setText(sb);
                        } else {
                            alertMode = 0;
                            alertBar.setVisibility(View.VISIBLE);
                            alertBar.setBackgroundResource(R.drawable.bg_alert_ok);
                            alertText.setSingleLine(false);
                            alertText.setText("\u2713 本地区暂无气象预警");
                        }
                    }
                });
            }
        }).start();
    }

    /** 当前展示城市名（手动优先，否则自动定位城市） */
    private String currentCityName() {
        if (WeatherReporter.hasManualCity(this)) {
            return WeatherReporter.manualCityName(this);
        }
        return cityName != null ? cityName : "";
    }

    /** v9.41：降雨图入口随定位模式显隐（高度折叠 + 淡入淡出，状态未变不重复播放） */
    private void applyMapCard(boolean show) {
        if (show == mapCardShown) return;
        mapCardShown = show;
        final View c = mapCardView;
        if (c == null) return;
        if (show) {
            c.setVisibility(View.VISIBLE);
            c.setAlpha(0f);
            c.measure(View.MeasureSpec.makeMeasureSpec(
                            c.getWidth() > 0 ? c.getWidth()
                                    : getResources().getDisplayMetrics().widthPixels,
                            View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            final int target = c.getMeasuredHeight();
            final ViewGroup.LayoutParams lp = c.getLayoutParams();
            lp.height = 0;
            c.requestLayout();
            c.post(new Runnable() {
                @Override
                public void run() {
                    ValueAnimator va = ValueAnimator.ofInt(0, target);
                    va.setDuration(340);
                    va.setInterpolator(new DecelerateInterpolator());
                    va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator a) {
                            lp.height = (Integer) a.getAnimatedValue();
                            c.requestLayout();
                        }
                    });
                    va.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator an) {
                            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                            c.requestLayout();
                        }
                    });
                    va.start();
                    c.animate().alpha(1f).setDuration(340).start();
                }
            });
        } else {
            final int start = c.getHeight();
            final ViewGroup.LayoutParams lp = c.getLayoutParams();
            if (start <= 0) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                c.setVisibility(View.GONE);
                c.requestLayout();
                return;
            }
            ValueAnimator va = ValueAnimator.ofInt(start, 0);
            va.setDuration(300);
            va.setInterpolator(new AccelerateInterpolator());
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    lp.height = (Integer) a.getAnimatedValue();
                    c.requestLayout();
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator an) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    c.setVisibility(View.GONE);
                    c.requestLayout();
                }
            });
            va.start();
            c.animate().alpha(0f).setDuration(300).start();
        }
    }

    /** v9.39：日落检测。已日落 -> 日出日落信息居左半边，右半边显示月相（翻转动画切换）。
     *  未日落（或时间数据缺失）-> 保持全宽日出日落卡。
     *  v9.86：修复夜间判断——旧逻辑只比较「今天日落」时刻，凌晨（0 点~日出前）被误判为
     *  白天，月相不显示；且日落时刻按设备时区解析，跨时区判断错误。现改用 API ISO 时间
     *  （城市当地时间）+ utc_offset_seconds 换算成绝对时刻，凌晨（now<日出）或傍晚后
     *  （now>=日落）均视为夜间。 */
    private void updateMoonCard(JSONObject daily, JSONObject root) {
        boolean night = false;
        try {
            // v9.87：utc_offset_seconds 在 JSON 根对象上（不在 daily 内）。
            // 旧代码从 daily 取恒为 0 → 东八区白天被误判为夜间 → 月相白天不消失。
            // 取不到时兜底设备时区偏移（手动查跨时区城市时才可能偏差）。
            long offMs = 0;
            try { offMs = root.getLong("utc_offset_seconds") * 1000L; }
            catch (Exception e) {
                offMs = java.util.TimeZone.getDefault()
                        .getOffset(System.currentTimeMillis());
            }
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm", java.util.Locale.US);
            fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            // 统一在「城市当地时间」坐标系比较：now 转成城市钟表读数，
            // sunrise/sunset 字符串即城市钟表读数（按 UTC 字面量解析）
            long now = System.currentTimeMillis() + offMs;
            long tSunrise = fmt.parse(
                    daily.getJSONArray("sunrise").getString(0)).getTime();
            long tSunset = fmt.parse(
                    daily.getJSONArray("sunset").getString(0)).getTime();
            night = now < tSunrise || now >= tSunset;
        } catch (Exception ignored) { }

        if (night) {
            // v9.87：月相相位改用 Meeus 简化新月算法（精度约 1 小时）。
            // 旧公式以 2000-01-06 新月为基准按平均朔望月外推 26 年，累积漂移可达 ±1 天，
            // 月相图与真实相差数天。新算法直接定位当前朔望月，长期稳定。
            double jd = 2440587.5 + System.currentTimeMillis() / 86400000.0;
            double k = Math.round((jd - 2451550.09766) / 29.530588861);
            double T = k / 1236.85;
            double jdNew = 2451550.09766 + 29.530588861 * k
                    + 0.00015437 * T * T - 0.000000150 * T * T * T
                    + 0.00000000073 * T * T * T * T;
            double age = jd - jdNew;
            if (age < 0) age += 29.530588861;
            double f = age / 29.530588861;
            // v9.87：月相三色按主题自适应，避免暗面与卡片底色混在一起
            final boolean darkM = Theme.isDark(this);
            moonView.setColors(darkM ? 0xFF4A5568 : 0xFFD3DAE3,
                    darkM ? 0xFFF4E8C8 : 0xFFF0D088,
                    darkM ? 0x667F8CA3 : 0x445A6B85);
            moonView.setPhase(f);
            moonText.setText(moonName(f) + " · 月龄 " + String.format(Locale.US, "%.1f", age) + "天");
        }

        if (night != moonShown) {
            moonShown = night;
            moonHalf.setVisibility(night ? View.VISIBLE : View.GONE);
            // 翻转动画：内容就位后从侧翻回正面
            sunMoonRow.setRotationY(90f);
            sunMoonRow.setAlpha(0.6f);
            sunMoonRow.animate().rotationY(0f).alpha(1f).setDuration(460)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    /** 月相八分名（f=0 新月 … 0.5 满月 …） */
    private String moonName(double f) {
        if (f < 0.03125 || f >= 0.96875) return "新月";
        if (f < 0.21875) return "娥眉月";
        if (f < 0.28125) return "上弦月";
        if (f < 0.46875) return "盈凸月";
        if (f < 0.53125) return "满月";
        if (f < 0.71875) return "亏凸月";
        if (f < 0.78125) return "下弦月";
        return "残月";
    }

    /** v9.39：预警条点击 -> 自绘弹出窗口（底部升起 + 遮罩，深浅色适配，图标随主页已加载） */
    private void openAlertList() {
        final android.app.Dialog dlg = new android.app.Dialog(this);
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.view.Window win = dlg.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        final boolean manual = WeatherReporter.hasManualCity(this);
        String city = manual ? WeatherReporter.manualCityName(this)
                : (cityName != null ? cityName : "");
        if (city == null) city = "";
        // 区县候选名「宝安区 · 深圳市」截断为「宝安区」用于匹配
        String ck = city.trim();
        int sp = ck.indexOf(" · ");
        if (sp > 0) ck = ck.substring(0, sp);
        final String ckf = ck;
        final String prov = manual ? "" : (regionProvince == null ? "" : regionProvince);
        final String dist = manual ? "" : (regionDistrict == null ? "" : regionDistrict);

        final int txtColor = Theme.isDark(this) ? 0xFFE8EAED : 0xFF1C1F23;
        final int subColor = Theme.isDark(this) ? 0xFF9AA0A8 : 0xFF6B7280;
        final int bg = Theme.isDark(this) ? 0xFF17181C : 0xFFF2F4F8;
        final int card = Theme.isDark(this) ? 0xFF1B1D22 : 0xFFFFFFFF;

        // 全屏根：上部留空可点按关闭，底部为自绘面板
        LinearLayout dlgRoot = new LinearLayout(this);
        dlgRoot.setOrientation(LinearLayout.VERTICAL);
        dlgRoot.setGravity(Gravity.BOTTOM);
        dlgRoot.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(bg);
        panelBg.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), dp(22), dp(22), dp(22), dp(22)});
        panel.setBackground(panelBg);
        panel.setPadding(dp(20), dp(18), dp(20), dp(28));
        panel.setOnClickListener(null);

        // 顶行：标题 + 城市名 + 关闭
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tBox = new LinearLayout(this);
        tBox.setOrientation(LinearLayout.VERTICAL);
        tBox.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView t1 = new TextView(this);
        t1.setText("气象预警");
        t1.setTextColor(txtColor);
        t1.setTextSize(19f);
        t1.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tBox.addView(t1);
        final TextView t2 = new TextView(this);
        t2.setText(ckf + " · 正在加载…");
        t2.setTextColor(subColor);
        t2.setTextSize(12f);
        tBox.addView(t2);
        top.addView(tBox);
        TextView close = new TextView(this);
        close.setTypeface(Fonts.icons());
        close.setText("\uE5CD");   // close
        close.setTextSize(22f);
        close.setTextColor(subColor);
        close.setPadding(dp(8), dp(4), dp(2), dp(4));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); }
        });
        top.addView(close);
        panel.addView(top);

        // 空态 / 失败态 / 列表
        final TextView emptyBox = new TextView(this);
        emptyBox.setGravity(Gravity.CENTER);
        emptyBox.setTextColor(subColor);
        emptyBox.setTextSize(15f);
        emptyBox.setPadding(0, dp(56), 0, dp(40));
        emptyBox.setVisibility(View.GONE);
        panel.addView(emptyBox);

        final TextView failBox = new TextView(this);
        failBox.setGravity(Gravity.CENTER);
        failBox.setTextColor(subColor);
        failBox.setTextSize(15f);
        failBox.setPadding(0, dp(56), 0, dp(40));
        failBox.setVisibility(View.GONE);
        panel.addView(failBox);

        final ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        final LinearLayout listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(0, dp(8), 0, dp(4));
        sv.addView(listBox);
        panel.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部刷新按钮
        TextView btn = new TextView(this);
        btn.setGravity(Gravity.CENTER);
        btn.setText("点此刷新");
        btn.setTextColor(Theme.onPrimaryContainer(this));
        btn.setTextSize(14f);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Theme.primaryContainer(this));
        btnBg.setCornerRadius(dp(16));
        btn.setBackground(btnBg);
        btn.setPadding(0, dp(12), 0, dp(12));
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                listBox.removeAllViews();
                emptyBox.setVisibility(View.GONE);
                failBox.setVisibility(View.GONE);
                t2.setText(ckf + " · 正在加载…");
                fetchAlertsTo(dlg, listBox, emptyBox, failBox, t2, ckf, prov, dist, txtColor, subColor, card);
            }
        });
        panel.addView(btn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dlgRoot.addView(panel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        dlg.setContentView(dlgRoot);
        Fonts.apply(dlgRoot);   // v9.57：强制 Google Sans
        dlg.show();
        // 弹出动画：遮罩淡入 + 面板底部升起
        dlgRoot.setAlpha(0f);
        panel.setTranslationY(dp(120));
        dlgRoot.animate().alpha(1f).setDuration(200).start();
        panel.animate().translationY(0).setDuration(320)
                .setInterpolator(new DecelerateInterpolator()).start();

        fetchAlertsTo(dlg, listBox, emptyBox, failBox, t2, ckf, prov, dist, txtColor, subColor, card);
    }

    /** 弹窗内后台拉取并渲染预警列表（标题/等级色条/时间） */
    private void fetchAlertsTo(final android.app.Dialog dlg, final LinearLayout listBox,
                               final TextView emptyBox, final TextView failBox, final TextView t2,
                               final String ck, final String prov, final String dist,
                               final int txtColor, final int subColor, final int card) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final WeatherApi.AlarmResult r = WeatherApi.fetchAlarms(ck, prov, dist);
                final boolean dead = dlg == null || !dlg.isShowing();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (dead) return;
                        if (r == null) {
                            t2.setText(ck + " · 预警服务暂不可用");
                            failBox.setText("\uE002  预警服务暂不可用\n请检查网络后点下方按钮重试");
                            failBox.setVisibility(View.VISIBLE);
                            return;
                        }
                        if (r.local.isEmpty()) {
                            t2.setText(ck + " · 暂无气象预警");
                            emptyBox.setText("\uE86C  本地区暂无气象预警");
                            emptyBox.setVisibility(View.VISIBLE);
                            return;
                        }
                        t2.setText(ck + " · " + r.local.size() + " 条预警");
                        renderAlertCards(listBox, r.local, txtColor, subColor, card);
                    }
                });
            }
        }).start();
    }

    /** 自绘预警卡片：左侧等级色条 + 标题 + 时间 */
    private void renderAlertCards(LinearLayout listBox, List<String[]> list,
                                  int txtColor, int subColor, int card) {
        for (int i = 0; i < list.size(); i++) {
            String[] a = list.get(i);
            String t = a[0] == null ? "" : a[0];
            String tm = a.length > 1 && a[1] != null ? a[1] : "";
            int lc = alertLevelColor(t);

            LinearLayout cardBox = new LinearLayout(this);
            cardBox.setOrientation(LinearLayout.HORIZONTAL);
            GradientDrawable cd = new GradientDrawable();
            cd.setColor(card);
            cd.setCornerRadius(dp(14));
            cardBox.setBackground(cd);
            cardBox.setPadding(dp(4), 0, dp(14), 0);

            View stripe = new View(this);
            GradientDrawable st = new GradientDrawable();
            st.setColor(lc);
            st.setCornerRadii(new float[]{dp(14), dp(14), 0, 0, 0, 0, dp(14), dp(14)});
            stripe.setBackground(st);
            cardBox.addView(stripe, new LinearLayout.LayoutParams(
                    dp(5), LinearLayout.LayoutParams.MATCH_PARENT));

            LinearLayout txtBox = new LinearLayout(this);
            txtBox.setOrientation(LinearLayout.VERTICAL);
            txtBox.setPadding(dp(12), dp(12), 0, dp(12));

            TextView warn = new TextView(this);
            warn.setText("\uE002  " + t);   // warning 图标（主页已加载图标字体）
            warn.setTextColor(txtColor);
            warn.setTextSize(15f);
            warn.setLineSpacing(dp(3), 1f);
            txtBox.addView(warn);

            TextView time = new TextView(this);
            time.setText(tm);
            time.setTextColor(subColor);
            time.setTextSize(12f);
            time.setPadding(0, dp(6), 0, 0);
            txtBox.addView(time);

            cardBox.addView(txtBox, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = i == 0 ? 0 : dp(10);
            listBox.addView(cardBox, lp);
        }
    }

    /** 预警等级色：红/橙/黄/蓝，默认灰 */
    private int alertLevelColor(String title) {
        if (title.contains("红色")) return 0xFFE53935;
        if (title.contains("橙色")) return 0xFFFB8C00;
        if (title.contains("黄色")) return 0xFFF9A825;
        if (title.contains("蓝色")) return 0xFF1E88E5;
        return Theme.isDark(this) ? 0xFF9AA0A8 : 0xFF8A9099;
    }

    /** 刷新主页面通知卡片副标题 */
    private void refreshReportCard() {
        SharedPreferences sp = getSharedPreferences(WeatherReporter.PREFS, MODE_PRIVATE);
        boolean on = sp.getBoolean(WeatherReporter.KEY_ENABLED, false);
        int h = sp.getInt(WeatherReporter.KEY_HOUR, 8);
        int m = sp.getInt(WeatherReporter.KEY_MINUTE, 0);
        TextView sub = (TextView) findViewById(R.id.reportSub);
        sub.setText(on ? "每天 " + String.format(Locale.US, "%02d:%02d", h, m)
                + " 天气通知 · 已开启"
                : "每天准点推送天气通知 · 点击设置");
    }

    /** 跳转厂商自启动管理页，失败回退应用详情页 */
    private void openAutoStartSettings() {
        // v9.75：覆盖主流 ROM 自启动管理入口（MIUI/澎湃 / 鸿蒙 / EMUI / MagicOS /
        // ColorOS / OPPO / vivo / iQOO / OriginOS / 三星 / Flyme）
        String[][] targets = {
                {"com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"},
                {"com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
                {"com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"},
                {"com.oplus.safecenter",
                        "com.oplus.safecenter.startupapp.StartupAppListActivity"},
                {"com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"},
                {"com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
                {"com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
                {"com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"},
                {"com.meizu.safe",
                        "com.meizu.safe.permission.SmartBGActivity"},
        };
        for (String[] t : targets) {
            try {
                Intent it = new Intent();
                it.setComponent(new ComponentName(t[0], t[1]));
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(it);
                Toast.makeText(this, "请在自启动列表中允许本应用",
                        Toast.LENGTH_LONG).show();
                return;
            } catch (Exception ignored) { }
        }
        try {
            Intent it = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(it);
            Toast.makeText(this, "已打开应用详情，请检查「自启动」相关选项",
                    Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
            Toast.makeText(this, "请到系统设置中手动允许本应用自启动",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ============ v9.70：首次启动权限引导（自绘 UI + 状态检测） ============

    /**
     * 自绘权限引导弹窗：实时检测定位/通知/无限制后台权限状态并展示，
     * v9.73 起逐项授权——每行独立可点，各自发起对应授权，取消一键批量授权。
     */
    private void showPermissionGuide() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            d.getWindow().setDimAmount(0.35f);
        }
        final boolean dark = Theme.isDark(this);
        final int bg = dark ? 0xFF1B232E : 0xFFFFFFFF;
        final int titleC = dark ? 0xFFFFFFFF : 0xFF1F2A36;
        final int subC = dark ? 0x88FFFFFF : 0xFF66717E;
        final int rowBg = dark ? 0xFF222C3A : 0xFFF4F6F9;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        root.setPadding(pad, pad, pad, pad);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(20));
        root.setBackground(gd);

        TextView title = new TextView(this);
        title.setText("完善权限，天气服务更可靠");
        title.setTextColor(titleC);
        title.setTextSize(18);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("点击各行即可授权");
        sub.setTextColor(subC);
        sub.setTextSize(12);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(6);
        sub.setLayoutParams(slp);
        root.addView(sub);

        // v9.73：逐项授权——每一行都是独立入口，点击未开启项即发起对应授权
        // 图标统一为 Material Icons 字形（v9.73 规范）
        permStates = new TextView[4];
        addPermRow(root, "\uE55B", "定位权限", "提供精确天气与本地预警", 0, rowBg, titleC, subC, dark);
        addPermRow(root, "\uE7F4", "通知权限", "天气简报与预警提醒", 1, rowBg, titleC, subC, dark);
        addPermRow(root, "\uE8B8", "自启动权限", "后台保活，点击前往系统设置开启", 2, rowBg, titleC, subC, dark);
        addPermRow(root, "\uE19C", "无限制后台", "免省电限制，点击前往系统设置", 3, rowBg, titleC, subC, dark);

        // 按钮行：稍后再说（outlined）+ 完成（filled）
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(20);
        btns.setLayoutParams(blp);

        TextView later = btn("稍后再说", dark ? 0xFF9AA0A8 : 0xFF6B7280,
                0x00000000, dark ? 0xFF2A3644 : 0xFFE5EAF0);
        later.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { d.dismiss(); }
        });
        btns.addView(later);

        TextView done = btn("完成", dark ? 0xFF1F1F1F : 0xFFFFFFFF,
                dark ? 0xFF8AB4F8 : 0xFF2F6FEB, 0x00000000);
        LinearLayout.LayoutParams dlp2 = (LinearLayout.LayoutParams) done.getLayoutParams();
        dlp2.leftMargin = dp(10);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { d.dismiss(); }
        });
        btns.addView(done);
        root.addView(btns);

        permGuide = d;
        refreshPermStates();   // v9.73：需在 permGuide 赋值后调用，首次展示即正确显示各项状态
        d.setContentView(root);
        Fonts.apply(root);   // v9.73：代码构建的弹窗也强制 Google Sans
        d.show();
    }

    /** v9.73：单项权限当前是否已开启（自启动无法程序化检测，恒 false） */
    private boolean permOk(int type) {
        switch (type) {
            case 0:
                return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
            case 1:
                return Build.VERSION.SDK_INT < 33
                        || checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        == PackageManager.PERMISSION_GRANTED;
            case 2:
                return false;
            case 3:
                // v9.77：不检测，始终作为一键入口跳转系统设置
                return false;
        }
        return false;
    }

    /** v9.73：刷新权限引导弹窗内 4 行状态（首次展示/授权返回/回前台时调用） */
    private void refreshPermStates() {
        if (permStates == null) return;   // 弹窗未构建过则不处理
        boolean dark = Theme.isDark(this);
        for (int i = 0; i < permStates.length; i++) {
            TextView st = permStates[i];
            if (st == null) continue;
            if (i == 2 || i == 3) {
                // v9.77：自启动/无限制后台无公开检测接口，不做状态检测，
                // 仅作一键入口提示，点击整行直达系统设置
                st.setText("去设置");
                st.setTextColor(dark ? 0xFF8AB4F8 : 0xFF2F6FEB);
            } else {
                boolean ok = permOk(i);
                st.setText(ok ? "已开启" : "未开启");
                st.setTextColor(ok ? 0xFF4CAF7D : (dark ? 0xFFF2A65A : 0xFFE8833A));
            }
        }
    }

    /** v9.73：发起单项授权（点击未开启的权限行时调用） */
    private void grantPerm(int type) {
        switch (type) {
            case 0:   // 定位
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                break;
            case 1:   // 通知（v9.73 修订：targetSdk 33，Android 13+ 可真实请求）
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(
                            new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
                } else {
                    Toast.makeText(this,
                            "当前系统版本通知权限默认开启，无需申请",
                            Toast.LENGTH_SHORT).show();
                    refreshPermStates();
                }
                break;
            case 2:   // 自启动：跳系统设置页
                openAutoStartSettings();
                break;
            case 3:   // 无限制后台（v9.75：优先专用弹窗，无则跳列表页，再失败给指引）
                try {
                    Intent bi = new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + getPackageName()));
                    if (bi.resolveActivity(getPackageManager()) != null) {
                        startActivity(bi);
                        Toast.makeText(this,
                                "请在系统弹窗中允许「无限制后台」",
                                Toast.LENGTH_SHORT).show();
                        break;
                    }
                } catch (Exception ignored) { }
                try {
                    Intent li = new Intent(
                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    if (li.resolveActivity(getPackageManager()) != null) {
                        startActivity(li);
                        Toast.makeText(this,
                                "请在列表中找到本应用并允许「无限制后台」",
                                Toast.LENGTH_LONG).show();
                        break;
                    }
                } catch (Exception ignored) { }
                Toast.makeText(this,
                        "请在系统设置 → 电池/省电中，允许本应用无限制后台",
                        Toast.LENGTH_LONG).show();
                break;
        }
    }

    /** 权限引导弹窗中的单行状态项（v9.73：整行可点击、逐项授权） */
    private void addPermRow(LinearLayout root, String iconChar, String name, String desc,
                            int type, int rowBg, int titleC, int subC,
                            boolean dark) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable rg = new GradientDrawable();
        rg.setColor(rowBg);
        rg.setCornerRadius(dp(12));
        row.setBackground(rg);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(10);
        row.setLayoutParams(rlp);

        TextView ic = new TextView(this);
        ic.setText(iconChar);
        ic.setTextSize(18);
        ic.setTypeface(Fonts.icons());   // v9.73：Material Icons 字形替代 emoji
        row.addView(ic);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mlp.leftMargin = dp(12);
        mid.setLayoutParams(mlp);
        TextView n = new TextView(this);
        n.setText(name);
        n.setTextColor(titleC);
        n.setTextSize(15);
        n.setTypeface(n.getTypeface(), android.graphics.Typeface.BOLD);
        mid.addView(n);
        TextView ds = new TextView(this);
        ds.setText(desc);
        ds.setTextColor(subC);
        ds.setTextSize(11);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(2);
        ds.setLayoutParams(dlp);
        mid.addView(ds);
        row.addView(mid);

        TextView st = new TextView(this);
        st.setTextSize(12);
        st.setTypeface(st.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(st);
        permStates[type] = st;

        // v9.73：整行可点击——未开启则发起对应授权，已开启则轻提示
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (permOk(type)) {
                    String n2 = type == 0 ? "定位权限"
                            : type == 1 ? "通知权限"
                            : type == 2 ? "自启动权限" : "无限制后台";
                    Toast.makeText(MainActivity.this, n2 + "已开启，无需重复授权",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                grantPerm(type);
            }
        });
        root.addView(row);
    }

    /** 权限引导弹窗按钮（圆角实心/描边） */
    private TextView btn(String text, int textColor, int fill, int stroke) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(14);
        b.setTypeface(b.getTypeface(), android.graphics.Typeface.BOLD);
        b.setTextColor(textColor);
        b.setPadding(dp(14), dp(11), dp(14), dp(11));
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(12));
        if (stroke != 0x00000000) g.setStroke(dp(1), stroke);
        b.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        b.setLayoutParams(lp);
        return b;
    }

    // ============ v9.27：查询其他城市 ============

    /** v9.28：对话框统一圆角背景（跟随深/浅主题） */
    private void styleDialog(android.app.AlertDialog d) {
        try {
            d.getWindow().setBackgroundDrawableResource(
                    Theme.isDark(this) ? R.drawable.bg_dialog_dark : R.drawable.bg_dialog_light);
        } catch (Exception ignored) { }
    }

    /** v9.29：自研城市搜索对话框——输入框/按钮/颜色全部自绘，深色浅色模式全适配 */
    private void showCityDialog() {
        final boolean dark = Theme.isDark(this);
        final int txtColor = dark ? 0xFFE8EAED : 0xFF1C1F23;
        final int subColor = dark ? 0xFF9AA0A8 : 0xFF6B7280;
        final int borderColor = dark ? 0xFF3A3E47 : 0xFFD0D4DB;
        final int focusColor = Theme.accent(this);
        final int panelBg = Theme.surfaceContainerHigh(this);
        final int boxBg = dark ? 0xFF1B1D22 : 0xFFFFFFFF;

        // ---- 输入框（无系统样式残留） ----
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16f);
        input.setTextColor(txtColor);
        input.setHintTextColor(subColor);
        input.setHint("搜索国内城市，如：深圳 / 宝安区");
        input.setBackgroundColor(0x00000000);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        try {
            java.lang.reflect.Field f =
                    android.widget.TextView.class.getDeclaredField("mCursorDrawableRes");
            f.setAccessible(true);
            f.setInt(input, R.drawable.bg_cursor);
        } catch (Exception ignored) { }

        // ---- 输入框容器：圆角 + 描边，聚焦高亮 ----
        final android.graphics.drawable.GradientDrawable boxDraw =
                new android.graphics.drawable.GradientDrawable();
        boxDraw.setColor(boxBg);
        boxDraw.setCornerRadius(dp(14));
        boxDraw.setStroke(dp(1), borderColor);

        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(boxDraw);
        row.setPadding(dp(14), 0, dp(4), 0);
        row.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1f));

        // 清空按钮（Material ✕，有输入才显示）
        final TextView clearBtn = new TextView(this);
        clearBtn.setTypeface(Fonts.icons());
        clearBtn.setText("\uE5C9");
        clearBtn.setTextColor(subColor);
        clearBtn.setTextSize(18f);
        clearBtn.setPadding(dp(8), dp(10), dp(4), dp(10));
        clearBtn.setVisibility(View.GONE);
        row.addView(clearBtn);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.setText("");
                input.requestFocus();
            }
        });
        // ---- v9.31：关键词候选区（本地区县+市/省实时联想，每页 5 个可翻页） ----
        final Dialog d = new Dialog(this);
        final int PER = 5;
        final int MAX = 15;
        final int disabledColor = dark ? 0x33FFFFFF : 0x33000000;
        final java.util.List<String[]> cands = new java.util.ArrayList<String[]>();
        final int[] page = { 0 };

        final TextView statusText = new TextView(this);
        statusText.setTextColor(subColor);
        statusText.setTextSize(12.5f);
        statusText.setPadding(0, dp(8), 0, 0);
        statusText.setVisibility(View.GONE);

        final LinearLayout candBox = new LinearLayout(this);
        candBox.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout pageRow = new LinearLayout(this);
        pageRow.setOrientation(LinearLayout.HORIZONTAL);
        pageRow.setGravity(Gravity.CENTER);
        pageRow.setPadding(0, dp(6), 0, 0);
        pageRow.setVisibility(View.GONE);
        final TextView prevBtn = new TextView(this);
        prevBtn.setTypeface(Fonts.icons());
        prevBtn.setText("\uE5CB");
        prevBtn.setTextSize(20f);
        prevBtn.setPadding(dp(20), dp(2), dp(20), dp(2));
        final TextView pageLabel = new TextView(this);
        pageLabel.setTextColor(subColor);
        pageLabel.setTextSize(13f);
        final TextView nextBtn = new TextView(this);
        nextBtn.setTypeface(Fonts.icons());
        nextBtn.setText("\uE5CC");
        nextBtn.setTextSize(20f);
        nextBtn.setPadding(dp(20), dp(2), dp(20), dp(2));
        pageRow.addView(prevBtn);
        pageRow.addView(pageLabel);
        pageRow.addView(nextBtn);

        final Runnable updateCands = new Runnable() {
            @Override
            public void run() {
                candBox.removeAllViews();
                if (cands.isEmpty()) {
                    pageRow.setVisibility(View.GONE);
                    return;
                }
                int pages = (cands.size() + PER - 1) / PER;
                if (page[0] >= pages) page[0] = pages - 1;
                if (page[0] < 0) page[0] = 0;
                int from = page[0] * PER;
                int to = Math.min(from + PER, cands.size());
                for (int i = from; i < to; i++) {
                    final String[] c = cands.get(i);
                    TextView item = new TextView(MainActivity.this);
                    item.setText(c[0]);
                    item.setTextColor(txtColor);
                    item.setTextSize(14.5f);
                    item.setPadding(dp(14), dp(9), dp(14), dp(9));
                    android.graphics.drawable.GradientDrawable itBg =
                            new android.graphics.drawable.GradientDrawable();
                    itBg.setColor(boxBg);
                    itBg.setCornerRadius(dp(10));
                    item.setBackground(itBg);
                    item.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            d.dismiss();
                            pickCand(c);
                        }
                    });
                    android.widget.LinearLayout.LayoutParams lp =
                            new android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = dp(5);
                    candBox.addView(item, lp);
                }
                if (pages > 1) {
                    pageRow.setVisibility(View.VISIBLE);
                    pageLabel.setText((page[0] + 1) + " / " + pages);
                    prevBtn.setTextColor(page[0] > 0 ? txtColor : disabledColor);
                    nextBtn.setTextColor(page[0] < pages - 1 ? txtColor : disabledColor);
                } else {
                    pageRow.setVisibility(View.GONE);
                }
            }
        };
        prevBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page[0] > 0) { page[0]--; updateCands.run(); }
            }
        });
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pages = (cands.size() + PER - 1) / PER;
                if (page[0] < pages - 1) { page[0]++; updateCands.run(); }
            }
        });

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                clearBtn.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                cands.clear();
                page[0] = 0;
                String q = s.toString().trim();
                if (q.isEmpty()) { updateCands.run(); return; }
                // v9.51：级联搜索（零网络）——第一条=当前输入实体，其后=下一级区划，
                // 全部候选统一「省 · 市 · 区」显示，每页 5 条
                cands.addAll(PlaceSearch.search(q));
                final String nq = normForSort(q);
                if (q.length() <= 1) {
                    // 单字符只保留前缀命中（"深"->"深圳"），避免上千候选
                    if (nq.isEmpty()) {
                        cands.clear();
                    } else {
                        for (int i = cands.size() - 1; i >= 0; i--) {
                            if (!normForSort(cands.get(i)[0]).startsWith(nq)) cands.remove(i);
                        }
                    }
                } else {
                    java.util.Collections.sort(cands, new java.util.Comparator<String[]>() {
                        @Override
                        public int compare(String[] x, String[] y) {
                            boolean ex = normForSort(x[0]).equals(nq);
                            boolean ey = normForSort(y[0]).equals(nq);
                            if (ex != ey) return ex ? -1 : 1;
                            return Integer.compare(x[0].length(), y[0].length());
                        }
                    });
                    if (cands.size() > MAX) {
                        cands.subList(MAX, cands.size()).clear();
                    }
                }
                updateCands.run();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) { }
        });
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                boxDraw.setStroke(dp(has ? 2 : 1), has ? focusColor : borderColor);
            }
        });

        // ---- 面板容器 ----
        final android.graphics.drawable.GradientDrawable panel =
                new android.graphics.drawable.GradientDrawable();
        panel.setColor(panelBg);
        panel.setCornerRadius(dp(28));
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(10));
        box.setBackground(panel);

        TextView title = new TextView(this);
        title.setText("查询其他城市");
        title.setTextColor(txtColor);
        title.setTextSize(19f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(title);

        // ---- v9.34：API 限制声明（暂时只支持国内城市） ----
        final TextView limitNote = new TextView(this);
        limitNote.setText("由于 API 限制，暂只支持国内城市查询");
        limitNote.setTextColor(subColor);
        limitNote.setTextSize(12.5f);
        limitNote.setPadding(0, dp(6), 0, dp(14));
        box.addView(limitNote);

        final TextView sub = new TextView(this);
        sub.setText("支持国内区县，如：宝安区 / 福田区");
        sub.setTextColor(subColor);
        sub.setTextSize(12.5f);
        sub.setPadding(0, 0, 0, dp(10));
        box.addView(sub);

        if (WeatherReporter.hasManualCity(this)) {
            TextView restore = new TextView(this);
            restore.setText("当前为手动城市 · 点此恢复自动定位");
            restore.setTextColor(focusColor);
            restore.setTextSize(13f);
            restore.setPadding(0, 0, 0, dp(10));
            restore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    WeatherReporter.clearManualCity(MainActivity.this);
                    Toast.makeText(MainActivity.this,
                            "已恢复自动定位", Toast.LENGTH_SHORT).show();
                    // v9.44：切换地区 -> 按设置中的定位方式重新定位并拉取预警
                    alertForceNext = true;
                    startLoad(true);
                }
            });
            box.addView(restore);
        }

        box.addView(row);

        box.addView(statusText);
        box.addView(candBox);
        box.addView(pageRow);
        // v9.43：去除「取消/查询」按钮行，输入即实时候选，点选即生效

        // ---- 对话框本体 ----
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(box);
        Fonts.apply(box);   // v9.57：强制 Google Sans
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            d.getWindow().setDimAmount(0.3f);
            WindowManager.LayoutParams lp = d.getWindow().getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            d.getWindow().setAttributes(lp);
        }
        // v9.43：候选实时显示，回车仅收起键盘，不再弹「未收录」提示
        input.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId,
                                          android.view.KeyEvent ev) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager)
                                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });
        input.requestFocus();
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        d.show();
    }

    /**
     * 城市搜索（v9.34）：仅本地（区县 2991 -> 市/省 390），不联网；
     * 因 API 限制暂不支持国外城市，未收录即提示。搜索对话框已改为实时候选+分页，
     * 本方法保留供其他入口调用。
     */
    private void searchCity(final String q) {
        final java.util.List<DistrictTable.Hit> dists = DistrictTable.lookup(q);
        if (!dists.isEmpty()) {
            java.util.List<DistrictTable.Hit> exact = new java.util.ArrayList<>();
            String key = q.trim();
            for (String suf : new String[]{"自治县", "自治旗", "林区", "矿区",
                    "新区", "特区", "区", "县", "市", "旗"}) {
                if (key.endsWith(suf)) { key = key.substring(0, key.length() - suf.length()); break; }
            }
            for (DistrictTable.Hit h : dists) {
                if (normForSort(h.name).startsWith(key) || h.name.contains(key)) exact.add(h);
            }
            java.util.List<DistrictTable.Hit> use = exact.isEmpty() ? dists : exact;
            if (use.size() > 8) use = use.subList(0, 8);
            if (use.size() == 1) {
                pickDistrict(use.get(0));
            } else {
                showDistrictPick(use);
            }
            return;
        }
        double[] hit = CityTable.lookup(null, q);
        if (hit != null) {
            JSONObject r = new JSONObject();
            try {
                r.put("name", q.endsWith("市") ? q : q + "市");
                r.put("latitude", hit[0]);
                r.put("longitude", hit[1]);
                r.put("admin1", "");
            } catch (Exception ignored) { }
            pickCity(r);
            return;
        }
        Toast.makeText(this, "仅支持国内城市查询（API 限制），未收录「" + q + "」",
                Toast.LENGTH_SHORT).show();
    }

    /** v9.30：区县多候选自绘列表（与查询框同风格，深浅主题适配） */
    private void showDistrictPick(final java.util.List<DistrictTable.Hit> hits) {
        final boolean dark = Theme.isDark(this);
        final int txtColor = dark ? 0xFFE8EAED : 0xFF1C1F23;
        final int subColor = dark ? 0xFF9AA0A8 : 0xFF6B7280;
        final int focusColor = Theme.accent(this);
        final int panelBg = Theme.surfaceContainerHigh(this);
        final int itemBg = dark ? 0xFF1B1D22 : 0xFFFFFFFF;

        final android.graphics.drawable.GradientDrawable panel =
                new android.graphics.drawable.GradientDrawable();
        panel.setColor(panelBg);
        panel.setCornerRadius(dp(28));

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(10));
        box.setBackground(panel);

        TextView title = new TextView(this);
        title.setText("找到多个同名区县");
        title.setTextColor(txtColor);
        title.setTextSize(18f);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText("请选择要查看天气的地区：");
        sub.setTextColor(subColor);
        sub.setTextSize(12.5f);
        sub.setPadding(0, dp(4), 0, dp(10));
        box.addView(sub);

        final Dialog dlg = new Dialog(this);

        for (final DistrictTable.Hit h : hits) {
            TextView item = new TextView(this);
            item.setText(h.name);
            item.setTextColor(txtColor);
            item.setTextSize(15f);
            item.setPadding(dp(14), dp(11), dp(14), dp(11));
            android.graphics.drawable.GradientDrawable itBg =
                    new android.graphics.drawable.GradientDrawable();
            itBg.setColor(itemBg);
            itBg.setCornerRadius(dp(10));
            item.setBackground(itBg);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            box.addView(item, lp);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dlg.dismiss();
                    pickDistrict(h);
                }
            });
        }

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(subColor);
        cancel.setTextSize(15f);
        cancel.setGravity(Gravity.END);
        cancel.setPadding(0, dp(10), dp(4), dp(6));
        box.addView(cancel);

        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(box);
        Fonts.apply(box);   // v9.57：强制 Google Sans
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            dlg.getWindow().setDimAmount(0.3f);
            WindowManager.LayoutParams lp = dlg.getWindow().getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dlg.getWindow().setAttributes(lp);
        }
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dlg.dismiss(); }
        });
        dlg.show();
    }

    /** v9.30：区县命中 -> 手动城市（显示名带所属市） */
    private void pickDistrict(DistrictTable.Hit h) {
        JSONObject r = new JSONObject();
        try {
            r.put("name", storeName(h.name));   // v9.51：三段显示名转「区 · 市」存储
            r.put("latitude", h.lat);
            r.put("longitude", h.lng);
            r.put("admin1", "");
        } catch (Exception ignored) { }
        pickCity(r);
    }

    /** v9.31：关键词候选选中 -> 手动城市；c[3] 存在时作为存储名（兼容旧版，国内候选均为 3 元素） */
    private void pickCand(String[] c) {
        JSONObject r = new JSONObject();
        try {
            String store = c.length > 3 && c[3] != null && !c[3].isEmpty()
                    ? c[3] : storeName(c[0]);   // v9.51：三段显示名转「区 · 市」存储
            r.put("name", store);
            r.put("latitude", Double.parseDouble(c[1]));
            r.put("longitude", Double.parseDouble(c[2]));
            r.put("admin1", "");
        } catch (Exception ignored) { }
        pickCity(r);
    }

    /** v9.51：三段显示名转存储名（手动城市/预警匹配用）：
     *  「浙江省 · 温州市 · 鹿城区」->「鹿城区 · 温州市」（区在前，预警取前段=区名）
     *  「浙江省 · 温州市」->「温州市」；「海南省 · 万宁市」->「万宁市」
     *  「北京市 · 东城区」->「东城区」；旧格式「宝安区 · 深圳市」原样保留 */
    private static String storeName(String disp) {
        if (disp == null) return "";
        String[] segs = disp.split(" · ");
        if (segs.length >= 3) {
            return segs[segs.length - 1] + " · " + segs[segs.length - 2];
        }
        if (segs.length == 2) {
            if (CityTable.provinceMatch(segs[0]) != null) return segs[1];
            return disp;
        }
        return disp;
    }

    /** v9.31/9.51：候选名取末段去后缀，用于排序/精确比对：
     *  "浙江省 · 温州市 · 鹿城区"->"鹿城"；"宝安区 · 深圳市"->"深圳"；"浙江省"->"浙江" */
    private static String normForSort(String name) {
        String t = name;
        int idx = t.lastIndexOf(" · ");
        if (idx > 0) t = t.substring(idx + 3);
        for (String suf : new String[]{"自治县", "自治旗", "林区", "矿区",
                "新区", "特区", "区", "县", "市", "旗"}) {
            if (t.endsWith(suf)) {
                t = t.substring(0, t.length() - suf.length());
                break;
            }
        }
        return t;
    }

    /** 选中候选城市：写入手动城市并立即拉天气 */
    private void pickCity(JSONObject r) {
        if (r == null) return;
        String name = r.optString("name", "");
        String admin = r.optString("admin1", "");
        if (admin.isEmpty() || admin.equals(name)) {
            // 直辖市等：name 已是城市名
        } else {
            name = name + " · " + admin;
        }
        double lat = r.optDouble("latitude", Double.NaN);
        double lng = r.optDouble("longitude", Double.NaN);
        if (Double.isNaN(lat) || Double.isNaN(lng) || name.isEmpty()) {
            Toast.makeText(this, "该城市缺少坐标，请换一个试试", Toast.LENGTH_SHORT).show();
            return;
        }
        WeatherReporter.setManualCity(this, name, lat, lng);
        // v9.70：预警监控同步切到手动城市（无省/区信息，按城市名全量过滤）
        AlertWatcher.saveRegion(this, name, "", "");
        // v9.42：立即拉取新城市预警（不等 startLoad；startLoad 可能被 loading 阻塞跳过）
        loadAlerts(name);
        Toast.makeText(this, "已切换到「" + name + "」\n长按城市名可恢复自动定位",
                Toast.LENGTH_LONG).show();
        // v9.35：立即同步主页大字与小字（不等网络渲染），避免残留自动定位的
        // 「IP 定位」提示或旧「省 · 区」地区行
        cityText.setText(name);
        if (ipHintText != null) ipHintText.setVisibility(View.GONE);
        if (regionText != null) {
            regionText.setText("");
            regionText.setVisibility(View.GONE);
        }
        sourceText.setText("手动选择城市 · 数据加载中…");
        startLoad(true);
    }

    // ============ 数据加载 ============

    /** 渲染上次成功缓存（打开即有内容，弱网/断网不空白）；成功返回 true */
    private boolean showCached() {
        // v9.79：统一走 WeatherCenter.freshCache（未过期缓存）
        final WeatherCache.Data c = WeatherCenter.get().freshCache(this);
        if (c == null) return false;
        try {
            final JSONObject j = new JSONObject(c.json);
            final Location l = new Location("cache");
            l.setLatitude(c.lat);
            l.setLongitude(c.lng);
            render(j, l, c.city, false);
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
            sourceText.setText("缓存数据 · 更新于 " + f.format(new java.util.Date(c.ts)));
            return true;
        } catch (Exception ignored) { }
        return false;
    }

    /** 定位 -> 反查城市 -> 拉数据 -> 渲染（后台线程）。
     *  v9.45：加载代际机制——不再被 loading 阻塞；每次调用递增 loadGen，
     *  旧线程回调发现代际过期即丢弃，新请求始终立即生效（修复长按恢复自动定位被阻塞失效）。 */
    private void startLoad(final boolean manual) {
        final int gen = ++loadGen;
        loading = true;
        String gpsEn = "?", netEn = "?";
        try {
            LocationManager lm0 = (LocationManager) getSystemService(LOCATION_SERVICE);
            gpsEn = String.valueOf(lm0.isProviderEnabled(LocationManager.GPS_PROVIDER));
            netEn = String.valueOf(lm0.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        } catch (Exception ignored) { }
        LogFile.i("Main", "startLoad manual=" + manual + " locChoice=" + locChoice
                + " perm=" + locator.hasPermission() + " gpsEnabled=" + gpsEn + " netEnabled=" + netEn);

        // v9.41：定位模式联动降雨图入口（手动城市隐藏 / 自动定位显示，带动画）
        applyMapCard(!WeatherReporter.hasManualCity(this));

        // v9.46：刷新即检测定位方式——定位期间地区大字下先显示「定位中…」
        if (ipHintText != null && !WeatherReporter.hasManualCity(this)) {
            ipHintText.setText("定位中…");
            ipHintText.setVisibility(View.VISIBLE);
        }

        // 打开即有内容：页面尚未渲染过数据时，先用缓存垫底
        if (!rendered) showCached();

        if (manual) {
            lastManual = true;
            ObjectAnimator spin = ObjectAnimator.ofFloat(refreshIcon, "rotation", 0f, 360f);
            spin.setDuration(600);
            spin.setInterpolator(new DecelerateInterpolator());
            spin.start();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (gen != loadGen) return;   // 已被更新的请求取代
                // v9.27：手动查询的城市优先（跳过定位，主页/小组件/定时通知统一）
                if (WeatherReporter.hasManualCity(MainActivity.this)) {
                    final double mlat = WeatherReporter.manualLat(MainActivity.this);
                    final double mlng = WeatherReporter.manualLng(MainActivity.this);
                    final String mcity = WeatherReporter.manualCityName(MainActivity.this);
                    try {
                        // v9.79：统一走 WeatherCenter（拉取 + 写缓存）
                        final JSONObject json = WeatherCenter.get()
                                .fetchWeather(MainActivity.this, mlat, mlng, mcity);
                        final Location loc = new Location("manual");
                        loc.setLatitude(mlat);
                        loc.setLongitude(mlng);
                        loc.setAccuracy(1000f);
                        loadAlerts(mcity);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (gen != loadGen) return;
                                render(json, loc, mcity, false);
                                lastFullRefreshTs = System.currentTimeMillis();
                                loading = false;
                            }
                        });
                        // 缓存已在 fetchWeather 内写入，此处只同步小组件
                        WeatherWidgetProvider.updateAll(MainActivity.this);
                    } catch (final Exception e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (gen != loadGen) return;
                                loading = false;
                                uvDayFailed = true;          // v9.68
                                if (activeUvDay != null) activeUvDay.setFailed();
                                Toast.makeText(MainActivity.this,
                                        "「" + mcity + "」获取失败，请检查网络\n可长按城市名恢复自动定位",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    return;
                }
                // v9.16：GPS/网络与 IP 定位并行竞争（Locator 公共定位，定时通知服务同款）
                // v9.44：按用户选择的定位方式执行（locChoice 空则自动竞争）
                if ("gps".equals(locChoice) && !locator.hasPermission()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != loadGen) return;
                            try { Toast.makeText(MainActivity.this,
                                    "未授予定位权限，已回退 IP 定位", Toast.LENGTH_SHORT).show(); }
                            catch (Exception ignored) { }
                        }
                    });
                }
                Location loc0 = locator.locateBy(locChoice);
                boolean ipLoc = loc0 != null && "ip".equals(loc0.getProvider());
                // v9.88.4：定位失败不再放弃拉取——用最后一次成功定位（无则默认坐标）
                // 兜底继续拉取，七天/24 小时等数据持续刷新（与定时播报同口径）
                final boolean locFailed = loc0 == null;
                final Location loc;
                if (loc0 == null) {
                    loc = new Location("lastfix");
                    loc.setLatitude(WeatherReporter.lat(MainActivity.this));
                    loc.setLongitude(WeatherReporter.lng(MainActivity.this));
                    loc.setAccuracy(9999f);
                    loc.setTime(System.currentTimeMillis());
                } else {
                    loc = loc0;
                }
                final boolean isIpLoc = ipLoc;
                // v9.47：四线程并行调度——
                //   T1 定位线程（本线程）完成后，并行启动 T2 城市反查 与 T3 天气请求；
                //   T2 一出城市名立即启动 T4 预警拉取（loadAlerts 内部独立线程，与 T3 并行）；
                //   主线程汇合 T2/T3 后统一渲染。网络 RTT 由串行 3 跳降为并行峰值。
                final String[] cityBox = new String[1];
                final JSONObject[] jsonBox = new JSONObject[1];
                final Exception[] errBox = new Exception[1];
                Thread t2 = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        cityBox[0] = reverseGeocode(loc.getLatitude(), loc.getLongitude());
                    }
                });
                Thread t3 = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // v9.79：统一走 WeatherCenter（内部含缓存写入，city 以 t2 结果为准）
                            jsonBox[0] = WeatherCenter.get().fetchWeather(
                                    MainActivity.this, loc.getLatitude(),
                                    loc.getLongitude(), cityBox[0]);
                        } catch (Exception e) {
                            errBox[0] = e;
                        }
                    }
                });
                t2.start();
                t3.start();
                try { t2.join(); } catch (InterruptedException ignored) { }
                final String city = cityBox[0];
                if (gen != loadGen) return;   // 代际过期：丢弃过期结果
                loadAlerts(city);             // T4：预警拉取内部独立线程，与 T3 并行
                try { t3.join(); } catch (InterruptedException ignored) { }
                if (gen != loadGen) return;
                final JSONObject json = jsonBox[0];
                if (json == null) {
                    final Exception fe = errBox[0];
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != loadGen) return;
                            loading = false;
                            if (rendered) {
                                // 页面已有数据，保留并提示网络异常
                                sourceText.setText("网络异常 · 当前显示上次数据，点 ⟳ 重试");
                            } else {
                                descText.setText("获取天气失败，请检查网络后点刷新");
                                tempText.setText("--°");
                                sourceText.setText("错误：" + (fe != null ? fe.getClass().getSimpleName() : "null"));
                            }
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != loadGen) return;
                        render(json, loc, city, isIpLoc);
                        lastFullRefreshTs = System.currentTimeMillis();
                        loading = false;
                        if (locFailed) {
                            // 定位失败：数据仍按上次位置实时刷新，仅提示定位状态
                            LogFile.w("Main", "定位失败 loc0=null 走历史兜底 locChoice=" + locChoice);
                            // v9.87-fix：GPS 模式全链路失败时给明确引导
                            // （类原生无 AGPS 冷启动极慢 / 定位开关未开 / 室内无信号）
                            if ("gps".equals(locChoice)) {
                                try {
                                    Toast.makeText(MainActivity.this,
                                            "GPS 暂无信号，已用上次位置显示\n已转入后台等待卫星信号，出结果会自动更新\n室内无信号属正常，请到室外空旷处；若室外仍失败，请检查定位模式是否选了高精度",
                                            Toast.LENGTH_LONG).show();
                                } catch (Exception ignored) { }
                            }
                        } else {
                            // v9.19：每次更新主页信息都强制弹出一次定位方式提示
                            toastLoc(isIpLoc);
                        }
                    }
                });
                // 成功后写缓存，下次打开直接可用
                WeatherCache.save(MainActivity.this, json.toString(),
                        (city != null && !city.isEmpty()) ? city : "",
                        loc.getLatitude(), loc.getLongitude());
                // v9.20：主页更新后同步刷新桌面小组件（读刚写的缓存，不重复请求）
                WeatherWidgetProvider.updateAll(MainActivity.this);
            }
        }).start();
    }

    /** v9.47：GPS 实时监控——自动模式下注册 GPS_PROVIDER 监听；
     *  拿到首个 fix 时若用户未手动配置定位方式/城市且当前是 IP 定位，
     *  立即切 GPS 重新刷新（60s 节流防频繁打断）。回调在主线程 Looper。 */
    private void startGpsWatch() {
        try {
            // v9.87-fix：auto 与 gps 模式都启用 GPS 监听——gps 模式实时定位超时走兜底后，
            // 卫星 fix 迟到时能自动升级为精确定位（Android 15 移植 ROM 冷启动可达 1 分钟+）
            if (gpsWatching) return;
            if (!"auto".equals(locChoice) && !"gps".equals(locChoice)) return;
            if (!locator.hasPermission()) return;   // 无权限：GPS 本就不可用
            gpsMgr = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (gpsMgr == null) return;
            gpsListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location l) {
                    if (l == null) return;
                    if (WeatherReporter.hasManualCity(MainActivity.this)) return;
                    LogFile.i("GPSWatch", "卫星 fix 到达 acc=" + l.getAccuracy()
                            + " provider=" + l.getProvider());
                    long now = System.currentTimeMillis();
                    if (now - lastGpsAutoTs < 60_000) return;   // 节流
                    if ("auto".equals(locChoice)) {
                        if (!lastLocModeIp) return;              // 已是 GPS 定位，无需切换
                        lastGpsAutoTs = now;
                        startLoad(false);   // GPS fix 到手 → 立即以 GPS 实时信息刷新
                    } else if ("gps".equals(locChoice)) {
                        // v9.87-fix：GPS 模式——实时定位可能已超时走 IP/历史兜底，
                        // 卫星 fix 一旦到达就升级为 GPS 精确定位（60s 节流）
                        lastGpsAutoTs = now;
                        startLoad(false);
                    }
                }
                @Override public void onStatusChanged(String p, int s, Bundle b) { }
                @Override public void onProviderEnabled(String p) { }
                @Override public void onProviderDisabled(String p) { }
            };
            // 最小 10s/50m 更新间隔，兼顾实时性与耗电
            gpsMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 50, gpsListener);
            // v9.87-fix：GNSS 卫星状态诊断——无 fix 时区分"HAL 不工作"（无卫星上报）
            // 与"有卫星但定不上"（信号/AGPS 问题）；15s 节流写日志
            try {
                gnssCallback = new android.location.GnssStatus.Callback() {
                    @Override
                    public void onSatelliteStatusChanged(android.location.GnssStatus status) {
                        long now = System.currentTimeMillis();
                        if (now - lastGnssLogTs < 15000) return;
                        lastGnssLogTs = now;
                        int vis = 0, used = 0;
                        for (int i = 0; i < status.getSatelliteCount(); i++) {
                            vis++;
                            if (status.usedInFix(i)) used++;
                        }
                        LogFile.i("GNSS", "可见卫星=" + vis + " 用于定位=" + used);
                    }
                    @Override
                    public void onFirstFix(int ttff) {
                        LogFile.i("GNSS", "首次 fix 耗时(ms)=" + ttff);
                    }
                };
                gpsMgr.registerGnssStatusCallback(gnssCallback,
                        new android.os.Handler(getMainLooper()));
            } catch (Exception e) {
                LogFile.e("Main", "GNSS 状态注册失败", e);
            }
            gpsWatching = true;
        } catch (SecurityException ignored) { }
    }

    private void stopGpsWatch() {
        try {
            if (gpsMgr != null && gpsListener != null) {
                gpsMgr.removeUpdates(gpsListener);
            }
            if (gpsMgr != null && gnssCallback != null) {
                gpsMgr.unregisterGnssStatusCallback(gnssCallback);
                gnssCallback = null;
            }
        } catch (Exception ignored) { }
        gpsWatching = false;
    }

    /** v9.19：每次主页更新后强制弹出本次定位方式提示（主线程调用） */
    private void toastLoc(boolean ipLoc) {
        try {
            Toast.makeText(this,
                    ipLoc ? "IP 定位 · 结果可能有偏差" : "GPS 定位",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { }
    }

    /** 反向地理编码：BigDataCloud 优先，失败回退 Nominatim；带缓存；
     *  同时填充 regionProvince / regionDistrict 供预警精确匹配使用。 */
    private String reverseGeocode(double lat, double lng) {
        if (cityName != null && distKm(lat, lng, lastLat, lastLng) < 0.5) {
            return cityName;
        }
        String c = null;
        String prov = null, dist = null;
        try {
            String url = "https://api.bigdatacloud.net/data/reverse-geocode-client"
                    + "?latitude=" + lat + "&longitude=" + lng + "&localityLanguage=zh";
            JSONObject j = WeatherApi.getJson(url);
            c = j.optString("city", "");
            if (c.isEmpty()) c = j.optString("locality", "");
            if (c.isEmpty()) c = j.optString("principalSubdivision", "");
            if (c.isEmpty()) c = j.optString("countryName", "");
            prov = j.optString("principalSubdivision", "");
            dist = j.optString("locality", "");
        } catch (Exception ignored) { }
        if (c == null || c.isEmpty()) {
            try {
                String url = "https://nominatim.openstreetmap.org/reverse"
                        + "?lat=" + lat + "&lon=" + lng + "&format=json&accept-language=zh";
                JSONObject j = WeatherApi.getJson(url);
                JSONObject addr = j.optJSONObject("address");
                if (addr != null) {
                    c = addr.optString("city", "");
                    if (c.isEmpty()) c = addr.optString("town", "");
                    if (c.isEmpty()) c = addr.optString("county", "");
                    if (c.isEmpty()) c = addr.optString("state", "");
                    prov = addr.optString("state", "");
                    dist = addr.optString("county", "");
                    if (dist.isEmpty()) dist = addr.optString("town", "");
                    if (dist.isEmpty()) dist = addr.optString("city", "");
                }
            } catch (Exception ignored) { }
        }
        if (c != null && !c.isEmpty()) {
            cityName = c;
            lastLat = lat;
            lastLng = lng;
            regionProvince = prov;
            regionDistrict = dist;
        }
        return c;
    }

    /** 渲染全部数据（含动效） */
    private void render(JSONObject json, Location loc, String city, boolean ipLoc) {
        lastLocModeIp = ipLoc;   // v9.47：供 GPS 实时监控判断「当前是 IP 才自动切 GPS」
        curLat = loc.getLatitude();
        curLng = loc.getLongitude();
        // v9.35：手动城市模式不显示「省 · 区」行（无该数据），也不显示 IP/GPS 定位提示
        boolean manual = WeatherReporter.hasManualCity(this);
        // 详细地区：省 · 区（IP 定位时提示可能偏差）
        String region = "";
        if (!manual && regionDistrict != null && !regionDistrict.isEmpty()) {
            region = (regionProvince != null && !regionProvince.isEmpty()
                    && !regionProvince.equals(regionDistrict))
                    ? regionProvince + " · " + regionDistrict : regionDistrict;
        } else if (!manual && regionProvince != null && !regionProvince.isEmpty()) {
            region = regionProvince;
        }
        if (regionText != null) {
            regionText.setText(region);
            regionText.setVisibility(region.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (ipHintText != null) {
            // v9.46：自动定位模式下地区大字下常驻显示定位方式（GPS 定位 / IP 定位 · 可能有偏差）
            if (!manual) {
                // v9.88：定位方式右侧常驻显示数据缓存更新时间
                String upd = "";
                try {
                    WeatherCache.Data cd = WeatherCache.load(MainActivity.this);
                    if (cd != null && cd.ts > 0) {
                        upd = " · 更新 " + new java.text.SimpleDateFormat(
                                "HH:mm", java.util.Locale.US)
                                .format(new java.util.Date(cd.ts));
                    }
                } catch (Exception ignored) { }
                LogFile.i("Main", "渲染完成 locMode=" + (ipLoc ? "IP" : "GPS") + " " + upd);
                ipHintText.setText((ipLoc ? "IP 定位 · 结果可能有偏差" : "GPS 定位") + upd);
                ipHintText.setVisibility(View.VISIBLE);
            } else {
                ipHintText.setVisibility(View.GONE);
            }
        }
        // 保存定位供定时通知使用
        WeatherReporter.saveLocation(this, curLat, curLng, city);
        // v9.70：同步预警监控地区快照（手动城市省/区留空，服务按城市名全量过滤）
        AlertWatcher.saveRegion(this, city,
                manual ? "" : (regionProvince == null ? "" : regionProvince),
                manual ? "" : (regionDistrict == null ? "" : regionDistrict));
        try {
            JSONObject cur = json.getJSONObject("current");
            int code = cur.getInt("weather_code");
            boolean day = cur.getInt("is_day") == 1;
            int hour = cur.optInt("hour", -1);
            if (hour < 0 || hour > 23) {
                hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            }

            int[] pal = Theme.paletteHour(this, code, hour);
            animateBg(pal);
            getWindow().setStatusBarColor(0x00000000);
            getWindow().setNavigationBarColor(0x00000000);
            getWindow().getDecorView()
                    .setSystemUiVisibility(Theme.statusBarLightFlag(this)
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

            double t = cur.getDouble("temperature_2m");
            double feels = cur.getDouble("apparent_temperature");
            int hum = cur.getInt("relative_humidity_2m");
            double wind = cur.getDouble("wind_speed_10m");
            double windDir = cur.optDouble("wind_direction_10m", -1);

            if (city != null && !city.isEmpty()) {
                cityText.setText(city);
            } else {
                cityText.setText(String.format("我的位置  (%.2f, %.2f)",
                        loc.getLatitude(), loc.getLongitude()));
            }

            animateTemp(t);
            descText.setText(Fonts.mixIcon(WeatherApi.icon(code, day), WeatherApi.text(code)));
            boolean bgLight = bgBrightness(pal);
            weatherBg.setLightPalette(bgLight);
            weatherBg.setWeather(code, day);
            applyTopColors(bgLight);
            applyCardTexts(pal);
            // v9.56：三组件创建时直接按卡片背景取自适应字色组
            int[] cols = Theme.cardTextColors(pal[1]);

            // 日出日落
            JSONObject daily = json.getJSONObject("daily");

            // v9.22：体感行精简；湿度/风速/云量/UV 移入下方实时详情卡
            String feelsStr = "体感 " + Math.round(feels) + "°";
            try {
                // v9.82：字段缺失/null 兜底（旧缓存升级场景不崩）
        JSONArray pops = daily.optJSONArray("precipitation_probability_max");
                if (pops.length() > 0) {
                    int pop0 = pops.getInt(0);
                    feelsStr += "   ·   今日降水 " + pop0 + "%";
                }
            } catch (Exception ignored) { }
            feelsText.setText(feelsStr);

            // 实时详情卡：湿度 / 风速 / 云量 / UV
            fillDetail(hum, wind, windDir, cur.optInt("cloud_cover", -1),
                    cur.optDouble("uv_index", -1), cols);
            String sr = WeatherApi.hhmm(daily.getJSONArray("sunrise").getString(0));
            String ss = WeatherApi.hhmm(daily.getJSONArray("sunset").getString(0));
            sunTimeText.setText("日出 " + sr + " · 日落 " + ss);
            // v9.87：日出日落弧线按卡片背景明暗自适应配色，太阳按背景亮度微调
            sunArc.setColors(cols[1],
                    bgLight ? 0xFFF0A020 : 0xFFFFC94D,
                    bgLight ? 0x55F0A020 : 0x55FFC94D);
            sunArc.setTimes(sr, ss);
            updateMoonCard(daily, json);

            // 背景细节已随天气切换（渐变 + 粒子动画），列表构建完成后刷新毛玻璃快照
            final android.view.View sv = findViewById(R.id.scrollRoot);
            sv.post(new Runnable() {
                @Override
                public void run() { refreshGlass(); }
            });

            // 24 小时 & 7 天（v9.82：buildHourly 传整个 json，内部按城市时区对齐）
            buildHourly(json, cols);
            buildDaily(daily, cols);

            sourceText.setText("数据来源：Open-Meteo  ·  " + json.optString("timezone", ""));

            if (!rendered) {
                rendered = true;
                popIn();
            }

            // 手动刷新成功提示
            if (lastManual) {
                lastManual = false;
                java.text.SimpleDateFormat sdf =
                        new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US);
                Toast.makeText(MainActivity.this,
                        "天气已更新 · " + sdf.format(new java.util.Date()),
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            descText.setText("解析数据失败：" + e.getMessage());
        }
    }

    /** v9.22：实时详情卡——4 列（湿度/风速/云量/UV），Material 图标 + 主次文字 */
    /** v9.56：详情卡文字直接用自适应色组（cols[0]主/cols[1]次/cols[2]强调） */
    /** v9.58：四列可点击弹出二级菜单；UV 弹窗参考 UVlens 展示晒伤时间与损害程度 */
    /** v9.59：湿度/云量改为分档色条弹窗，风速新增实时风向标，各档位配建议 */
    private void fillDetail(int hum, double wind, double windDir, int cloud, double uv,
                            int[] cols) {
        if (detailRow == null) return;
        detailRow.removeAllViews();
        String[] labels = {"湿度", "风速", "云量", "UV 指数"};
        // v9.24：风速图标纠正——\uE3B9 实际是 send（纸飞机），air（三波浪线）
        // 的正确码位是 \uE3BA（官方 Material Icons），这才符合 Google UI 标准。
        String[] icons = {"\uE798", "\uE3BA", "\uE2BD", "\uE430"};
        String[] vals = new String[4];
        vals[0] = hum >= 0 ? hum + "%" : "--";
        vals[1] = wind >= 0 ? Math.round(wind) + " km/h" : "--";
        vals[2] = cloud >= 0 ? cloud + "%" : "--";
        vals[3] = uv >= 0 ? String.format(Locale.US, "%.1f", uv) : "--";
        final boolean dark = Theme.isDark(this);
        final int fHum = hum;
        final double fWind = wind;
        final double fWindDir = windDir;
        final int fCloud = cloud;
        final double fUv = uv;
        for (int i = 0; i < 4; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            col.setPadding(dp(4), dp(6), dp(4), dp(6));

            // v9.58：按压反馈（按下微亮）+ 二级菜单点击
            android.graphics.drawable.StateListDrawable sld =
                    new android.graphics.drawable.StateListDrawable();
            android.graphics.drawable.GradientDrawable pressedBg =
                    new android.graphics.drawable.GradientDrawable();
            pressedBg.setColor(dark ? 0x14FFFFFF : 0x0A000000);
            pressedBg.setCornerRadius(dp(12));
            android.graphics.drawable.GradientDrawable normalBg =
                    new android.graphics.drawable.GradientDrawable();
            normalBg.setColor(0x00000000);
            normalBg.setCornerRadius(dp(12));
            sld.addState(new int[]{android.R.attr.state_pressed}, pressedBg);
            sld.addState(new int[]{}, normalBg);
            col.setBackground(sld);
            col.setClickable(true);
            col.setFocusable(true);

            TextView ic = new TextView(this);
            ic.setText(icons[i]);
            ic.setTextColor(cols[2]);
            ic.setTextSize(17);
            ic.setGravity(Gravity.CENTER);
            ic.setTypeface(Fonts.icons());

            TextView val = new TextView(this);
            val.setText(vals[i]);
            val.setTextColor(cols[0]);
            val.setTextSize(14);
            val.setTypeface(Fonts.medium());
            val.setGravity(Gravity.CENTER);
            val.setPadding(0, dp(4), 0, 0);

            TextView lbl = new TextView(this);
            lbl.setText(labels[i]);
            lbl.setTextColor(cols[1]);
            lbl.setTextSize(10);
            lbl.setGravity(Gravity.CENTER);
            lbl.setSingleLine(true);
            lbl.setEllipsize(android.text.TextUtils.TruncateAt.END);
            lbl.setPadding(0, dp(2), 0, 0);

            final int idx = i;
            col.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx == 0 && fHum >= 0) {
                        showHumDialog(fHum);
                    } else if (idx == 1 && fWind >= 0) {
                        showWindDialog(fWind, fWindDir);
                    } else if (idx == 2 && fCloud >= 0) {
                        showCloudDialog(fCloud);
                    } else if (idx == 3 && fUv >= 0) {
                        showUvDialog(fUv);
                    }
                }
            });

            col.addView(ic);
            col.addView(val);
            col.addView(lbl);
            detailRow.addView(col);
        }
        Fonts.apply(detailRow);   // v9.57：强制 Google Sans（中文自动回退系统字体）
    }

    // ============ v9.58：详情卡二级菜单 ============

    /** 紫外线等级（WHO 标准：0-2 低 / 3-5 中等 / 6-7 高 / 8-10 很高 / 11+ 极高） */
    private static final String[] UV_NAMES = {"低", "中等", "高", "很高", "极高"};
    private static final String[] UV_EN = {"LOW", "MODERATE", "HIGH", "VERY HIGH", "EXTREME"};
    /** v9.69：更多信息弹窗统一低饱和浅色调色板（雾蓝灰系，浅→深） */
    private static final int[] PASTEL = {
            0xFFCBD6E6, 0xFFB8C6DC, 0xFFA5B6D2, 0xFF92A6C8, 0xFF7F96BE};
    /** 云量档（4 档）用前 4 色 */
    private static final int[] PASTEL4 = {
            0xFFCBD6E6, 0xFFB8C6DC, 0xFFA5B6D2, 0xFF92A6C8};

    private static final int[] UV_COLORS = {
            0xFF4CAF50, 0xFFFFC107, 0xFFFF9800, 0xFFF44336, 0xFF9C27B0};
    /** 中等肤色（II–III 型）在太阳下的晒伤时间估算 */
    private static final String[] UV_BURN = {
            "60 分钟以上", "约 30 分钟", "约 20 分钟", "约 10 分钟", "约 5 分钟"};
    private static final String[] UV_DAMAGE = {
            "长时间暴露才有轻微泛红风险，皮肤损伤很小。",
            "短时间暴晒即可能泛红，长期日晒会加速皮肤老化。",
            "皮肤约 20 分钟即可晒伤，色素沉着与光老化风险上升。",
            "10 分钟内可能晒伤，出现灼痛、脱皮，需严格防护。",
            "5 分钟内即可严重晒伤，强烈建议留在室内。"};
    private static final String[] UV_ADVICE = {
            "无需特别防护，长时间户外可涂 SPF15+ 防晒霜。",
            "外出建议 SPF30+ 防晒霜并戴遮阳帽，正午减少暴晒。",
            "SPF30+ 防晒霜 + 遮阳帽 + 太阳镜，10–16 点避免长时间暴晒。",
            "SPF50+ 防晒霜 + 防晒衣 + 遮阳伞，10–16 点尽量待在阴凉处。",
            "紫外线极强：尽量留在室内，外出必须全副防护并缩短暴露时间。"};

    /** v9.67：Fitzpatrick 肤质分型（I 最白 → VI 最深），用于个性化晒伤时间 */
    private static final int[] SKIN_COLORS = {
            0xFFF7D9C3, 0xFFEDC39A, 0xFFD9A06F, 0xFFB47B4E, 0xFF8C5A38, 0xFF5C3A26};
    private static final String[] SKIN_NAMES = {
            "I 型 · 白皙（极易晒伤）", "II 型 · 白皙（易晒伤）",
            "III 型 · 自然（晒伤速度中等）", "IV 型 · 小麦（较耐受）",
            "V 型 · 棕色（耐受）", "VI 型 · 深棕（不易晒伤）"};
    /** UV=1 时约多少分钟晒伤（随肤质加深而变长） */
    private static final int[] SKIN_K = {90, 120, 160, 220, 300, 420};

    /** v9.65：颜色明度缩放（k>1 变浅，k<1 变深），用于档位色条渐变 */
    private static int shade(int color, float k) {
        int r = Math.min(255, (int) (Color.red(color) * k));
        int g = Math.min(255, (int) (Color.green(color) * k));
        int b = Math.min(255, (int) (Color.blue(color) * k));
        return Color.rgb(r, g, b);
    }

    private static int uvLevel(double uv) {
        if (uv <= 2) return 0;
        if (uv <= 5) return 1;
        if (uv <= 7) return 2;
        if (uv <= 10) return 3;
        return 4;
    }

    // ---- v9.59：湿度档位（5 档） ----
    private static final String[] HUM_BADGES = {"干燥", "舒适", "略湿", "潮湿", "非常潮湿"};
    private static final int[] HUM_COLORS = {
            0xFFF5A623, 0xFF4CAF50, 0xFF3D7BD9, 0xFF2E5E9E, 0xFF7E57C2};
    private static final String[] HUM_ADVICE = {
            "空气偏干：多喝水、注意皮肤保湿，室内可开加湿器，减少静电。",
            "湿度宜人：体感舒适，保持通风即可，无需特别处理。",
            "略感闷热：注意通风换气，出汗后及时补充水分。",
            "较为潮湿：汗液难蒸发、闷热感明显，减少剧烈运动，衣物注意防潮。",
            "非常潮湿：体感黏腻，谨防中暑，尽量待在凉爽环境，物品防霉防潮。"};

    private static int humLevel(int hum) {
        if (hum < 30) return 0;
        if (hum < 60) return 1;
        if (hum < 70) return 2;
        if (hum < 80) return 3;
        return 4;
    }

    // ---- v9.59：云量档位（4 档） ----
    private static final String[] CLOUD_BADGES = {"晴", "少云", "多云", "阴"};
    private static final int[] CLOUD_COLORS = {
            0xFFF5A623, 0xFF58A7E8, 0xFF8A94A6, 0xFF5F6B7C};
    private static final String[] CLOUD_ADVICE = {
            "晴空万里：紫外线较强注意防晒；是晾晒衣物、户外活动的好时机。",
            "云量不多：日照充足，适合户外活动；紫外线仍不弱，记得防晒。",
            "云层较多：日照减弱、体感舒适，适合户外，但需留意阵雨，随身带伞。",
            "天空阴沉：日照弱、体感偏凉，出门带伞，谨防降雨与潮湿。"};

    private static int cloudLevel(int cloud) {
        if (cloud < 10) return 0;
        if (cloud < 30) return 1;
        if (cloud < 70) return 2;
        return 3;
    }

    // ---- v9.59：风速影响档（5 档：0-2 级轻柔 / 3-4 和畅 / 5-6 明显 / 7-8 强劲 / 9+ 猛烈） ----
    // v9.60：升级为现代渐变配色（翠绿→青→琥珀→橙→红，Tailwind 风格）
    private static final String[] WIND_LEVEL_NAMES = {"轻柔", "和畅", "明显", "强劲", "猛烈"};
    private static final int[] WIND_LEVEL_COLORS = {
            0xFF10B981, 0xFF06B6D4, 0xFFF59E0B, 0xFFF97316, 0xFFEF4444};
    private static final String[] WIND_ADVICE = {
            "风力轻柔：适合散步、骑行等户外活动，出行无碍。",
            "风力和畅：晾晒效果好；骑行略有风阻，留意侧风。",
            "风力明显：户外活动注意防风，撑伞吃力，晾晒衣物需夹牢。",
            "风力强劲：出行远离广告牌与大树，谨防高空坠物，减少户外活动。",
            "风力猛烈：请留在室内、关好门窗，避免外出，注意安全。"};

    private static int windLevel(double kmh) {
        if (kmh < 12) return 0;    // 0-2 级
        if (kmh < 29) return 1;    // 3-4 级
        if (kmh < 50) return 2;    // 5-6 级
        if (kmh < 75) return 3;    // 7-8 级
        return 4;                  // 9 级以上
    }

    // ---- v9.59：16 方位风向 ----
    private static final String[] WIND_DIRS = {"北", "北东北", "东北", "东东北", "东", "东东南",
            "东南", "南东南", "南", "南西南", "西南", "西西南", "西", "西西北", "西北", "北西北"};
    // v9.63：8 方位（罗盘大字风向用，索引 0=北 顺时针）
    private static final String[] WIND_DIR8 = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};

    private static String windDirName(double dir) {
        int i = (int) Math.round(dir / 22.5) % 16;
        return WIND_DIRS[i];
    }

    /** 蒲福风级：返回 {等级名, 描述} */
    private static String[] windInfo(double kmh) {
        if (kmh < 1)   return new String[]{"0 级 · 无风", "烟直上，几乎感觉不到风。"};
        if (kmh < 6)   return new String[]{"1 级 · 软风", "轻风拂面，烟略倾斜。"};
        if (kmh < 12)  return new String[]{"2 级 · 轻风", "树叶微响，风感明显。"};
        if (kmh < 20)  return new String[]{"3 级 · 微风", "旗帜展开，小枝摇动。"};
        if (kmh < 29)  return new String[]{"4 级 · 和风", "尘土扬起，小树摇动。"};
        if (kmh < 39)  return new String[]{"5 级 · 清风", "小树摇摆，水面起波，撑伞吃力。"};
        if (kmh < 50)  return new String[]{"6 级 · 强风", "大枝摇动，撑伞困难。"};
        if (kmh < 62)  return new String[]{"7 级 · 疾风", "迎风难行，全树摇动。"};
        if (kmh < 75)  return new String[]{"8 级 · 大风", "小枝折断，步行受阻。"};
        if (kmh < 89)  return new String[]{"9 级 · 烈风", "屋顶瓦片可能受损。"};
        if (kmh < 103) return new String[]{"10 级 · 狂风", "树木可能连根拔起，破坏较大。"};
        if (kmh < 118) return new String[]{"11 级 · 暴风", "陆上罕见，破坏严重。"};
        return new String[]{"12 级 · 飓风", "摧毁力极大，尽量留在室内。"};
    }

    // ============ v9.59：档位弹窗公共组件 ============

    /** 档位面板：顶行（图标+标题+关闭）+ 大数值/徽章 + 档位色条 + 逐段对齐的档位名 */
    private void addLevelPanel(final Dialog d, final LinearLayout box, final String title,
                               final String icon, final String value, final String badge,
                               final int badgeColor, final int[] segColors, final int lv,
                               final String[] scaleNames, final boolean dark,
                               final int txtColor, final int subColor) {
        // 顶行：图标 + 标题 + 关闭
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTypeface(Fonts.icons());
        iconTv.setTextSize(20f);
        iconTv.setTextColor(badgeColor);
        top.addView(iconTv);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(txtColor);
        titleTv.setTextSize(18f);
        titleTv.setTypeface(Fonts.medium());
        titleTv.setPadding(dp(10), 0, 0, 0);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(titleTv);

        TextView closeTv = new TextView(this);
        closeTv.setText("\uE5CD");
        closeTv.setTypeface(Fonts.icons());
        closeTv.setTextSize(19f);
        closeTv.setTextColor(subColor);
        closeTv.setPadding(dp(6), dp(2), 0, dp(2));
        closeTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { d.dismiss(); }
        });
        top.addView(closeTv);
        box.addView(top);

        // 大数值 + 等级徽章
        LinearLayout valRow = new LinearLayout(this);
        valRow.setOrientation(LinearLayout.HORIZONTAL);
        valRow.setGravity(Gravity.BOTTOM);
        valRow.setPadding(0, dp(10), 0, 0);

        TextView valTv = new TextView(this);
        valTv.setText(value);
        valTv.setTextColor(txtColor);
        valTv.setTextSize(46f);
        valTv.setTypeface(Fonts.medium());
        valRow.addView(valTv);

        TextView badgeTv = new TextView(this);
        badgeTv.setText(badge);
        badgeTv.setTextColor(0xFF37465E);   // v9.69：浅色底配深字
        badgeTv.setTextSize(12f);
        badgeTv.setTypeface(Fonts.medium());
        badgeTv.setPadding(dp(11), dp(4), dp(11), dp(4));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(badgeColor);
        badgeBg.setCornerRadius(dp(11));
        badgeTv.setBackground(badgeBg);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = dp(12);
        badgeLp.bottomMargin = dp(9);
        valRow.addView(badgeTv, badgeLp);
        box.addView(valRow);

        // 档位色条（当前档位及以下点亮）
        LinearLayout barRow = new LinearLayout(this);
        barRow.setOrientation(LinearLayout.HORIZONTAL);
        barRow.setPadding(0, dp(10), 0, 0);
        for (int i = 0; i < segColors.length; i++) {
            View seg = new View(this);
            GradientDrawable segBg = new GradientDrawable();
            if (i <= lv) {
                // v9.66：点亮段统一用当前档位色，段间由浅到深（第 0 段最浅，当前段最深）
                float k = lv == 0 ? 1.15f
                        : (1.55f - 0.83f * ((float) i / lv));
                segBg.setColor(shade(segColors[lv], k));
            } else {
                segBg.setColor(Theme.outlineVariant(this));
            }
            segBg.setCornerRadius(dp(2));
            seg.setBackground(segBg);
            LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(0, dp(6), 1f);
            if (i > 0) segLp.leftMargin = dp(4);
            barRow.addView(seg, segLp);
        }
        box.addView(barRow);

        // 档位名：每段色条正下方各一个，逐段对齐（与色条同 margin/weight）
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setPadding(0, dp(4), 0, 0);
        for (int i = 0; i < segColors.length; i++) {
            TextView nameTv = new TextView(this);
            nameTv.setText(scaleNames[i]);
            nameTv.setTextColor(subColor);
            nameTv.setTextSize(10f);
            nameTv.setGravity(Gravity.CENTER);
            nameTv.setLineSpacing(0, 1f);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) nlp.leftMargin = dp(4);
            nameRow.addView(nameTv, nlp);
        }
        box.addView(nameRow);
    }

    /** 当前档位行（强调色） */
    private void addLevelName(final LinearLayout box, final String text, final int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(shade(color, 0.62f));   // v9.69：低饱和浅色系下加深保证可读
        tv.setTextSize(16f);
        tv.setTypeface(Fonts.medium());
        tv.setPadding(0, dp(10), 0, 0);
        box.addView(tv);
    }

    /** 分隔线 */
    private void addDivider(final LinearLayout box, final int divider) {
        View div = new View(this);
        div.setBackgroundColor(divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(10);
        box.addView(div, lp);
    }

    /** 信息行：加粗前缀 + 正文 */
    private void addInfoRow(final LinearLayout box, final String label, final String text,
                            final int txtColor) {
        TextView tv = new TextView(this);
        tv.setTextSize(13.5f);
        tv.setTextColor(txtColor);
        tv.setLineSpacing(dp(2), 1f);
        tv.setPadding(0, dp(8), 0, 0);
        SpannableString ss = new SpannableString(label + "：" + text);
        ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                0, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(ss);
        box.addView(tv);
    }

    /** 建议卡：图标 + 建议文案 */
    private void addAdviceRow(final LinearLayout box, final String text, final int txtColor,
                              final boolean dark, final int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.surfaceContainerLow(this));
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        box.addView(card, lp);

        TextView ic = new TextView(this);
        ic.setText("\uE0F0");
        ic.setTypeface(Fonts.icons());
        ic.setTextSize(18f);
        ic.setTextColor(shade(accent, 0.62f));   // v9.69
        card.addView(ic);

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(txtColor);
        tv.setTextSize(13f);
        tv.setLineSpacing(dp(2), 1f);
        tv.setPadding(dp(10), 0, 0, 0);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(tv);
    }

    /** v9.64：风速风向区——左：大字风向；右：罗盘风标 */
    private void addCompass(final LinearLayout box, final double windDir, final int accent,
                            final boolean dark, final int txtColor, final int subColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(10);
        box.addView(row, rowLp);

        // ---- 左栏：大字风向（8 方位） ----
        final int i8 = (int) Math.round(windDir / 45.0) % 8;
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        row.addView(left, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        TextView bigTv = new TextView(this);
        bigTv.setText(WIND_DIR8[i8] + "风");
        bigTv.setTextColor(accent);
        bigTv.setTextSize(38f);
        bigTv.setTypeface(Fonts.medium());
        left.addView(bigTv);

        // ---- 右栏：罗盘风标 ----
        CompassView compass = new CompassView(this);
        int size = dp(100);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(size, size);
        clp.leftMargin = dp(12);
        compass.setLayoutParams(clp);
        compass.setDirection((float) (Math.round(windDir) % 360));
        // v9.60：罗盘渐变圆盘——暗色深蓝 / 亮色浅蓝
        compass.setColors(dark ? 0xFF2A3448 : 0xFFFFFFFF,
                dark ? 0xFF1A2332 : 0xFFE9F0FA,
                dark ? 0xFF33415C : 0xFFD7E0EC, subColor, accent);
        row.addView(compass);
    }

    /** 弹窗收尾：装载内容 + 字体 + 圆角透明窗口 + 宽度 */
    private void showPanel(final Dialog d, final View box) {
        d.setContentView(box);
        Fonts.apply(box);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            d.getWindow().setDimAmount(0.3f);
            WindowManager.LayoutParams lp = d.getWindow().getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.82f);
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            d.getWindow().setAttributes(lp);
        }
        d.show();
    }

    /** v9.59：湿度档位弹窗 */
    private void showHumDialog(final int hum) {
        final boolean dark = Theme.isDark(this);
        final int txtColor = dark ? 0xFFE8EAED : 0xFF1C1F23;
        final int subColor = dark ? 0xFF9AA0A8 : 0xFF6B7280;
        final int panelBg = Theme.surfaceContainerHigh(this);
        final int divider = dark ? 0xFF2E3138 : 0xFFE3E6EB;

        final int lv = humLevel(hum);
        final int c = PASTEL[lv];   // v9.69 统一浅色调

        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final GradientDrawable panel = new GradientDrawable();
        panel.setColor(panelBg);
        panel.setCornerRadius(dp(28));
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(12));
        box.setBackground(panel);

        addLevelPanel(d, box, "相对湿度", "\uE798", hum + "%", HUM_BADGES[lv], c,
                PASTEL, lv,
                new String[]{"<30\n干燥", "30-60\n舒适", "60-70\n略湿", "70-80\n潮湿", ">80\n非常潮湿"},
                dark, txtColor, subColor);
        addLevelName(box, "湿度等级：" + HUM_BADGES[lv], c);
        addDivider(box, divider);
        addInfoRow(box, "科普", "相对湿度是空气水汽压与同温饱和水汽压的比值，数值越高空气越潮湿。", txtColor);
        addAdviceRow(box, HUM_ADVICE[lv], txtColor, dark, c);
        showPanel(d, box);
    }

    /** v9.59：云量档位弹窗 */
    private void showCloudDialog(final int cloud) {
        final boolean dark = Theme.isDark(this);
        final int txtColor = dark ? 0xFFE8EAED : 0xFF1C1F23;
        final int subColor = dark ? 0xFF9AA0A8 : 0xFF6B7280;
        final int panelBg = Theme.surfaceContainerHigh(this);
        final int divider = dark ? 0xFF2E3138 : 0xFFE3E6EB;

        final int lv = cloudLevel(cloud);
        final int c = PASTEL4[lv];   // v9.69 统一浅色调

        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final GradientDrawable panel = new GradientDrawable();
        panel.setColor(panelBg);
        panel.setCornerRadius(dp(28));
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(12));
        box.setBackground(panel);

        addLevelPanel(d, box, "云量", "\uE2BD", cloud + "%", CLOUD_BADGES[lv], c,
                PASTEL4, lv,
                new String[]{"0-10\n晴", "10-30\n少云", "30-70\n多云", "70-100\n阴"},
                dark, txtColor, subColor);
        addLevelName(box, "云量等级：" + CLOUD_BADGES[lv], c);
        addDivider(box, divider);
        addInfoRow(box, "科普", "云量指天空被云层遮蔽的比例，10 成即全天密布阴天。", txtColor);
        addAdviceRow(box, CLOUD_ADVICE[lv], txtColor, dark, c);
        showPanel(d, box);
    }

    /** v9.59：风速档位弹窗——含罗盘风向标 */
    private void showWindDialog(final double wind, final double windDir) {
        final boolean dark = Theme.isDark(this);
        final int txtColor = dark ? 0xFFE8EAED : 0xFF1C1F23;
        final int subColor = dark ? 0xFF9AA0A8 : 0xFF6B7280;
        final int panelBg = Theme.surfaceContainerHigh(this);
        final int divider = dark ? 0xFF2E3138 : 0xFFE3E6EB;

        final int lv = windLevel(wind);
        final int c = PASTEL[lv];   // v9.69 统一浅色调
        String[] wi = windInfo(wind);

        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final GradientDrawable panel = new GradientDrawable();
        panel.setColor(panelBg);
        panel.setCornerRadius(dp(28));
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(12));
        box.setBackground(panel);

        addLevelPanel(d, box, "风速", "\uE3BA", Math.round(wind) + " km/h", wi[0], c,
                PASTEL, lv,
                new String[]{"0-2级\n轻柔", "3-4级\n和畅", "5-6级\n明显", "7-8级\n强劲", "9+级\n猛烈"},
                dark, txtColor, subColor);
        addCompass(box, windDir, shade(c, 0.62f), dark, txtColor, subColor);   // v9.69 深色版指针
        addLevelName(box, "风速等级：" + WIND_LEVEL_NAMES[lv], c);
        addDivider(box, divider);
        addInfoRow(box, "风力", wi[1], txtColor);
        addAdviceRow(box, WIND_ADVICE[lv], txtColor, dark, c);
        showPanel(d, box);
    }

    /** v9.58：UV 指数二级菜单——参考 UVlens，展示晒伤时间与暴露损害程度 */
    private void showUvDialog(final double uv) {
        final boolean dark = Theme.isDark(this);
        final int txtColor = dark ? 0xFFE8EAED : 0xFF1C1F23;
        final int subColor = dark ? 0xFF9AA0A8 : 0xFF6B7280;
        final int panelBg = Theme.surfaceContainerHigh(this);
        final int divider = dark ? 0xFF2E3138 : 0xFFE3E6EB;

        final int lv = uvLevel(uv);
        final int uvColor = PASTEL[lv];          // v9.69 统一浅色调
        final int uvAccent = shade(uvColor, 0.62f); // 文字/描边用深色版保证可读

        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final GradientDrawable panel = new GradientDrawable();
        panel.setColor(panelBg);
        panel.setCornerRadius(dp(28));
        // v9.67：UV 弹窗内容较长，圆角背景固定在 ScrollView 上（背景不随内容滚动）
        final ScrollView sv = new ScrollView(this);
        sv.setBackground(panel);
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(12));

        addLevelPanel(d, box, "UV 指数", "\uE430",
                String.format(Locale.US, "%.1f", uv), UV_EN[lv], uvColor,
                PASTEL, lv,
                new String[]{"0-2\n低", "3-5\n中等", "6-7\n高", "8-10\n很高", "11+\n极高"},
                dark, txtColor, subColor);
        addLevelName(box, "紫外线等级：" + UV_NAMES[lv], uvColor);
        addDivider(box, divider);

        // ---- v9.67：今日 UV 走势（UVLens 式时间线） ----
        TextView dayTitle = new TextView(this);
        dayTitle.setText("今日 UV 走势 · 柱越高越需防护");
        dayTitle.setTextColor(subColor);
        dayTitle.setTextSize(12f);
        box.addView(dayTitle);

        UvDayView day = new UvDayView(this);
        if (uvDay == null && !uvDayFailed) {
            day.setLoading(true);   // v9.68：数据未就绪——显示拉取中并触发静默刷新
            startLoad(false);
        } else if (uvDayFailed) {
            day.setFailed();
        } else {
            day.setData(uvDay, uvDayHour, PASTEL, subColor, uvAccent);   // v9.69
        }
        activeUvDay = day;
        day.setPadding(0, dp(2), 0, 0);
        box.addView(day);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface di) {
                if (activeUvDay == day) activeUvDay = null;
            }
        });

        addDivider(box, divider);

        // ---- v9.67：肤质选择（Fitzpatrick 分型，记住选择） ----
        final SharedPreferences sp = getSharedPreferences("uv_pref", MODE_PRIVATE);
        final int[] skin = {sp.getInt("skin", 2)};   // 默认 III 型

        TextView skinTitle = new TextView(this);
        skinTitle.setText("我的肤质 · 点击色块切换（决定晒伤速度）");
        skinTitle.setTextColor(subColor);
        skinTitle.setTextSize(12f);
        box.addView(skinTitle);

        final TextView skinNote = new TextView(this);
        skinNote.setTextColor(subColor);
        skinNote.setTextSize(11.5f);
        skinNote.setPadding(0, dp(4), 0, 0);

        // 晒伤时间文案（声明提前供肤质点击监听使用，addView 在其后）
        final TextView burnTv = new TextView(this);
        // v9.68：晒伤估算放大高亮（UVLens 核心信息强调）
        GradientDrawable burnBg = new GradientDrawable();
        burnBg.setCornerRadius(dp(14));
        burnBg.setColor(Color.argb(46, Color.red(uvAccent),
                Color.green(uvAccent), Color.blue(uvAccent)));
        burnTv.setBackground(burnBg);
        burnTv.setTextColor(uvAccent);   // v9.69 深色版
        burnTv.setTextSize(28f);
        burnTv.setTypeface(Fonts.medium());
        burnTv.setGravity(Gravity.CENTER);
        burnTv.setPadding(dp(14), dp(10), dp(14), dp(10));

        final TextView burnNote = new TextView(this);
        burnNote.setTextColor(subColor);
        burnNote.setTextSize(11.5f);
        burnNote.setPadding(0, dp(2), 0, 0);

        final TextView spfTv = new TextView(this);
        spfTv.setTextColor(subColor);
        spfTv.setTextSize(11.5f);
        spfTv.setPadding(0, dp(6), 0, 0);

        LinearLayout skinRow = new LinearLayout(this);
        skinRow.setOrientation(LinearLayout.HORIZONTAL);
        skinRow.setGravity(Gravity.CENTER_VERTICAL);
        skinRow.setPadding(0, dp(4), 0, 0);
        for (int s = 0; s < 6; s++) {
            final int si = s;
            TextView dot = new TextView(this);
            int size = dp(28);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(SKIN_COLORS[s]);
            dg.setStroke(s == skin[0] ? dp(3) : dp(1),
                    s == skin[0] ? uvAccent : divider);   // v9.69
            dot.setBackground(dg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(size, size);
            dlp.rightMargin = dp(8);
            dot.setLayoutParams(dlp);
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    skin[0] = si;
                    sp.edit().putInt("skin", si).apply();
                    refreshSkinDots(skinRow, si, uvAccent, divider);   // v9.69
                    skinNote.setText(SKIN_NAMES[si]);
                    updateBurnText(burnTv, burnNote, spfTv, uv, skin[0], lv);
                }
            });
            skinRow.addView(dot);
        }
        box.addView(skinRow);
        skinNote.setText(SKIN_NAMES[skin[0]]);
        box.addView(skinNote);

        // 晒伤时间（按肤质 + 当前 UV 实时计算，UVLens 核心）
        box.addView(burnTv);
        box.addView(burnNote);

        updateBurnText(burnTv, burnNote, spfTv, uv, skin[0], lv);

        // 暴露损害程度 + 补涂提醒（UVLens 特色）+ 防护建议
        addInfoRow(box, "损害程度", UV_DAMAGE[lv], txtColor);
        box.addView(spfTv);
        addAdviceRow(box, UV_ADVICE[lv], txtColor, dark, uvColor);

        sv.addView(box, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        showPanel(d, sv);
    }

    /** v9.67：刷新肤质色块选中描边 */
    private void refreshSkinDots(LinearLayout row, int sel, int accent, int divider) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            if (v.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) v.getBackground()).setStroke(
                        i == sel ? dp(3) : dp(1), i == sel ? accent : divider);
            }
        }
    }

    /** v9.68：按肤质 + 当前 UV 更新晒伤时间与防晒建议（随肤质联动） */
    private void updateBurnText(TextView burnTv, TextView burnNote, TextView spfTv,
                                double uv, int skin, int lv) {
        if (uv < 0.3) {
            burnTv.setText("晒伤风险极低，可安心户外活动");
        } else {
            int min = (int) Math.round(SKIN_K[skin] / uv);
            burnTv.setText("约 " + burnMinText(min) + " 晒伤");
        }
        burnNote.setText("当前 UV " + String.format(Locale.US, "%.1f", uv)
                + " 下按「" + SKIN_NAMES[skin] + "」估算（不涂防晒）");
        spfTv.setText("补涂提醒：" + spfAdvice(lv, skin));
    }

    /** v9.68：防晒建议按肤质分组（浅 I-II / 中 III-IV / 深 V-VI）联动 */
    private static String spfAdvice(int lv, int skin) {
        int g = skin <= 1 ? 0 : (skin <= 3 ? 1 : 2);
        switch (lv) {
            case 0:
                return g == 0 ? "建议 SPF30+ · 户外每 3 小时补涂"
                        : (g == 1 ? "无需频繁补涂 · 长时间户外建议 SPF15+" : "无需特别防护");
            case 1:
                return g == 0 ? "建议 SPF50+ · 户外每 2 小时补涂"
                        : "建议 SPF30+ · 户外每 3 小时补涂";
            case 2:
                return g == 0 ? "建议 SPF50+ PA++++ · 每 2 小时补涂"
                        : (g == 1 ? "建议 SPF30+ · 每 2 小时补涂" : "建议 SPF30+ · 每 3 小时补涂");
            case 3:
                return g == 0 ? "建议 SPF50+ PA++++ · 每 1.5 小时补涂"
                        : (g == 1 ? "建议 SPF50+ · 每 2 小时补涂" : "建议 SPF50+ · 每 2–3 小时补涂");
            default:
                return "建议 SPF50+ PA++++ · 尽量留在室内";
        }
    }

    private static String burnMinText(int min) {
        if (min >= 480) return "8 小时以上";
        if (min >= 60) {
            int h = min / 60;
            int m = min % 60;
            return h + " 小时" + (m == 0 ? "" : " " + m + " 分");
        }
        return min + " 分钟";
    }

    // ============ 动效 ============

    /** 温度数字滚动 */
    private void animateTemp(double target) {
        final float from = Float.isNaN(currentTemp) ? (float) target : currentTemp;
        currentTemp = (float) target;
        ValueAnimator va = ValueAnimator.ofFloat(from, (float) target);
        va.setDuration(650);
        va.setInterpolator(new DecelerateInterpolator());
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                tempText.setText(Math.round((Float) a.getAnimatedValue()) + "°");
            }
        });
        va.start();
    }

    /** 背景渐变色平滑过渡 */
    private void animateBg(final int[] to) {
        if (Arrays.equals(bgColors, to)) return;
        final int[] from = bgColors;
        bgColors = to;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(900);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = a.getAnimatedFraction();
                int[] cur = new int[3];
                for (int i = 0; i < 3; i++) cur[i] = lerp(from[i], to[i], t);
                setGradient(cur);
            }
        });
        va.start();
    }

    /** 首次加载：各区块依次浮入 */
    private void popIn() {
        fadeIn(findViewById(R.id.sunCard), 0);
        fadeIn(findViewById(R.id.mapCard), 80);
        fadeIn(findViewById(R.id.reportCard), 120);
        fadeIn(findViewById(R.id.titleHourly), 200);
        fadeIn(findViewById(R.id.hourlyScroll), 280);
        fadeIn(findViewById(R.id.titleDaily), 360);
        fadeIn(findViewById(R.id.dailyList), 440);
    }

    private void fadeIn(final View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(22));
        v.animate().alpha(1f).translationY(0).setDuration(550)
                .setStartDelay(delay).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void setGradient(int[] colors) {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors);
        rootFrame.setBackground(bg);
    }

        // ---------- 毛玻璃 ----------

    /** 截取渐变底色并缩小为 1/4 模糊快照，重铺到玻璃卡片（粒子实时透过，内外同步） */
    private void refreshGlass() {
        try {
            int vw = weatherBg.getWidth(), vh = weatherBg.getHeight();
            if (vw <= 0 || vh <= 0) return;
            int sw = Math.max(vw / 4, 1), sh = Math.max(vh / 4, 1);
            Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            // 1) 画当前渐变底色（静态，与根背景一致）
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM, bgColors);
            g.setBounds(0, 0, sw, sh);
            g.draw(c);
            // 注意：不把粒子画进快照 —— 粒子由底层 WeatherBackView 实时
            // 透过半透明玻璃显示，玻璃内外完全同步，避免重影/断层撕裂
            glassCache = small;
            applyGlass();
        } catch (Throwable ignored) {
            // 毛玻璃失败不影响主流程
        }
    }

    /** 给玻璃卡片铺上当前模糊快照（按窗口位置裁切）；透明度由设置档位换算 */
    private void applyGlass() {
        if (glassCache == null || glassCards == null) return;
        int alpha = Theme.glassAlpha(this);
        int[] loc = new int[2];
        for (View card : glassCards) {
            if (card == null) continue;
            card.getLocationInWindow(loc);
            GlassDrawable gd = new GlassDrawable(
                    glassCache, loc[0], loc[1], dp(22), 4,
                    Theme.glassHighlight(this), Theme.glassBorder(this));
            gd.setAlpha(alpha);
            card.setBackground(gd);
        }
    }

    /** v9.78：自绘 M3 时间选择器回调 */
    private interface TimeSetListener {
        void onTimeSet(int hour, int minute);
    }

    /** v9.78：自绘 M3 时间选择器（替代系统 TimePickerDialog）：
     *  24 小时制，小时/分钟两列 ▲▼ 步进（长按连发），大数字显示，深浅主题适配 */
    private void showTimePicker(final int initHour, final int initMinute,
                                final TimeSetListener listener) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final boolean dark = Theme.isDark(this);
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
            d.getWindow().setDimAmount(0.35f);
            WindowManager.LayoutParams lp = d.getWindow().getAttributes();
            lp.width = (int) Math.min(
                    getResources().getDisplayMetrics().widthPixels
                            - 48 * getResources().getDisplayMetrics().density,
                    320 * getResources().getDisplayMetrics().density);
            d.getWindow().setAttributes(lp);
        }
        final int bg = dark ? 0xFF2B2D33 : 0xFFE6E9F0;
        final int titleC = dark ? 0xFFFFFFFF : 0xFF1F2A36;
        final int subC = dark ? 0x88FFFFFF : 0xFF66717E;
        final int accent = dark ? 0xFF8AB4F8 : 0xFF2F6FEB;

        final int[] hour = {initHour};
        final int[] minute = {initMinute};

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, dp(20));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(dp(28));
        root.setBackground(gd);

        TextView title = new TextView(this);
        title.setText("通知时间");
        title.setTextColor(titleC);
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("24 小时制");
        sub.setTextColor(subC);
        sub.setTextSize(13);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        sub.setLayoutParams(slp);
        root.addView(sub);

        // 选择区：小时 ▲数字▼ : 分钟 ▲数字▼
        LinearLayout picker = new LinearLayout(this);
        picker.setOrientation(LinearLayout.HORIZONTAL);
        picker.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(10);
        picker.setLayoutParams(plp);

        // v9.79：滚轮选择——小时/分钟两个自绘 WheelView（上下滑动换值，循环滚动）
        WheelView hourWheel = new WheelView(this);
        hourWheel.setRange(0, 23, initHour);
        hourWheel.setOnValueChangedListener(new WheelView.OnValueChangedListener() {
            @Override
            public void onValueChanged(WheelView w, int v) { hour[0] = v; }
        });

        WheelView minuteWheel = new WheelView(this);
        minuteWheel.setRange(0, 59, initMinute);
        minuteWheel.setOnValueChangedListener(new WheelView.OnValueChangedListener() {
            @Override
            public void onValueChanged(WheelView w, int v) { minute[0] = v; }
        });

        picker.addView(hourWheel);
        TextView colon = new TextView(this);
        colon.setText(":");
        colon.setTextColor(titleC);
        colon.setTextSize(40);
        colon.setGravity(Gravity.CENTER);
        colon.setTypeface(colon.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams colp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        colp.leftMargin = dp(4);
        colp.rightMargin = dp(4);
        colon.setLayoutParams(colp);
        picker.addView(colon);
        picker.addView(minuteWheel);
        root.addView(picker);

        // 按钮行：取消（outlined）+ 确定（filled）
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(24);
        btns.setLayoutParams(blp);

        TextView cancel = btn("取消", dark ? 0xFF9AA0A8 : 0xFF6B7280,
                0x00000000, dark ? 0xFF2A3644 : 0xFFE5EAF0);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { d.dismiss(); }
        });
        btns.addView(cancel);

        TextView ok = btn("确定", dark ? 0xFF1F1F1F : 0xFFFFFFFF, accent, 0x00000000);
        LinearLayout.LayoutParams okp = (LinearLayout.LayoutParams) ok.getLayoutParams();
        okp.leftMargin = dp(10);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onTimeSet(hour[0], minute[0]);
                d.dismiss();
            }
        });
        btns.addView(ok);
        root.addView(btns);

        d.setContentView(root);
        Fonts.apply(root);
        d.show();
    }

    /** v9.78：Bottom Sheet 下滑关闭动画（滑出 + 淡出） */
    private void dismissSheet(final Dialog d, final View sheet) {
        final int h = Math.max(sheet.getHeight(), dp(300));
        sheet.animate().translationY(h).alpha(0f).setDuration(180)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() { d.dismiss(); }
                }).start();
    }

    /** 外观设置弹窗（Bottom Sheet）：主题选择（跟随系统 / 深色 / 浅色），选中即生效 */
    /** v9.87：设置面板 —— 横向分页（外观 / 定位与后台 / 天气源）+ 防误触 + 自定义天气源 */
    /** v9.87.3：设置改为系统悬浮窗（SYSTEM_ALERT_WINDOW），可拖动、贴底半屏、进出场动效保留 */
    private void showSettingsDialog() {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final boolean dark = Theme.isDark(this);
        final int bg = Theme.setBg(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        // v9.87：设置改为「半屏底部弹窗：一级列表 -> 二级页面」结构
        final LinearLayout listPage = new LinearLayout(this);
        listPage.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout detailPage = new LinearLayout(this);
        detailPage.setOrientation(LinearLayout.VERTICAL);
        detailPage.setVisibility(View.GONE);

        // 一级页顶部：标题 + 完成
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(16);
        header.setLayoutParams(hlp);

        TextView titleTv = stTitle("设置", 20, dark);
        titleTv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(20));   // v9.87.1：锁定字号，防系统大字缩放
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(titleTv);

        TextView done = new TextView(this);
        done.setText("完成");
        done.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, dp(14));      // v9.87.1：锁定字号，防系统大字缩放
        done.setTypeface(done.getTypeface(), android.graphics.Typeface.BOLD);
        int accent = Theme.setAccent(this);
        done.setTextColor(accent);
        // v9.87.1：胶囊形按钮，观感更完整
        GradientDrawable doneBg = new GradientDrawable();
        doneBg.setColor((accent & 0x00FFFFFF) | 0x26000000);
        doneBg.setCornerRadius(dp(15));
        done.setBackground(doneBg);
        done.setPadding(dp(16), dp(6), dp(16), dp(6));
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { d.dismiss(); }
        });
        header.addView(done);
        listPage.addView(header);

        stEntry(listPage, "外观", "深色 / 浅色 / 跟随系统", themeLabel(), dark, new Runnable() {
            @Override public void run() {
                openSettingsDetail(dark, listPage, detailPage, "外观",
                        buildAppearancePage(dark, d, null));
            }
        });
        stEntry(listPage, "定位与后台", "定位方式 · 后台预警 · 常驻", locLabel(), dark, new Runnable() {
            @Override public void run() {
                openSettingsDetail(dark, listPage, detailPage, "定位与后台",
                        buildLocBgPage(dark, d, null));
            }
        });
        stEntry(listPage, "天气源", "数据来源 · 可切换国内主流 API",
                WeatherSources.label(this), dark, new Runnable() {
                    @Override public void run() {
                        openSettingsDetail(dark, listPage, detailPage, "天气源",
                                buildSourcePage(dark, d, null));
                    }
                });
        stEntry(listPage, "自定义提醒", "温度 · 湿度 · 紫外线超阈值提醒",
                customAlertLabel(), dark, new Runnable() {
                    @Override public void run() {
                        openSettingsDetail(dark, listPage, detailPage, "自定义提醒",
                                buildCustomAlertPage(dark, d, null));
                    }
                });

        root.addView(listPage);
        root.addView(detailPage);

        d.setContentView(root);
        Fonts.apply(root);

        // v9.87.4：全屏设置面板——主题底色铺满全屏（含状态栏与底部手势条区域）
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(bg);
        root.setBackground(sheetBg);

        if (d.getWindow() != null) {
            Window w = d.getWindow();
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setDimAmount(0f);                         // 全屏面板，无需压暗主界面
            w.setWindowAnimations(R.style.SettingsSheetAnim);   // 底部滑入/滑出动效
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            // v9.87.1：窗口延伸到系统栏之后，insets 才能回传给 root 做避让；
            // 状态栏/手势条区域由窗口背景（主题底色）铺满，图标深浅随主题
            int vis = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            if (Build.VERSION.SDK_INT >= 23 && !Theme.isDark(this)) {
                vis |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;     // 浅色主题：状态栏深色图标
            }
            if (Build.VERSION.SDK_INT >= 26 && !Theme.isDark(this)) {
                vis |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; // 浅色主题：手势条深色
            }
            w.getDecorView().setSystemUiVisibility(vis);
            if (Build.VERSION.SDK_INT >= 30) {
                w.setDecorFitsSystemWindows(false);
            }
            w.setStatusBarColor(bg);                    // 兜底：系统栏颜色 = 主题底色
            w.setNavigationBarColor(bg);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;  // v9.87.4：全屏
            lp.gravity = Gravity.BOTTOM;
            w.setAttributes(lp);
        }
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int bottom = 0;
                int top = 0;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets sb = insets.getInsets(WindowInsets.Type.systemBars());
                    android.graphics.Insets im = insets.getInsets(WindowInsets.Type.ime());
                    android.graphics.Insets mg = insets.getInsets(
                            WindowInsets.Type.mandatorySystemGestures());
                    bottom = Math.max(Math.max(sb.bottom, im.bottom), mg.bottom);
                    top = sb.top;
                } else if (Build.VERSION.SDK_INT >= 29) {
                    bottom = Math.max(insets.getSystemWindowInsetBottom(),
                            insets.getMandatorySystemGestureInsets().bottom);
                    top = insets.getSystemWindowInsetTop();
                } else {
                    bottom = insets.getSystemWindowInsetBottom();
                    top = insets.getSystemWindowInsetTop();
                }
                // v9.87.4：顶部避让状态栏、底部避让手势条/导航栏/键盘，其余铺主题底色
                v.setPadding(dp(20), top + dp(14), dp(20), bottom + dp(18));
                return insets;
            }
        });

        // 返回键：二级页动画返回一级，一级页关闭
        d.setCancelable(false);
        d.setCanceledOnTouchOutside(false);
        settingsInDetail = false;
        d.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface di, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    handleSettingsBack(d, listPage, detailPage);
                    return true;
                }
                return false;
            }
        });

        d.show();
    }

    /** 一级列表入口行：标题 + 副标题 + 当前值 + 右箭头 */
    private void stEntry(LinearLayout parent, String title, String sub, String value,
                         boolean dark, final Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(6);
        row.setLayoutParams(rlp);
        row.setBackgroundResource(dark ? R.drawable.bg_opt_row : R.drawable.bg_opt_row_light);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        // v9.87：按压波纹反馈
        if (Build.VERSION.SDK_INT >= 23) {
            row.setForeground(new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(
                            dark ? 0x26FFFFFF : 0x1A000000), null, null));
        }

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(Theme.textPrimary(this));
        col.addView(t);

        if (sub != null) {
            TextView sv = new TextView(this);
            sv.setText(sub);
            sv.setTextSize(11);
            sv.setTextColor(Theme.textSecondary(this));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(2);
            sv.setLayoutParams(slp);
            col.addView(sv);
        }
        row.addView(col);

        if (value != null && !value.isEmpty()) {
            TextView val = new TextView(this);
            val.setText(value);
            val.setTextSize(12);
            val.setTextColor(Theme.setAccent(this));
            val.setGravity(Gravity.RIGHT);
            val.setPadding(dp(8), 0, 0, 0);
            row.addView(val);
        }

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(20);
        arrow.setTextColor(Theme.textHint(this));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = dp(8);
        arrow.setLayoutParams(alp);
        row.addView(arrow);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onClick.run(); }
        });
        parent.addView(row);
    }

    /** 打开二级设置页：左上角返回按钮 + 标题 + 占满剩余空间的可滚动内容（带推入动效） */
    private void openSettingsDetail(final boolean dark, final LinearLayout listPage,
                                    final LinearLayout detailPage,
                                    final String title, final View content) {
        detailPage.removeAllViews();
        settingsInDetail = true;

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.bottomMargin = dp(8);
        header.setLayoutParams(hlp);

        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setTypeface(back.getTypeface(), android.graphics.Typeface.BOLD);
        back.setTextColor(Theme.setAccent(this));
        back.setPadding(0, dp(4), dp(14), dp(4));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                animatePageSwitch(listPage, detailPage, false, new Runnable() {
                    @Override public void run() {
                        detailPage.removeAllViews();
                        settingsInDetail = false;
                    }
                });
            }
        });
        header.addView(back);

        TextView titleTv = stTitle(title, 18, dark);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(titleTv);
        detailPage.addView(header);

        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);
        sc.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        sc.addView(content);
        detailPage.addView(sc);

        // v9.87.2：等 detailPage 完成首次布局再播放滑入动画，避免首帧空白闪烁
        detailPage.post(new Runnable() {
            @Override public void run() {
                animatePageSwitch(listPage, detailPage, true, null);
            }
        });
    }

    /** v9.87.2：设置一级/二级页切换动效——详情页右滑入/右滑出 + 淡入淡出，避免双页重叠闪烁 */
    private void animatePageSwitch(final LinearLayout listPage,
                                   final LinearLayout detailPage,
                                   final boolean forward, final Runnable onEnd) {
        if (settingsAnimating) return;   // 防抖：动画进行中忽略重复触发
        settingsAnimating = true;
        float w = getResources().getDisplayMetrics().widthPixels;
        final float detFromX = forward ? w * 0.45f : 0f;
        final float detToX = forward ? 0f : w * 0.45f;
        final float detFromA = forward ? 0f : 1f;
        final float detToA = forward ? 1f : 0f;

        listPage.setVisibility(View.GONE);
        detailPage.setVisibility(View.VISIBLE);
        detailPage.setTranslationX(detFromX);
        detailPage.setAlpha(detFromA);

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(forward ? 280 : 220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) {
                float f = va.getAnimatedFraction();
                detailPage.setTranslationX(detFromX + (detToX - detFromX) * f);
                detailPage.setAlpha(detFromA + (detToA - detFromA) * f);
            }
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                detailPage.setTranslationX(0f);
                detailPage.setAlpha(1f);
                if (!forward) {
                    detailPage.setVisibility(View.GONE);
                    listPage.setVisibility(View.VISIBLE);
                }
                settingsAnimating = false;
                if (onEnd != null) onEnd.run();
            }
        });
        anim.start();
    }

    private String themeLabel() {
        String m = Theme.mode(this);
        if ("dark".equals(m)) return "深色";
        if ("light".equals(m)) return "浅色";
        return "跟随系统";
    }

    private String locLabel() {
        if ("gps".equals(locChoice)) return "GPS 定位";
        if ("ip".equals(locChoice)) return "IP 定位";
        return "自动切换";
    }

    private String customAlertLabel() {
        if (!CustomAlert.enabled(this)) return "未开启";
        int n = 0;
        for (String k : CustomAlert.keys()) {
            if (!CustomAlert.get(this, k).trim().isEmpty()) n++;
        }
        return n > 0 ? "已开启 · " + n + " 项阈值" : "已开启 · 未设阈值";
    }

    // ============ v9.87：设置面板动态构建辅助 ============

    private static class OptionRow {
        final LinearLayout row;
        final View radio;
        OptionRow(LinearLayout r, View b) { row = r; radio = b; }
    }

    private TextView stTitle(String text, float sp, boolean dark) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        tv.setTextColor(Theme.textPrimary(this));
        return tv;
    }

    private void stSection(LinearLayout parent, String title, String sub, boolean dark) {
        TextView t = stTitle(title, 18, dark);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        t.setLayoutParams(lp);
        parent.addView(t);
        if (sub != null) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(12);
            s.setTextColor(Theme.textSecondary(this));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(6);
            s.setLayoutParams(slp);
            parent.addView(s);
        }
    }

    private OptionRow stOption(LinearLayout parent, String tag, String title, String sub,
                               boolean dark, boolean checked, final SettingsPager pager,
                               final Runnable onPick) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTag(tag);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(6);
        row.setLayoutParams(rlp);
        row.setBackgroundResource(dark ? R.drawable.bg_opt_row : R.drawable.bg_opt_row_light);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setSelected(checked);

        View radio = new View(this);
        radio.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        radio.setBackgroundResource(dark ? R.drawable.bg_radio_selector : R.drawable.bg_radio_selector_light);
        radio.setSelected(checked);
        row.addView(radio);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(14);
        col.setLayoutParams(clp);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(Theme.textPrimary(this));
        col.addView(t);

        if (sub != null) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(11);
            s.setTextColor(Theme.textSecondary(this));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(2);
            s.setLayoutParams(slp);
            col.addView(s);
        }
        row.addView(col);

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (pager != null && pager.isScrolling()) return;   // 防误触 3a
                if (onPick != null) onPick.run();
            }
        });

        parent.addView(row);
        return new OptionRow(row, radio);
    }

    private TextView stButton(String text, boolean dark, boolean highlight) {
        final TextView btn = new TextView(this);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(14);
        btn.setTypeface(btn.getTypeface(), android.graphics.Typeface.BOLD);
        btn.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        btn.setLayoutParams(lp);
        btn.setBackgroundResource(dark ? R.drawable.bg_opt_row : R.drawable.bg_opt_row_light);
        btn.setSelected(highlight);
        btn.setTextColor(Theme.setAccent(this));
        return btn;
    }

    private void stTextInput(LinearLayout parent, final EditText input, String hint, boolean dark) {
        input.setSingleLine(true);
        input.setHint(hint);
        input.setHintTextColor(Theme.textHint(this));
        input.setTextColor(Theme.textPrimary(this));
        input.setTextSize(14);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        input.setLayoutParams(lp);
        // v9.87：输入框加描边，避免与底色糊在一起
        GradientDrawable inBg = new GradientDrawable();
        inBg.setColor(Theme.setInputBg(this));
        inBg.setCornerRadius(dp(14));
        inBg.setStroke(dp(1), Theme.setDivider(this));
        input.setBackground(inBg);
        parent.addView(input);
    }

    private void stSwitchRow(LinearLayout parent, String title, String sub, boolean dark, M3Switch sw) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(6);
        row.setLayoutParams(rlp);
        row.setBackgroundResource(dark ? R.drawable.bg_opt_row : R.drawable.bg_opt_row_light);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(Theme.textPrimary(this));
        col.addView(t);

        if (sub != null) {
            TextView s = new TextView(this);
            s.setText(sub);
            s.setTextSize(11);
            s.setTextColor(Theme.textSecondary(this));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dp(2);
            s.setLayoutParams(slp);
            col.addView(s);
        }
        row.addView(col);
        row.addView(sw);
        parent.addView(row);
    }

    // ============ 分页内容 ============

    private LinearLayout buildAppearancePage(final boolean dark, final Dialog d, final SettingsPager pager) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        stSection(page, "外观主题", "选择界面主题，切换立即生效", dark);
        final String cur = Theme.mode(this);
        stOption(page, "system", "跟随系统", "随系统设置自动切换深色或浅色", dark,
                "system".equals(cur), pager, new Runnable() {
                    @Override public void run() { pickTheme("system", d); }
                });
        stOption(page, "dark", "深色", "深色天空渐变 · 夜间更护眼", dark,
                "dark".equals(cur), pager, new Runnable() {
                    @Override public void run() { pickTheme("dark", d); }
                });
        stOption(page, "light", "浅色", "明亮天空渐变 · 白天更清爽", dark,
                "light".equals(cur), pager, new Runnable() {
                    @Override public void run() { pickTheme("light", d); }
                });

        // v9.90：界面引擎（经典 View / Compose 新界面）
        stSection(page, "界面引擎", "实验性 Compose 界面，可在两套 UI 间随时切换", dark);
        stOption(page, "engine_view", "经典界面", "Java View · 稳定默认", dark,
                !Theme.isCompose(this), pager, new Runnable() {
                    @Override public void run() { pickEngine("view", d); }
                });
        stOption(page, "engine_compose", "Compose 新界面", "Compose + Material 3 · 实验性", dark,
                Theme.isCompose(this), pager, new Runnable() {
                    @Override public void run() { pickEngine("compose", d); }
                });

        return page;
    }

    private void pickTheme(String m, Dialog d) {
        if (m.equals(Theme.mode(this))) { d.dismiss(); return; }
        Theme.setMode(this, m);
        reopenSettings = true;
        recreate();
    }

    /** v9.90：切换界面引擎（compose 时立即进入新界面；view 时留在经典界面） */
    private void pickEngine(String e, Dialog d) {
        if (Theme.ENGINE_COMPOSE.equals(e) && Theme.isCompose(this)) { d.dismiss(); return; }
        if (Theme.ENGINE_VIEW.equals(e) && !Theme.isCompose(this)) { d.dismiss(); return; }
        Theme.setEngine(this, e);
        d.dismiss();
        if (Theme.ENGINE_COMPOSE.equals(e)) {
            startActivity(new Intent(this, ComposeWeatherActivity.class));
            finish();
        } else {
            recreate();
        }
    }

    private LinearLayout buildLocBgPage(final boolean dark, final Dialog d, final SettingsPager pager) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        stSection(page, "定位方式", "刷新时按所选方式重新定位", dark);
        final OptionRow[] rows = new OptionRow[3];
        rows[0] = stOption(page, "auto", "自动切换", "GPS 可用时优先，否则 IP 定位", dark,
                "auto".equals(locChoice), pager, new Runnable() {
                    @Override public void run() { pickLoc("auto", rows); }
                });
        rows[1] = stOption(page, "gps", "GPS 定位", "卫星 / 网络定位 · 更精准", dark,
                "gps".equals(locChoice), pager, new Runnable() {
                    @Override public void run() { pickLoc("gps", rows); }
                });
        rows[2] = stOption(page, "ip", "IP 定位", "按网络 IP 估算 · 结果可能有偏差", dark,
                "ip".equals(locChoice), pager, new Runnable() {
                    @Override public void run() { pickLoc("ip", rows); }
                });

        // v9.87-fix：导出诊断日志（SAF 保存到用户指定位置，任何 ROM 都可用）
        LinearLayout expRow = new LinearLayout(this);
        expRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams expLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        expLp.topMargin = dp(6);
        expRow.setLayoutParams(expLp);
        expRow.setBackgroundResource(dark ? R.drawable.bg_opt_row : R.drawable.bg_opt_row_light);
        expRow.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView expT = new TextView(this);
        expT.setText("导出诊断日志");
        expT.setTextSize(15);
        expT.setTextColor(Theme.textPrimary(this));
        expRow.addView(expT);
        TextView expS = new TextView(this);
        expS.setText("当前：" + LogFile.state());
        expS.setTextSize(11);
        expS.setTextColor(Theme.textSecondary(this));
        LinearLayout.LayoutParams expSlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        expSlp.topMargin = dp(3);
        expS.setLayoutParams(expSlp);
        expRow.addView(expS);
        expRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent ei = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                ei.addCategory(Intent.CATEGORY_OPENABLE);
                ei.setType("text/plain");
                ei.putExtra(Intent.EXTRA_TITLE, LogFile.fileName());
                startActivityForResult(ei, REQ_EXPORT_LOG);
            }
        });
        page.addView(expRow);

        stSection(page, "后台与推送", null, dark);

        final M3Switch alertSw = new M3Switch(this);
        alertSw.setChecked(AlertWatcher.enabled(this));
        stSwitchRow(page, "后台预警监控", "每 30 分钟检查，黄色及以上自动提醒", dark, alertSw);
        alertSw.setOnCheckedChangeListener(new M3Switch.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(M3Switch sw, boolean on) {
                if (on && Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            "android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
                }
                AlertWatcher.setEnabled(MainActivity.this, on);
                Toast.makeText(MainActivity.this,
                        on ? "已开启：每 30 分钟检查预警，黄色及以上自动提醒"
                           : "已关闭后台预警监控", Toast.LENGTH_SHORT).show();
            }
        });

        final M3Switch keepSw = new M3Switch(this);
        keepSw.setChecked(KeepAliveManager.enabled(this));
        stSwitchRow(page, "后台常驻", "划掉后台任务后仍保持推送与自动刷新", dark, keepSw);
        keepSw.setOnCheckedChangeListener(new M3Switch.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(M3Switch sw, boolean on) {
                KeepAliveManager.setEnabled(MainActivity.this, on);
                Toast.makeText(MainActivity.this,
                        on ? "已开启后台常驻：划掉任务后仍保持推送与自动刷新"
                           : "已关闭后台常驻", Toast.LENGTH_SHORT).show();
            }
        });

        TextView hint = new TextView(this);
        hint.setText("若系统仍拦截后台，请点击此处前往系统设置，允许自启动与忽略电池优化");
        hint.setTextSize(12);
        hint.setTextColor(dark ? 0x66FFFFFF : 0xFF8A93A0);
        hint.setPadding(dp(14), dp(6), dp(14), 0);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(6);
        hint.setLayoutParams(hlp);
        hint.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent si = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName()));
                    si.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(si);
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    } catch (Exception e2) {
                        Toast.makeText(MainActivity.this,
                                "请到系统设置中允许本应用自启动", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
        page.addView(hint);
        return page;
    }

    private void pickLoc(String m, OptionRow[] rows) {
        locChoice = m;
        getSharedPreferences("loc_pref", MODE_PRIVATE).edit().putString("loc_choice", m).apply();
        LogFile.i("Main", "定位模式切换 -> " + m);
        // v9.87-fix：auto/gps 模式均启动 GPS 续等监听（gps 失败后 fix 迟到可自动升级）
        if ("auto".equals(m) || "gps".equals(m)) startGpsWatch(); else stopGpsWatch();
        startLoad(false);
        for (OptionRow r : rows) {
            boolean sel = m.equals(r.row.getTag());
            r.row.setSelected(sel);
            r.radio.setSelected(sel);
        }
    }

    private LinearLayout buildCustomAlertPage(final boolean dark, final Dialog d, final SettingsPager pager) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        stSection(page, "自定义气象提醒",
                "当前气温 / 湿度 / 紫外线指数超过阈值时推送提醒，留空表示不启用该项", dark);

        final M3Switch sw = new M3Switch(this);
        sw.setChecked(CustomAlert.enabled(this));
        stSwitchRow(page, "开启提醒", "天气刷新后自动检查当前实况", dark, sw);
        sw.setOnCheckedChangeListener(new M3Switch.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(M3Switch s, boolean on) {
                if (on && Build.VERSION.SDK_INT >= 33
                        && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{
                            "android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
                }
                CustomAlert.setEnabled(MainActivity.this, on);
                Toast.makeText(MainActivity.this,
                        on ? "已开启自定义气象提醒" : "已关闭自定义气象提醒",
                        Toast.LENGTH_SHORT).show();
            }
        });

        stSection(page, "阈值设定", null, dark);

        final String[] keys = CustomAlert.keys();
        final String[] labels = {"温度上限（°C）", "温度下限（°C）", "湿度上限（%）", "湿度下限（%）", "紫外线指数上限"};
        final String[] hints = {"如 35", "如 -5", "如 90", "如 20", "如 8"};
        final EditText[] inputs = new EditText[keys.length];
        for (int i = 0; i < keys.length; i++) {
            TextView t = new TextView(this);
            t.setText(labels[i]);
            t.setTextSize(13);
            t.setTextColor(Theme.textSecondary(this));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = dp(10);
            t.setLayoutParams(tlp);
            page.addView(t);

            EditText in = new EditText(this);
            in.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                    | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            in.setText(CustomAlert.get(this, keys[i]));
            stTextInput(page, in, hints[i], dark);
            inputs[i] = in;
        }

        TextView save = stButton("保存阈值", dark, true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (int i = 0; i < keys.length; i++) {
                    CustomAlert.put(MainActivity.this, keys[i],
                            inputs[i].getText().toString().trim());
                }
                Toast.makeText(MainActivity.this, "阈值已保存，天气刷新后自动检查",
                        Toast.LENGTH_SHORT).show();
            }
        });
        page.addView(save);

        return page;
    }

    private LinearLayout buildSourcePage(final boolean dark, final Dialog d, final SettingsPager pager) {
        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        stSection(page, "天气源", "选择数据来源", dark);

        final String initType = WeatherSources.type(this);
        final String[] selType = { initType };
        final OptionRow[] rows = new OptionRow[5];
        final EditText keyInput = new EditText(this);
        final EditText hostInput = new EditText(this);
        final TextView status = new TextView(this);

        rows[0] = stOption(page, WeatherSources.OPEN_METEO, "Open-Meteo", "备用源 · 无需密钥", dark,
                WeatherSources.OPEN_METEO.equals(initType), pager, new Runnable() {
                    @Override public void run() {
                        selType[0] = WeatherSources.OPEN_METEO;
                        refreshSourceUi(rows, selType[0], keyInput, hostInput);
                    }
                });
        rows[1] = stOption(page, WeatherSources.QWEATHER, "和风天气", "免费官方源 · 需 Key", dark,
                WeatherSources.QWEATHER.equals(initType), pager, new Runnable() {
                    @Override public void run() {
                        selType[0] = WeatherSources.QWEATHER;
                        refreshSourceUi(rows, selType[0], keyInput, hostInput);
                    }
                });
        rows[2] = stOption(page, WeatherSources.SENIVERSE, "心知天气", "需 API Key", dark,
                WeatherSources.SENIVERSE.equals(initType), pager, new Runnable() {
                    @Override public void run() {
                        selType[0] = WeatherSources.SENIVERSE;
                        refreshSourceUi(rows, selType[0], keyInput, hostInput);
                    }
                });
        rows[3] = stOption(page, WeatherSources.CAIYUN, "彩云天气", "需 Token", dark,
                WeatherSources.CAIYUN.equals(initType), pager, new Runnable() {
                    @Override public void run() {
                        selType[0] = WeatherSources.CAIYUN;
                        refreshSourceUi(rows, selType[0], keyInput, hostInput);
                    }
                });
        rows[4] = stOption(page, WeatherSources.AMAP, "高德天气", "需 Key", dark,
                WeatherSources.AMAP.equals(initType), pager, new Runnable() {
                    @Override public void run() {
                        selType[0] = WeatherSources.AMAP;
                        refreshSourceUi(rows, selType[0], keyInput, hostInput);
                    }
                });

        stTextInput(page, keyInput, keyHint(initType), dark);
        keyInput.setText(WeatherSources.key(this, initType));
        // v9.87-fix1：和风个人专属 API Host（自建中转/代理域名），仅和风源显示
        stTextInput(page, hostInput, "和风 API Host（选填，默认官方 devapi.qweather.com）", dark);
        hostInput.setText(WeatherSources.qHost(this));
        hostInput.setVisibility(WeatherSources.QWEATHER.equals(initType)
                ? View.VISIBLE : View.GONE);

        status.setTextSize(12);
        status.setTextColor(dark ? 0x88FFFFFF : 0xFF5C6B7A);
        status.setPadding(dp(14), dp(6), dp(14), 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(4);
        status.setLayoutParams(slp);
        status.setText("当前：" + WeatherSources.label(this));
        page.addView(status);

        final TextView testBtn = stButton("测试当前配置", dark, false);
        page.addView(testBtn);
        final TextView saveBtn = stButton("保存并应用", dark, true);
        page.addView(saveBtn);
        TextView resetBtn = stButton("恢复默认源", dark, false);
        resetBtn.setTextColor(dark ? 0xFFE88A8A : 0xFFB3261E);
        page.addView(resetBtn);

        testBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String t = selType[0];
                final String k = keyInput.getText().toString().trim();
                final String host = hostInput.getText().toString().trim();
                status.setText("正在测试…（北京坐标）");
                status.setTextColor(dark ? 0x88FFFFFF : 0xFF5C6B7A);
                testBtn.setEnabled(false);
                new Thread(new Runnable() {
                    @Override public void run() {
                        final WeatherSources.TestResult r =
                                WeatherSources.test(MainActivity.this, t, k, host);
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                testBtn.setEnabled(true);
                                if (r.ok) {
                                    status.setText("测试通过：" + r.summary);
                                    status.setTextColor(0xFF4CAF50);
                                } else {
                                    status.setText("测试失败：" + r.error);
                                    status.setTextColor(dark ? 0xFFE88A8A : 0xFFB3261E);
                                }
                            }
                        });
                    }
                }).start();
            }
        });

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String t = selType[0];
                String k = keyInput.getText().toString().trim();
                if (!WeatherSources.OPEN_METEO.equals(t) && k.isEmpty()) {
                    Toast.makeText(MainActivity.this, "请先填写 API Key", Toast.LENGTH_SHORT).show();
                    return;
                }
                WeatherSources.save(MainActivity.this, t, k);
                if (WeatherSources.QWEATHER.equals(t)) {
                    WeatherSources.saveHost(MainActivity.this,
                            hostInput.getText().toString().trim());
                }
                Toast.makeText(MainActivity.this, "已切换天气源：" + WeatherSources.label(MainActivity.this),
                        Toast.LENGTH_SHORT).show();
                status.setText("当前：" + WeatherSources.label(MainActivity.this));
                startLoad(false);
            }
        });

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("恢复默认天气源？")
                        .setMessage("将清除已保存的 API Key，恢复默认源（Open-Meteo）。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("恢复", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface di, int which) {
                                WeatherSources.reset(MainActivity.this);
                                selType[0] = WeatherSources.OPEN_METEO;
                                keyInput.setText("");
                                refreshSourceUi(rows, selType[0], keyInput, hostInput);
                                status.setText("已恢复默认源：Open-Meteo");
                                Toast.makeText(MainActivity.this, "已恢复默认天气源",
                                        Toast.LENGTH_SHORT).show();
                                startLoad(false);
                            }
                        }).show();
            }
        });

        refreshSourceUi(rows, initType, keyInput, hostInput);
        return page;
    }

    private void refreshSourceUi(OptionRow[] rows, String type, EditText keyInput,
                                 EditText hostInput) {
        for (OptionRow r : rows) {
            boolean sel = type.equals(r.row.getTag());
            r.row.setSelected(sel);
            r.radio.setSelected(sel);
        }
        boolean needKey = !WeatherSources.OPEN_METEO.equals(type);
        keyInput.setVisibility(needKey ? View.VISIBLE : View.GONE);
        if (needKey) {
            // v9.89：各源 Key 隔离，切换时加载对应源已保存的 Key
            keyInput.setHint(keyHint(type));
            keyInput.setText(WeatherSources.key(MainActivity.this, type));
        }
        // v9.87-fix1：和风专属 Host 输入框，仅和风源显示
        boolean qw = WeatherSources.QWEATHER.equals(type);
        hostInput.setVisibility(qw ? View.VISIBLE : View.GONE);
        if (qw) hostInput.setText(WeatherSources.qHost(MainActivity.this));
    }

    /** 各源 Key 输入框的专属提示文案 */
    private String keyHint(String type) {
        if (WeatherSources.CAIYUN.equals(type)) return "彩云 Token";
        if (WeatherSources.AMAP.equals(type)) return "高德 Web 服务 Key";
        if (WeatherSources.QWEATHER.equals(type)) return "和风 API Key";
        if (WeatherSources.SENIVERSE.equals(type)) return "心知 API Key";
        return "API Key";
    }

    /** 滚动/布局变化时更新玻璃裁切位置（无需重建位图） */
    private void updateGlassPositions() {
        if (glassCache == null || glassCards == null) return;
        int[] loc = new int[2];
        for (View card : glassCards) {
            if (card == null) continue;
            android.graphics.drawable.Drawable d = card.getBackground();
            if (d instanceof GlassDrawable) {
                card.getLocationInWindow(loc);
                ((GlassDrawable) d).setWindowPos(loc[0], loc[1]);
            }
        }
    }

    /** ARGB 颜色插值 */
    private static int lerp(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        return Color.rgb((int) (ar + (br - ar) * t),
                (int) (ag + (bg - ag) * t),
                (int) (ab + (bb - ab) * t));
    }

    // ============ 列表构建 ============

    /** v9.56：24 小时组件直接按卡片背景取自适应字色组（"现在"蓝/温度主字/标签次字/降水强调） */
    private void buildHourly(JSONObject json, int[] cols) throws Exception {
        JSONObject hourly = json.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code");
        JSONArray pops = hourly.optJSONArray("precipitation_probability");

        // v9.82：按「城市时区」对齐当前时刻——timezone=auto 的 hourly 时间是城市当地时间，
        // 若用设备时区（nowIso()）比较，跨时区查看时整条 24 小时线（时间/概率/温度）都会错位偏移。
        long offMs = 0;
        try { offMs = json.getLong("utc_offset_seconds") * 1000L; } catch (Exception ignored) { }
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:00", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String nowIso = f.format(new java.util.Date(System.currentTimeMillis() + offMs));
        int start = 0;
        for (int i = 0; i < times.length(); i++) {
            if (times.getString(i).compareTo(nowIso) >= 0) { start = i; break; }
        }

        // v9.67：缓存今日逐小时 UV（UVLens 式 UV 时间线数据源）
        try {
            JSONArray uvs = hourly.getJSONArray("uv_index");
            uvDay = new double[24];
            uvDayHour = new int[24];
            for (int k = 0; k < 24 && start + k < times.length(); k++) {
                uvDay[k] = uvs.getDouble(start + k);
                String t = times.getString(start + k);
                uvDayHour[k] = Integer.parseInt(t.substring(11, 13));
            }
            uvDayFailed = false;   // v9.68：拉取成功，通知弹窗内走势图刷新
            if (activeUvDay != null) activeUvDay.applyData(uvDay, uvDayHour);
        } catch (Exception ignored) {
            uvDay = null;
            uvDayHour = null;
            uvDayFailed = true;    // v9.68：逐小时 UV 解析失败
            if (activeUvDay != null) activeUvDay.setFailed();
        }

        hourlyRow.removeAllViews();
        for (int k = 0; k < 24; k++) {
            int i = start + k;
            if (i >= times.length()) break;

            // v9.83：固定等宽列（dp56）——wrap_content 时各列宽随内容参差，
            // 时间/概率等元素的中心线左右错位，视觉上呈「UI 偏移」
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(56), LinearLayout.LayoutParams.WRAP_CONTENT));
            col.setPadding(dp(4), dp(8), dp(4), dp(8));

            TextView time = new TextView(this);
            time.setText(k == 0 ? "现在" : timeLabel(times.getString(i)));
            time.setTextColor(k == 0 ? cols[2] : cols[1]);
            // v9.83：「现在」列与普通列同字号（仅加粗），保证整列行高一致不位移
            time.setTextSize(11);
            time.setGravity(Gravity.CENTER);
            time.setTypeface(time.getTypeface(), k == 0 ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);

            TextView ic = new TextView(this);
            ic.setText(WeatherApi.icon(codes.getInt(i), true));
            ic.setTextColor(cols[0]);
            ic.setTextSize(22);
            ic.setGravity(Gravity.CENTER);
            ic.setPadding(0, dp(6), 0, dp(2));

            // 天气类型标签（含强度：大雨/中雨/小雪…）
            TextView lbl = new TextView(this);
            lbl.setText(WeatherApi.label(codes.getInt(i)));
            lbl.setTextColor(cols[1]);
            lbl.setTextSize(10);
            lbl.setGravity(Gravity.CENTER);
            lbl.setSingleLine(true);
            lbl.setEllipsize(android.text.TextUtils.TruncateAt.END);
            lbl.setPadding(0, 0, 0, dp(6));

            TextView tp = new TextView(this);
            tp.setText(Math.round(temps.getDouble(i)) + "°");
            tp.setTextColor(cols[0]);
            tp.setTextSize(14);
            tp.setGravity(Gravity.CENTER);
            tp.setTypeface(tp.getTypeface(), android.graphics.Typeface.BOLD);

            // v9.59：实时降雨概率始终显示（0% 也显示）；「现在」列加粗强调
            // v9.82：null/缺字段兜底为 0（旧缓存/模型缺数不崩不偏移）
            TextView pop = new TextView(this);
            int p = (pops == null || pops.isNull(i)) ? 0 : pops.optInt(i, 0);
            pop.setText(p + "%");
            pop.setTextColor(cols[2]);
            pop.setTextSize(11);
            pop.setGravity(Gravity.CENTER);
            pop.setTypeface(pop.getTypeface(), k == 0
                    ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);

            col.addView(time);
            col.addView(ic);
            col.addView(lbl);
            col.addView(tp);
            col.addView(pop);
            Fonts.apply(col);
            ic.setTypeface(Fonts.icons());   // 小时图标用 Material Icons 字形
            hourlyRow.addView(col);
        }
    }

    /** 构建 7 天列表（含温度区间条） */
    /** v9.56：7 天组件直接按卡片背景取自适应字色组（星期/图标主字、标签/低温次字、降水/高温强调） */
    private void buildDaily(JSONObject daily, int[] cols) throws Exception {
        JSONArray times = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray maxs = daily.getJSONArray("temperature_2m_max");
        JSONArray mins = daily.getJSONArray("temperature_2m_min");
        // v9.82：字段缺失/null 兜底（旧缓存升级场景不崩）
        JSONArray pops = daily.optJSONArray("precipitation_probability_max");

        double minAll = Double.MAX_VALUE, maxAll = -Double.MAX_VALUE;
        for (int i = 0; i < maxs.length(); i++) {
            minAll = Math.min(minAll, mins.getDouble(i));
            maxAll = Math.max(maxAll, maxs.getDouble(i));
        }
        if (maxAll <= minAll) maxAll = minAll + 1;

        dailyList.removeAllViews();
        for (int i = 0; i < times.length(); i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            TextView day = new TextView(this);
            day.setText(weekLabel(times.getString(i), i));
            day.setTextColor(cols[0]);
            day.setTextSize(14);
            day.setTypeface(day.getTypeface(), i == 0 ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
            day.setWidth(dp(52));
            row.addView(day);

            // 图标 + 天气类型（竖排，含强度）
            LinearLayout icWrap = new LinearLayout(this);
            icWrap.setOrientation(LinearLayout.VERTICAL);
            icWrap.setGravity(Gravity.CENTER_HORIZONTAL);
            icWrap.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(46), LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView ic = new TextView(this);
            ic.setText(WeatherApi.icon(codes.getInt(i), true));
            ic.setTextColor(cols[0]);
            ic.setTextSize(18);
            ic.setGravity(Gravity.CENTER);

            TextView lbl = new TextView(this);
            lbl.setText(WeatherApi.label(codes.getInt(i)));
            lbl.setTextColor(cols[1]);
            lbl.setTextSize(10);
            lbl.setGravity(Gravity.CENTER);
            lbl.setSingleLine(true);
            lbl.setEllipsize(android.text.TextUtils.TruncateAt.END);
            lbl.setPadding(0, dp(2), 0, 0);

            icWrap.addView(ic);
            icWrap.addView(lbl);
            row.addView(icWrap);

            // v9.82：7 天降雨概率始终显示（与 24 小时口径一致，低概率不再隐藏）
            TextView pop = new TextView(this);
            int p = (pops == null || pops.isNull(i)) ? 0 : pops.optInt(i, 0);
            pop.setText(p + "%");
            pop.setTextColor(cols[2]);
            pop.setTextSize(12);
            pop.setWidth(dp(44));
            row.addView(pop);

            TextView lo = new TextView(this);
            lo.setText(Math.round(mins.getDouble(i)) + "°");
            lo.setTextColor(cols[1]);
            lo.setTextSize(13);
            lo.setWidth(dp(36));
            row.addView(lo);

            // 温度区间条（纯 weight 布局）
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setLayoutParams(new LinearLayout.LayoutParams(0, dp(4), 1f));
            GradientDrawable track = new GradientDrawable();
            track.setColor(Theme.trackBg(this));
            track.setCornerRadius(dp(2));
            bar.setBackground(track);

            double loW = Math.max(0, mins.getDouble(i) - minAll);
            double midW = maxs.getDouble(i) - mins.getDouble(i);
            double hiW = Math.max(0, maxAll - maxs.getDouble(i));

            LinearLayout spacerLo = new LinearLayout(this);
            spacerLo.setLayoutParams(new LinearLayout.LayoutParams(0, dp(4), (float) loW));
            bar.addView(spacerLo);

            LinearLayout fill = new LinearLayout(this);
            fill.setLayoutParams(new LinearLayout.LayoutParams(0, dp(4), (float) midW));
            GradientDrawable fillBg = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{Theme.accentLight(this), Theme.accent(this)});
            fillBg.setCornerRadius(dp(2));
            fill.setBackground(fillBg);
            bar.addView(fill);

            LinearLayout spacerHi = new LinearLayout(this);
            spacerHi.setLayoutParams(new LinearLayout.LayoutParams(0, dp(4), (float) hiW));
            bar.addView(spacerHi);

            row.addView(bar);

            TextView hi = new TextView(this);
            hi.setText(Math.round(maxs.getDouble(i)) + "°");
            hi.setTextColor(cols[0]);
            hi.setTextSize(13);
            hi.setTypeface(hi.getTypeface(), android.graphics.Typeface.BOLD);
            hi.setGravity(Gravity.RIGHT);
            hi.setWidth(dp(38));
            row.addView(hi);

            Fonts.apply(row);
            ic.setTypeface(Fonts.icons());   // 日报图标用 Material Icons 字形
            dailyList.addView(row);

            if (i < times.length() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(Theme.divider(this));
                dailyList.addView(divider, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
            }
        }
    }

    // ============ 工具 ============

    private String timeLabel(String iso) {
        return iso.length() >= 16 ? iso.substring(11, 16) : iso;
    }

    /** daily.time "2026-08-09" -> 今天/明天/周X */
    private String weekLabel(String date, int index) {
        if (index == 0) return "今天";
        if (index == 1) return "明天";
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd",
                    java.util.Locale.US);
            Calendar c = Calendar.getInstance();
            c.setTime(f.parse(date));
            return WeatherApi.WEEK[c.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) {
            return "周?";
        }
    }

    private static double distKm(double a1, double o1, double a2, double o2) {
        double R = 6371.0;
        double dLat = Math.toRadians(a2 - a1);
        double dLon = Math.toRadians(o2 - o1);
        double x = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a1)) * Math.cos(Math.toRadians(a2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(x));
    }

    /** XML 静态布局的颜色按当前主题覆盖（动态创建的 View 直接用 Theme 方法设色，不受影响） */
    private void applyThemeToTree(View v) {
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            int c = tv.getCurrentTextColor();
            if (c == 0xFFFFFFFF) tv.setTextColor(Theme.textPrimary(this));
            else if (c == 0xCCFFFFFF) tv.setTextColor(0xCC000000 | (Theme.textPrimary(this) & 0xFFFFFF));
            else if (c == 0x99FFFFFF) tv.setTextColor(0x99000000 | (Theme.textSecondary(this) & 0xFFFFFF));
            else if (c == 0xE6FFFFFF) tv.setTextColor(0xE6000000 | (Theme.accent(this) & 0xFFFFFF));
            else if (c == 0xFF1F1F1F) tv.setTextColor(Theme.textPrimary(this));
            else if (c == 0xFF5F5F5F || c == 0xFF8A8A8E) tv.setTextColor(Theme.textSecondary(this));
            else if (c == 0xFF3D7BD9 || c == 0xFF1F6FEB) tv.setTextColor(Theme.accent(this));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyThemeToTree(g.getChildAt(i));
        }
    }

    /** 背景亮度（0~1）：亮背景用深色粒子，暗背景用浅色粒子，保证始终可见 */
    /** v9.53/9.54：顶部文字跟随背景亮度切换明暗——浅色模式晚霞/晨曦/晴空等亮背景
     *  下白字与半透明白字可读性差，改深色文字；暗背景保持白色系。
     *  v9.54：暗背景半透明字提高不透明度（#CC->#E6、#99->#B3）保证对比度；
     *  亮背景弱字也从 70% 提到 75%。 */
    /** v9.55：毛玻璃卡文字自适应——卡片实际底色=背景模糊快照（+高光白），
     *  按渐变 mid 色亮度选深/浅字组，覆盖卡片内所有走主题色的文字（主/次/强调），
     *  保证浅色模式夜晚等任何时段次级文字对比度都足够明显。 */
    private void applyCardTexts(int[] pal) {
        if (glassCards == null) return;
        int[] cols = Theme.cardTextColors(pal[1]);   // mid 渐变代表卡片区域
        final int[][] pairs = {
                {Theme.textPrimary(this), cols[0]},
                {Theme.textSecondary(this), cols[1]},
                {Theme.accent(this), cols[2]},
                {0xFF1F2A36, cols[0]}, {0xFFF5F7FA, cols[0]},
                {0xFF5C6B7A, cols[1]}, {0xFFD9E2EC, cols[1]},
                {0xFF1F6FEB, cols[2]}, {0xFF3D7BD9, cols[2]},
                {0xFF2F6FEB, cols[2]}, {0xFF6FB3E8, cols[2]},
                {0xFF7EB6FF, cols[2]}, {0xFF8FC0F5, cols[2]},
        };
        for (View card : glassCards) {
            if (card != null) recolorCard(card, cols, pairs);
        }
    }

    private void recolorCard(View v, int[] cols, int[][] pairs) {
        if (v instanceof TextView) {
            int c = ((TextView) v).getCurrentTextColor();
            for (int[] pr : pairs) {
                if (c == pr[0]) { ((TextView) v).setTextColor(pr[1]); break; }
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) recolorCard(g.getChildAt(i), cols, pairs);
        }
    }

    private void applyTopColors(boolean light) {
        final int cMain = light ? 0xFF1F2A36 : 0xFFFFFFFF;
        final int cSub  = light ? 0xD95C6B7A : 0xE6FFFFFF;
        final int cWeak = light ? 0xC05C6B7A : 0xB3FFFFFF;
        cityText.setTextColor(cMain);
        tempText.setTextColor(cMain);
        descText.setTextColor(cMain);
        feelsText.setTextColor(cSub);
        sourceText.setTextColor(cSub);
        sunTimeText.setTextColor(cSub);
        regionText.setTextColor(cWeak);
        ipHintText.setTextColor(cWeak);
        if (refreshLabel != null) refreshLabel.setTextColor(cMain);
        if (cityIconTv != null) cityIconTv.setTextColor(cMain);
        if (gearIconTv != null) gearIconTv.setTextColor(cMain);
        if (titleHourlyTv != null) titleHourlyTv.setTextColor(cMain);
        if (titleDailyTv != null) titleDailyTv.setTextColor(cMain);
        if (creditTv != null) creditTv.setTextColor(cWeak);
        if (refreshIcon != null) {
            refreshIcon.setColorFilter(light ? 0xFF1F2A36 : 0xFFFFFFFF,
                    android.graphics.PorterDuff.Mode.SRC_IN);
        }
        // 阴影：暗背景白字需阴影增强对比；亮背景深字关掉避免发脏
        cityText.setShadowLayer(light ? 0f : 5f, 0f, light ? 0f : 2f,
                light ? 0 : 0x59000000);
        tempText.setShadowLayer(light ? 0f : 8f, 0f, light ? 0f : 3f,
                light ? 0 : 0x59000000);
        descText.setShadowLayer(light ? 0f : 5f, 0f, light ? 0f : 2f,
                light ? 0 : 0x59000000);
        feelsText.setShadowLayer(light ? 0f : 5f, 0f, light ? 0f : 2f,
                light ? 0 : 0x59000000);
    }

    private boolean bgBrightness(int[] pal) {
        int r = (pal[0] >> 16) & 0xFF, g = (pal[0] >> 8) & 0xFF, b = pal[0] & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 255000f > 0.55f;
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
