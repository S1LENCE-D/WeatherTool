package com.simpleweather.app;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * v9.88：后台常驻服务（前台服务 + 常驻低优先级通知）。
 *
 * 解决「手动清除 App 后台后无法推送 / 无法刷新缓存」：
 *  - 前台服务存活时，用户从最近任务划掉 App 只会销毁 Activity，进程保留；
 *  - 进程活着 → 每日定时通知闹钟（AlarmReceiver）与每小时缓存刷新都能照常工作；
 *  - 服务内自带每小时缓存刷新（前台服务进程不受 Doze 限制，且无需 10 秒广播窗口）；
 *  - onTaskRemoved（用户划掉任务）时兜底重排全部闹钟，双保险；
 *  - START_STICKY：进程被系统回收后尝试自动重建。
 *
 * 注意：系统「强制停止」后所有闹钟/广播都会失效且无法代码绕过，
 * 设置面板提供「自启动设置」入口引导用户到系统设置放行。
 */
public class KeepAliveService extends Service {

    private static final long REFRESH_MS = 60L * 60 * 1000;   // 每小时刷新缓存

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            refreshCache();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // v9.87test：不再使用前台服务常驻通知（用户要求移除通知栏常驻），
        // 改为普通后台服务 + CacheRefresher 15 分钟高频率闹钟保活。
        Notifier.ensureChannels(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 幂等：重复启动不重复排队
        handler.removeCallbacks(refreshTask);
        handler.postDelayed(refreshTask, 30_000);   // 启动 30 秒后首刷
        return START_STICKY;   // 被系统回收后自动重建
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 用户从最近任务划掉 App：Activity 销毁、服务保留。
        // 兜底重排全部闹钟（部分 ROM 划掉任务会顺带清理闹钟）。
        WeatherReporter.ensureScheduled(this);
        AlertWatcher.ensureRunning(this);
        CacheRefresher.ensureRunning(this);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(refreshTask);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** 静默拉取最新天气并写缓存（失败忽略，下轮再试） */
    private void refreshCache() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WeatherCache.Data d = WeatherCache.load(KeepAliveService.this);
                    if (d == null) return;
                    WeatherCenter.get().fetchWeather(
                            KeepAliveService.this, d.lat, d.lng, d.city);
                } catch (Exception ignored) { }
            }
        }).start();
    }
}
