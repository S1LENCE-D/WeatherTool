package com.simpleweather.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v10.1.3：厂商 ROM 的后台/自启动设置跳转。
 *
 * <p>依据：《Android 后台进程自启动实现说明文档》§5.2 / §5.3 ——
 * 国内厂商没有统一 API，只能按品牌逐个尝试 ComponentName 跳转，失败再退回应用详情页。
 *
 * <p>写法上做了两层保险：
 * <ol>
 *   <li>先用 {@code resolveActivity} 确认目标 Activity 真的存在（ROM 版本差异大，
 *       很多机型改过类名），存在才跳；</li>
 *   <li>全部失败就退到「应用详情」页 —— 那个任何 ROM 都有，用户也能自己找到
 *       自启动/省电管理入口。</li>
 * </ol>
 * 不做“强行跳转 + 崩溃”的写法。
 */
public final class RomSettings {

    private RomSettings() { }

    private static final String[][] MIUI = {
            {"com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"},
    };
    /** MIUI/HyperOS：单应用省电策略页（「无限制」就在这里改，是自启动之外最常被漏的一项） */
    private static final String[][] MIUI_POWER = {
            {"com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"},
            {"com.miui.powerkeeper", "com.miui.powerkeeper.ui.PowerKeeperActivity"},
    };
    private static final String[][] EMUI = {
            {"com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"},
            {"com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"},
    };
    private static final String[][] COLOROS = {
            {"com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"},
            {"com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"},
    };
    private static final String[][] VIVO = {
            {"com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"},
            {"com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"},
    };
    private static final String[][] SAMSUNG = {
            {"com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"},
    };

    /** 前往「自启动 / 后台管理」设置页（按品牌优先，再全量兜底，最后退应用详情页） */
    public static void openAutoStart(Context c) {
        String b = (Build.BRAND + " " + Build.MANUFACTURER).toLowerCase(Locale.ROOT);
        List<String[]> targets = new ArrayList<String[]>();
        if (b.contains("xiaomi") || b.contains("redmi")) addAll(targets, MIUI);
        else if (b.contains("huawei") || b.contains("honor")) addAll(targets, EMUI);
        else if (b.contains("oppo") || b.contains("realme") || b.contains("oneplus")) addAll(targets, COLOROS);
        else if (b.contains("vivo") || b.contains("iqoo")) addAll(targets, VIVO);
        else if (b.contains("samsung")) addAll(targets, SAMSUNG);
        // 通用兜底：用户可能刷了别家 ROM，或品牌串识别不到，把常见入口都试一遍
        addAll(targets, MIUI);
        addAll(targets, EMUI);
        addAll(targets, COLOROS);
        addAll(targets, VIVO);

        for (String[] t : targets) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(t[0], t[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (c.getPackageManager().resolveActivity(i, 0) == null) continue;
                c.startActivity(i);
                Diag.i("RomSettings: 已跳转 " + t[0] + "/" + t[1]);
                return;
            } catch (Throwable ignored) { }
        }
        Diag.i("RomSettings: 无可用自启动页，退回应用详情页");
        openAppDetails(c);
    }

    /**
     * MIUI / HyperOS 的「省电策略」页：把本应用改成「无限制」。
     * HyperOS 下「智能省电」会限 JobScheduler 与后台闹钟，是"自启动开了但仍不准时"的常见原因。
     */
    public static void openPowerSettings(Context c) {
        for (String[] t : MIUI_POWER) {
            try {
                Intent i = new Intent();
                i.setComponent(new ComponentName(t[0], t[1]));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra("package_name", c.getPackageName());
                if (c.getPackageManager().resolveActivity(i, 0) == null) continue;
                c.startActivity(i);
                Diag.i("RomSettings: 已跳转省电策略页 " + t[1]);
                return;
            } catch (Throwable ignored) { }
        }
        openAppDetails(c);   // 非 MIUI：退应用详情页，自己找「电池/省电」
    }

    /** 本应用的系统设置详情页（任何 ROM 都有，作为兜底） */
    public static void openAppDetails(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable ignored) { }
    }

    /** 请求加入电池优化白名单（文档 §5.3；需 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS） */
    public static void requestIgnoreBattery(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return;
        } catch (Throwable ignored) { }
        openAppDetails(c);
    }

    private static void addAll(List<String[]> to, String[][] from) {
        for (String[] s : from) to.add(s);
    }
}
