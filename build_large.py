#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""WeatherTool 大字版分支构建脚本（v9.85L）：
与原版共用同一份源码，构建时临时替换 manifest（包名/应用名/版本/Application 入口），
产物输出为 WeatherTool_Large_v{版本}.apk，与原版 WeatherTool_v{版本}.apk 完全独立、
互不覆盖，可同时安装（不同包名，数据/闹钟/小组件各自隔离）。

用法：python3 build_large.py
"""
import os, shutil, subprocess, zipfile

ROOT = "/astrbot/data/workspaces/_FriendMessage_89ACCD0BF9AFD9E85A1A4FAFC24C79E0/weathertool"
APP = os.path.join(ROOT, "app")
SRC = os.path.join(APP, "src/main")
MANIFEST = os.path.join(SRC, "AndroidManifest.xml")
RES = os.path.join(SRC, "res")
ASSETS = os.path.join(SRC, "assets")
GEN = os.path.join(ROOT, "gen")
BUILD = os.path.join(ROOT, "build")
AAPT = "/usr/bin/aapt"
ANDROID_JAR = "/opt/android-sdk/platforms/android-30/android.jar"
BT = "/opt/android-sdk/build-tools/35.0.0"
D8_JAR = os.path.join(BT, "lib/d8.jar")
APKSIGNER_JAR = os.path.join(BT, "lib/apksigner.jar")
KEYSTORE = os.path.join(ROOT, "weather.keystore")
KS_PASS = "reboot123"
BASE_APK = os.path.join(BUILD, "base_large.apk")
OUT_APK = os.path.join(BUILD, "WeatherTool_Large.apk")

# 大字版独立版本号（与原版 89 区分，后续大字版可独立迭代）
VER_CODE = "904"
VER_NAME = "9.89L"
OUT_VER_APK = os.path.join(BUILD, "WeatherTool_Large_v" + VER_NAME + ".apk")

def sh(cmd, **kw):
    print("$", " ".join(cmd) if isinstance(cmd, list) else cmd)
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.stdout: print(r.stdout[-3000:])
    if r.stderr: print("STDERR:", r.stderr[-3000:])
    if r.returncode != 0:
        raise SystemExit(f"FAILED ({r.returncode}): {cmd}")
    return r

def patch_manifest():
    """临时替换 manifest 为大字版配置；返回原内容以便恢复。
    注意：package 属性保持原样——应用 ID 改用 aapt --rename-manifest-package 改名，
    这样 R.java 仍在原包名下生成，源码零改动即可编译。"""
    s = open(MANIFEST, encoding='utf-8').read()
    orig = s
    s = s.replace('android:versionCode="90"', 'android:versionCode="' + VER_CODE + '"')
    s = s.replace('android:versionName="9.86 · 酷安@Eartecd"',
                  'android:versionName="' + VER_NAME + ' · 大字版"')
    s = s.replace('android:label="简洁天气"', 'android:label="简洁天气 · 大字版"')
    # 大字版入口：LargeApp + 两 Activity 的 getResources() 实现 2 倍整体缩放
    # （原版无 android:name，仅大字版启用）
    if 'android:name=".LargeApp"' not in s:
        s = s.replace('<application\n        android:label="简洁天气 · 大字版"',
                      '<application\n        android:name=".LargeApp"\n        android:label="简洁天气 · 大字版"')
    open(MANIFEST, 'w', encoding='utf-8').write(s)
    return orig



# 大字版专用源码微调：fontScale 1.5 下固定 dp 列宽同步放宽，防文字截断。
# （仅大字版构建时临时替换，构建后恢复，不影响原版源码）
SRC_MAIN = os.path.join(SRC, "java/com/simpleweather/app/MainActivity.java")
SOURCE_PATCHES = [
    # ===== 大字版 v9.88L：整体 UI + 所有字体统一放大 2 倍 =====
    # LargeApp.scaleResources 在 getResources() 中把 density/densityDpi/scaledDensity
    # 统一翻倍（dp 布局/自绘 View/sp 文字全部 2 倍，幂等）。源码中所有 dp 尺寸自动放大，
    # 无需逐个 patch；
    # 仅 7 天预报行 5 列固定宽在 2 倍后超出屏宽（52+46+44+36+38=216dp -> 432dp > 336dp），
    # 需把源码列宽减半并按 2 倍字体文字需求重算：
    #   星期列: 视觉需 56dp（"今天" 2 全角字 x 28sp）  -> 源码 28dp
    #   图标+类型: 视觉需 60dp（"雷阵雨" 20sp x 3 字）  -> 源码 30dp
    #   概率列: 视觉需 56dp（"100%" 24sp）             -> 源码 28dp
    #   低温列: 视觉需 60dp（"-12°" 26sp）             -> 源码 30dp
    #   高温列: 视觉需 46dp（"12°" 26sp）              -> 源码 23dp
    #   合计视觉 278dp，剩余 58dp 给温度条，五列文字均单行完整显示。
    ('day.setWidth(dp(52));', 'day.setWidth(dp(28));'),
    ("""icWrap.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(46), LinearLayout.LayoutParams.WRAP_CONTENT));""",
     """icWrap.setLayoutParams(new LinearLayout.LayoutParams(
                    dp(30), LinearLayout.LayoutParams.WRAP_CONTENT));"""),
    ('pop.setWidth(dp(44));', 'pop.setWidth(dp(28));'),
    ('lo.setWidth(dp(36));', 'lo.setWidth(dp(30));'),
    ('hi.setWidth(dp(38));', 'hi.setWidth(dp(23));'),
    # 24h 列 56dp（2 倍后 112dp 视觉，横滚容器可容纳）、搜索框 48dp（2 倍后 96dp 视觉）
    # 均随整体 2 倍自然放大，无需 patch。
]

# 大字版专用 XML 微调：2.5 倍下防爆屏/协调图形尺寸（构建后恢复）
SRC_XML = os.path.join(SRC, "res/layout/activity_main.xml")
XML_PATCHES = [
    # 主页大温度 96sp -> 66sp（2 倍后实际渲染 132sp，比原版 96sp 大 37%；
    # 若不限幅，192sp 的 "-12°" 约 413dp 宽，超出卡片被裁切）
    ('android:textSize="96sp"', 'android:textSize="66sp"'),
    # 太阳弧/月相图 110dp 随 density 翻倍自动变 220dp 视觉，与整体 2 倍界面协调，无需 patch。
]

def patch_xml():
    s = open(SRC_XML, encoding='utf-8').read()
    orig = s
    for old, new in XML_PATCHES:
        cnt = s.count(old)
        if cnt < 1:
            raise SystemExit("XML PATCH FAILED (count=%d): %s..." % (cnt, old[:40]))
        s = s.replace(old, new)
    open(SRC_XML, 'w', encoding='utf-8').write(s)
    return orig

def restore_xml(orig):
    open(SRC_XML, 'w', encoding='utf-8').write(orig)


def patch_sources():
    s = open(SRC_MAIN, encoding='utf-8').read()
    orig = s
    for old, new in SOURCE_PATCHES:
        cnt = s.count(old)
        if cnt != 1:
            raise SystemExit("SOURCE PATCH FAILED (count=%d): %s..." % (cnt, old[:40]))
        s = s.replace(old, new)
    open(SRC_MAIN, 'w', encoding='utf-8').write(s)
    return orig

def restore_sources(orig):
    open(SRC_MAIN, 'w', encoding='utf-8').write(orig)

def restore_manifest(orig):
    open(MANIFEST, 'w', encoding='utf-8').write(orig)

orig_manifest = patch_manifest()
orig_sources = patch_sources()
orig_xml = patch_xml()
try:
    # 1) 清理（不删除原版 APK 产物）
    for p in [GEN, os.path.join(BUILD, "classes"), os.path.join(BUILD, "dexout")]:
        shutil.rmtree(p, ignore_errors=True)
    os.makedirs(GEN, exist_ok=True)
    os.makedirs(os.path.join(BUILD, "classes"), exist_ok=True)
    os.makedirs(os.path.join(BUILD, "dexout"), exist_ok=True)
    if os.path.exists(BASE_APK): os.remove(BASE_APK)

    # 2) aapt 打包资源（--rename-manifest-package 让 APK 包名独立，R 包名不变）
    sh([AAPT, "package", "-f", "-m", "-J", GEN, "-M", MANIFEST, "-S", RES,
        "-A", ASSETS, "-I", ANDROID_JAR, "-F", BASE_APK,
        "--rename-manifest-package", "com.simpleweather.app.large"])

    # 3) javac
    java_srcs = subprocess.check_output(
        ["find", os.path.join(SRC, "java"), GEN, "-name", "*.java"]).decode().split()
    sh(["javac", "-encoding", "UTF-8", "-source", "1.8", "-target", "1.8",
        "-classpath", ANDROID_JAR, "-d", os.path.join(BUILD, "classes")] + java_srcs)

    # 4) d8
    cls = subprocess.check_output(
        ["find", os.path.join(BUILD, "classes"), "-name", "*.class"]).decode().split()
    sh(["java", "-cp", D8_JAR, "com.android.tools.r8.D8",
        "--lib", ANDROID_JAR, "--min-api", "24",
        "--output", os.path.join(BUILD, "dexout")] + cls)

    # 5) dex 入包
    dex = os.path.join(BUILD, "dexout", "classes.dex")
    with zipfile.ZipFile(BASE_APK, "a") as z:
        z.write(dex, "classes.dex")
    print("dex added:", os.path.getsize(dex), "bytes")

    # 6) 签名
    if os.path.exists(OUT_APK): os.remove(OUT_APK)
    sh(["java", "-jar", APKSIGNER_JAR, "sign",
        "--ks", KEYSTORE, "--ks-pass", "pass:" + KS_PASS,
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--out", OUT_APK, BASE_APK])

    # 6.5) 版本文件名
    shutil.copy(OUT_APK, OUT_VER_APK)
    print("versioned apk:", OUT_VER_APK)

    # 7) 验证
    sh([AAPT, "dump", "badging", OUT_APK])
    sh(["java", "-jar", APKSIGNER_JAR, "verify", "--verbose", OUT_APK])
    print("\nDONE size:", os.path.getsize(OUT_APK), "bytes")
finally:
    restore_manifest(orig_manifest)
    print("manifest restored")
    restore_sources(orig_sources)
    print("sources restored")
    restore_xml(orig_xml)
    print("xml restored")
