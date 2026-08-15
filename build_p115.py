#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""WeatherTool 115% 界面大小分支构建脚本（v9.91P）：
与 v9.86 原版共用同一份源码；构建时临时注入
  - ScaleApp.java（固定 1.05 倍整机缩放）
  - MainActivity / RainMapActivity 的 getResources() override
  - manifest（版本号 / label / application 入口 / --rename-manifest-package 独立包名）
构建完成后全部恢复，原版源码零残留。流程：aapt -> javac -> d8 -> zip -> apksigner"""
import os, shutil, subprocess, zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "app/src/main")
MANIFEST = os.path.join(SRC, "AndroidManifest.xml")
JAVA_DIR = os.path.join(SRC, "java/com/simpleweather/app")
MAIN_ACT = os.path.join(JAVA_DIR, "MainActivity.java")
RAIN_ACT = os.path.join(JAVA_DIR, "RainMapActivity.java")
PATCH_SCALE = os.path.join(ROOT, "scale_patch", "ScaleApp.java")
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
KS_PASS = "***REMOVED***"
BASE_APK = os.path.join(BUILD, "base_p115.apk")
OUT_APK = os.path.join(BUILD, "WeatherTool_P115.apk")
VER_CODE = "92"
VER_NAME = "9.91P"
OUT_VER_APK = os.path.join(BUILD, "WeatherTool_v" + VER_NAME + ".apk")

OVERRIDE = """
    /** 115% 界面大小分支：dp/sp 整体 ×1.15（幂等，构建脚本临时注入） */
    @Override
    public android.content.res.Resources getResources() {
        android.content.res.Resources res = super.getResources();
        ScaleApp.apply(res);
        return res;
    }
"""

def sh(cmd, **kw):
    print("$", " ".join(cmd) if isinstance(cmd, list) else cmd)
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.stdout: print(r.stdout[-3000:])
    if r.stderr: print("STDERR:", r.stderr[-3000:])
    if r.returncode != 0:
        raise SystemExit(f"FAILED ({r.returncode}): {cmd}")
    return r

def patch_sources():
    """临时注入：ScaleApp.java + 两 Activity override + manifest；返回原 manifest 供恢复"""
    shutil.copy(PATCH_SCALE, os.path.join(JAVA_DIR, "ScaleApp.java"))
    for path, anchor in [(MAIN_ACT, "public class MainActivity extends Activity {"),
                         (RAIN_ACT, "public class RainMapActivity extends Activity {")]:
        t = open(path, encoding='utf-8').read()
        if "ScaleApp.apply" not in t:
            t = t.replace(anchor, anchor + OVERRIDE, 1)
            open(path, 'w', encoding='utf-8').write(t)
    s = open(MANIFEST, encoding='utf-8').read()
    orig_manifest = s
    s = s.replace('android:versionCode="90"', 'android:versionCode="' + VER_CODE + '"')
    s = s.replace('android:versionName="9.86 · 酷安@Eartecd"',
                  'android:versionName="' + VER_NAME + ' · 115%"')
    s = s.replace('android:label="简洁天气"', 'android:label="简洁天气 · 115%"')
    if 'android:name=".ScaleApp"' not in s:
        s = s.replace('<application\n', '<application\n        android:name=".ScaleApp"\n', 1)
    open(MANIFEST, 'w', encoding='utf-8').write(s)
    return orig_manifest

def restore_sources(orig_manifest):
    """恢复原版源码：删 ScaleApp.java、还原两 Activity 与 manifest"""
    try:
        os.remove(os.path.join(JAVA_DIR, "ScaleApp.java"))
    except OSError:
        pass
    for path in [MAIN_ACT, RAIN_ACT]:
        t = open(path, encoding='utf-8').read()
        if OVERRIDE in t:
            t = t.replace(OVERRIDE, "", 1)
            open(path, 'w', encoding='utf-8').write(t)
    open(MANIFEST, 'w', encoding='utf-8').write(orig_manifest)
    print("[restore] 源码已恢复原版")

orig_manifest = patch_sources()
try:
    # 1) 清理
    for p in [GEN, os.path.join(BUILD, "classes"), os.path.join(BUILD, "dexout")]:
        shutil.rmtree(p, ignore_errors=True)
    os.makedirs(GEN, exist_ok=True)
    os.makedirs(os.path.join(BUILD, "classes"), exist_ok=True)
    os.makedirs(os.path.join(BUILD, "dexout"), exist_ok=True)
    if os.path.exists(BASE_APK): os.remove(BASE_APK)

    # 2) aapt 打包资源（--rename-manifest-package 让 APK 应用 ID 独立，R 包名不变）
    sh([AAPT, "package", "-f", "-m", "-J", GEN, "-M", MANIFEST, "-S", RES,
        "-A", ASSETS, "-I", ANDROID_JAR, "-F", BASE_APK,
        "--rename-manifest-package", "com.simpleweather.app.p115"])

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
    shutil.copy(OUT_APK, OUT_VER_APK)
    print("signed apk:", OUT_VER_APK)

    # 7) 验证
    sh([AAPT, "dump", "badging", OUT_APK])
    sh(["java", "-jar", APKSIGNER_JAR, "verify", "--verbose", OUT_APK])
    print("\nDONE size:", os.path.getsize(OUT_APK), "bytes")
finally:
    restore_sources(orig_manifest)
