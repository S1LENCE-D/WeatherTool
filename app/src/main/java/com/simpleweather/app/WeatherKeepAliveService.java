package com.simpleweather.app;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * v10.1.2：可选的「常驻后台进程」（前台服务 + 一条 IMPORTANCE_MIN 常驻通知）。
 *
 * <p>作用：让进程不被系统当缓存进程回收，定时播报与预警更准时；每 15 分钟自跑一轮
 * {@link BackgroundTasks#run}。与心跳闹钟、JobScheduler 任务三者互为兜底。
 *
 * <p><b>为什么不做成"开机自启"</b>：Android 12+ 明确禁止从开机广播启动前台服务
 * （{@code ForegroundServiceStartNotAllowedException}），旧版 SpeakService 就是踩了这个坑。
 * 所以本服务只在应用内被开启/启动；重启后靠 {@link WeatherJobService} 的 persisted 任务
 * 维持后台链路，用户打开一次应用即可恢复常驻。
 *
 * <p>默认关闭（需要一条常驻通知，属于"要稳定就得接受打扰"的取舍），在设置里可开关。
 */
public class WeatherKeepAliveService extends Service {

    public static final int NOTI_ID = 9901;
    private static final long TICK_MS = 15L * 60 * 1000;
    private static volatile boolean running = false;
    private Handler h;

    public static boolean enabled(Context c) {
        return c.getSharedPreferences("ui_pref", Context.MODE_PRIVATE)
                .getBoolean("keep_alive", false);
    }

    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("ui_pref", Context.MODE_PRIVATE)
                .edit().putBoolean("keep_alive", on).apply();
    }

    public static boolean isRunning() {
        return running;
    }

    /** 应用内启动（后台调用会被系统拒绝，静默失败由 Job/心跳兜底） */
    public static void start(Context c) {
        try {
            Intent i = new Intent(c, WeatherKeepAliveService.class);
            // minSdk 24：startForegroundService 是 API 26 才有的，低版本用 startService
            if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {
            // 后台启动受限时静默失败，交给心跳闹钟与 JobScheduler 兜底
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, WeatherKeepAliveService.class));
        } catch (Throwable ignored) { }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            new Thread(new Runnable() {
                @Override public void run() {
                    try { BackgroundTasks.run(WeatherKeepAliveService.this); }
                    catch (Throwable ignored) { }
                }
            }).start();
            if (h != null) h.postDelayed(this, CacheRefresher.intervalMs(WeatherKeepAliveService.this));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        try {
            startForeground(NOTI_ID, Notifier.buildKeepAlive(this));
        } catch (Throwable ignored) { }
        h = new Handler(Looper.getMainLooper());
        // v10.1.3：间隔跟随快速检查开关（正常 15 分钟 / 快速 30 秒）
        h.postDelayed(tick, CacheRefresher.intervalMs(this));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;            // 被系统回收后尝试自动恢复
    }

    @Override
    public void onDestroy() {
        running = false;
        if (h != null) {
            h.removeCallbacksAndMessages(null);
            h = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
