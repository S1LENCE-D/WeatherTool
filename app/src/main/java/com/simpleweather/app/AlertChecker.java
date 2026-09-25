package com.simpleweather.app;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 气象预警检查：一次「拉预警 → 挑出黄色及以上新增 → 发通知 → 记去重」的完整动作。
 *
 * <p><b>v10.1 重构</b>：逻辑从 {@code AlertWatchService}（已删除）迁出。
 * 旧实现由 15 分钟心跳接收器 `startService` 启动该服务——Android 8+ 明确禁止后台
 * 启动服务，会抛 {@code IllegalStateException} 且原代码未捕获，导致后台进程崩溃、
 * 预警检查**从未真正执行**（只有用户在设置里打开开关时的前台首查能跑）。
 * 现在由调用方在自己的后台线程里直接调用 {@link #check}。
 *
 * <p>必须在子线程调用（内部有网络请求）。
 */
public final class AlertChecker {

    private AlertChecker() { }

    /** 检查并推送新增的黄色及以上预警；任何异常都吞掉（下一轮心跳会再试） */
    public static void check(Context ctx) {
        try {
            if (!AlertWatcher.enabled(ctx)) {
                Diag.i("AlertChecker: 开关已关，跳过");
                return;
            }
            final String city = AlertWatcher.city(ctx);
            if (city == null || city.isEmpty()) {   // 尚未定位成功，等下一轮
                Diag.i("AlertChecker: 城市快照为空，跳过本轮");
                return;
            }
            final String prov = AlertWatcher.prov(ctx);
            final String dist = AlertWatcher.dist(ctx);
            Diag.i("AlertChecker: 开始检查 city=" + city + " prov=" + prov + " dist=" + dist);

            WeatherApi.AlarmResult r = WeatherApi.fetchAlarms(city, prov, dist);
            if (r == null || r.local.isEmpty()) {
                Diag.i("AlertChecker: 无结果或空列表 r=" + r);
                return;
            }
            Diag.i("AlertChecker: 预警条数=" + r.local.size());

            Set<String> done = AlertWatcher.notified(ctx);
            String day = AlertWatcher.todayKey();
            List<String[]> actives = new ArrayList<String[]>();
            int fresh = 0, topLv = 0;
            for (String[] it : r.local) {
                if (it == null || it[0] == null) continue;
                int lv = WeatherApi.alarmLevel(it[0]);
                if (lv < 3) continue;               // 黄色（3）及以上才提醒
                actives.add(it);
                if (lv > topLv) topLv = lv;
                if (!done.contains(it[0] + "|" + day)) {
                    done.add(it[0] + "|" + day);
                    fresh++;
                }
            }
            if (fresh <= 0 || actives.isEmpty()) {
                Diag.i("AlertChecker: 无新增 fresh=" + fresh + " actives=" + actives.size());
                return;
            }
            Diag.i("AlertChecker: 有新增 " + fresh + " 条，发送通知");
            AlertWatcher.rememberNotified(ctx, done);

            // 正文：逐条列出（最多 5 条，其余计数）
            StringBuilder body = new StringBuilder();
            body.append("当前有 ").append(actives.size()).append(" 条气象预警生效");
            if (fresh > 0) body.append("，新增 ").append(fresh).append(" 条");
            body.append("：");
            int shown = Math.min(actives.size(), 5);
            for (int i = 0; i < shown; i++) {
                body.append('\n').append(i + 1).append(". ").append(actives.get(i)[0]);
            }
            if (actives.size() > shown) {
                body.append("\n… 等共 ").append(actives.size()).append(" 条");
            }
            final String lvName = topLv >= 5 ? "红色" : topLv == 4 ? "橙色" : "黄色";
            final int color = topLv >= 5 ? 0xFFD93025
                    : topLv == 4 ? 0xFFE8710A : 0xFFF9AB00;
            // NotificationManager.notify 可跨线程调用，无需切主线程
            Notifier.notifyAlert(ctx, lvName + "预警 · " + actives.size() + " 条生效",
                    body.toString(), color);
        } catch (Throwable t) {
            Diag.i("AlertChecker: 异常 " + t);
        }
    }
}
