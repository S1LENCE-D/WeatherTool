package com.simpleweather.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * v10.1.2：后台自启动任务（JobScheduler）。
 *
 * <p>{@code setPersisted(true)} 让任务在被系统清掉、甚至重启之后由系统自动恢复，
 * <b>不需要用户再打开应用</b> —— 这是"后台自启进程"在 Android 12+ 上最可靠的做法
 * （开机广播不允许启动前台服务，见 {@link WeatherKeepAliveService} 注释）。
 *
 * <p>周期取系统允许的最小值 15 分钟，每轮跑一遍 {@link BackgroundTasks#run}：
 * 刷新实时天气 → 补定时播报 → 查预警 → 刷小组件。
 * 与 AlarmManager 心跳互为兜底：一边被系统限制，另一边还能顶上。
 */
public class WeatherJobService extends JobService {

    static final int JOB_ID = 8801;      // 包内可见：设置页自检要查它是否已排队
    private static final long INTERVAL_MS = 15L * 60 * 1000;

    /** 幂等注册（开机、应用启动、覆盖安装后都调一次） */
    public static void ensureScheduled(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            // v10.1.3：对齐 WorkManager 的 ExistingPeriodicWorkPolicy.KEEP ——
            // 已经在排队就不要重排：重排会把 15 分钟周期重新计时，
            // 用户频繁打开应用时这个任务可能永远轮不到。
            try {
                if (js.getPendingJob(JOB_ID) != null) return;
            } catch (Throwable ignored) { }
            JobInfo job = new JobInfo.Builder(JOB_ID,
                    new ComponentName(ctx, WeatherJobService.class))
                    .setPersisted(true)                       // 需 RECEIVE_BOOT_COMPLETED
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(INTERVAL_MS)
                    .build();
            js.schedule(job);
        } catch (Throwable ignored) {
            // 部分厂商 ROM 限制 JobScheduler：静默失败，心跳闹钟仍在兜底
        }
    }

    public static void cancel(Context ctx) {
        try {
            JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js != null) js.cancel(JOB_ID);
        } catch (Throwable ignored) { }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context ctx = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    BackgroundTasks.run(ctx);
                } catch (Throwable ignored) {
                } finally {
                    jobFinished(params, false);
                }
            }
        }).start();
        return true;    // 还有后台线程在跑
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;    // 被系统打断：要求重新排期
    }
}
