package com.simpleweather.app;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 预警检查服务（v9.78 重写）：
 * - 由 AlertAlarmReceiver 周期闹钟触发，每次只做一次检查，完成后自停；
 * - 拉取本地气象预警，黄色及以上（alarmLevel>=3）且未通知过则推送（走 Notifier）；
 * - 不再 startForeground / 不再 Handler 轮询 / 不再 WakeLock：
 *   通知栏无「预警监控中」常驻通知，后台无长期驻留。
 */
public class AlertWatchService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 开关已关（可能用户刚在设置里关闭）：自停
        if (!AlertWatcher.enabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        checkAlerts();
        return START_NOT_STICKY;
    }

    /** 拉取预警并推送黄色及以上新预警（单次，结束后自停） */
    private void checkAlerts() {
        final String city = AlertWatcher.city(this);
        if (city == null || city.isEmpty()) {   // 尚未定位成功，等下一轮闹钟
            stopSelf();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String prov = AlertWatcher.prov(AlertWatchService.this);
                    String dist = AlertWatcher.dist(AlertWatchService.this);
                    final WeatherApi.AlarmResult r =
                            WeatherApi.fetchAlarms(city, prov, dist);
                    if (r == null || r.local.isEmpty()) {
                        stopSelf();
                        return;
                    }
                    Set<String> done = AlertWatcher.notified(AlertWatchService.this);
                    String day = AlertWatcher.todayKey();

                    // 收集全部黄色及以上预警（非仅最高一条），未通知过的计入新增
                    final List<String[]> actives = new ArrayList<String[]>();
                    int fresh = 0;
                    int topLv = 0;
                    for (String[] it : r.local) {
                        if (it == null || it[0] == null) continue;
                        int lv = WeatherApi.alarmLevel(it[0]);
                        if (lv < 3) continue;   // 黄色（3）及以上才提醒
                        actives.add(it);
                        if (lv > topLv) topLv = lv;
                        if (!done.contains(it[0] + "|" + day)) {
                            done.add(it[0] + "|" + day);
                            fresh++;
                        }
                    }
                    if (fresh <= 0 || actives.isEmpty()) {
                        stopSelf();
                        return;
                    }
                    AlertWatcher.rememberNotified(AlertWatchService.this, done);

                    // 正文：逐条列出（最多 5 条，其余计数）
                    StringBuilder body = new StringBuilder();
                    body.append("当前有 ").append(actives.size())
                            .append(" 条气象预警生效");
                    if (fresh > 0) body.append("，新增 ").append(fresh).append(" 条");
                    body.append("：");
                    int shown = Math.min(actives.size(), 5);
                    for (int i = 0; i < shown; i++) {
                        body.append('\n').append(i + 1).append(". ")
                                .append(actives.get(i)[0]);
                    }
                    if (actives.size() > shown) {
                        body.append("\n… 等共 ").append(actives.size()).append(" 条");
                    }
                    final String lvName = topLv >= 5 ? "红色" : topLv == 4 ? "橙色" : "黄色";
                    final int color = topLv >= 5 ? 0xFFD93025
                            : topLv == 4 ? 0xFFE8710A : 0xFFF9AB00;
                    final String title = lvName + "预警 · " + actives.size() + " 条生效";
                    final String text = body.toString();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Notifier.notifyAlert(AlertWatchService.this,
                                        title, text, color);
                            } finally {
                                stopSelf();
                            }
                        }
                    });
                } catch (Exception ignored) {
                    // 网络/解析失败静默跳过，下轮闹钟再试
                    stopSelf();
                }
            }
        }).start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
