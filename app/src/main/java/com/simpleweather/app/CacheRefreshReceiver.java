package com.simpleweather.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 15 分钟后台心跳：① 拉取最新天气写缓存 ② 定时播报错过补发 ③ 气象预警检查。
 *
 * <p><b>v10.1 重构</b>：三件事全部在本接收器的 goAsync 线程里完成，不再启动任何服务。
 * 旧实现在这里 `startForegroundService`（补播报）与 `startService`（预警检查）——
 * 两者都是从后台启动服务：Android 8+ 禁普通后台服务、Android 12+ 禁后台启前台服务，
 * 会抛 {@code IllegalStateException} / {@code ForegroundServiceStartNotAllowedException}，
 * 而原代码没有捕获：结果是后台进程周期性崩溃，同一接收器里排在后面的预警检查
 * 永远执行不到，补播报还会反复重试重崩（「定时提醒不工作」的根因之一）。
 *
 * <p>广播窗口（后台 60 秒）足够覆盖一次网络请求；异常全部吞掉，下一轮心跳自会重试。
 */
public class CacheRefreshReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(final Context context, Intent intent) {
        if (intent == null) return;
        if (!CacheRefresher.ACTION_REFRESH.equals(intent.getAction())) return;
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // v10.1.2：统一走 BackgroundTasks —— 拉天气 → 补播报 → 查预警 → 刷小组件，
                    // 每一步单独 try：小组件渲染异常不再把定时播报一起拖没
                    //（v10.1.1 就是把这步塞进同一个 try 里，抛出后整段跳过 → 后台通知失效）
                    BackgroundTasks.run(context);
                    // 按开关同步常驻服务；后台启动被系统拒绝时静默失败
                    BackgroundTasks.syncKeepAlive(context);
                } catch (Throwable ignored) {
                    // 失败静默跳过：下一轮心跳 / 回前台补刷兜底
                } finally {
                    // v10.1.3：快速检查模式下，由这一轮负责排下一轮（30 秒接力）
                    try {
                        if (CacheRefresher.fastCheck(context)) {
                            CacheRefresher.scheduleNext(context, CacheRefresher.FAST_INTERVAL_MS);
                        }
                    } catch (Throwable ignored) { }
                    pr.finish();
                }
            }
        }).start();
    }
}
