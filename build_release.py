#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""WeatherTool 正式版发布构建脚本（aapt -> javac -> d8 -> zip -> apksigner）

与 build_v4.py 的关系：v4 是开发期脚本；本脚本是「正式发布专用」，
在 v4 基础上增加了 manifest 临时注入/自动恢复，保证 APK 版本信息完整：
  - package  com.simpleweather.app
  - versionCode 97 / versionName 9.87
  - uses-sdk  minSdk 24 / targetSdk 33（关键：缺 targetSdk 会导致安装
    Failure [-26 new target SDK 0 doesn't support runtime permissions]）

用法：python3 build_release.py
产物：build/WeatherTool_v9.87.apk（正式签名，v1+v2+v3）
"""
import os, shutil, subprocess, zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
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
BASE_APK = os.path.join(BUILD, "base.apk")
OUT_APK = os.path.join(BUILD, "WeatherTool.apk")

# ---- 版本信息（发布时在此改） ----
PACKAGE = "com.simpleweather.app"
VER_CODE = "97"
VER_NAME = "9.87"
MIN_SDK = "24"
TARGET_SDK = "33"
OUT_VER_APK = os.path.join(BUILD, "WeatherTool_v" + VER_NAME + ".apk")

MANIFEST_TAG = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
MANIFEST_INJECT = (
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android"\n'
    '    package="' + PACKAGE + '"\n'
    '    android:versionCode="' + VER_CODE + '"\n'
    '    android:versionName="' + VER_NAME + '">\n'
    '    <uses-sdk android:minSdkVersion="' + MIN_SDK + '"'
    ' android:targetSdkVersion="' + TARGET_SDK + '" />'
)

def sh(cmd, **kw):
    print("$", " ".join(cmd) if isinstance(cmd, list) else cmd)
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.stdout: print(r.stdout[-2000:])
    if r.stderr: print("STDERR:", r.stderr[-2000:])
    if r.returncode != 0:
        raise SystemExit(f"FAILED ({r.returncode}): {cmd}")
    return r

def main():
    # 0) manifest 临时注入（构建结束无论成败都恢复）
    backup = open(MANIFEST, encoding="utf-8").read()
    if MANIFEST_TAG not in backup:
        raise SystemExit("manifest 结构异常，找不到注入锚点")
    open(MANIFEST, "w", encoding="utf-8").write(
        backup.replace(MANIFEST_TAG, MANIFEST_INJECT, 1))
    try:
        # 1) 清理
        for p in [GEN, os.path.join(BUILD, "classes"), os.path.join(BUILD, "dexout")]:
            shutil.rmtree(p, ignore_errors=True)
        os.makedirs(GEN, exist_ok=True)
        os.makedirs(os.path.join(BUILD, "classes"), exist_ok=True)
        os.makedirs(os.path.join(BUILD, "dexout"), exist_ok=True)
        if os.path.exists(BASE_APK): os.remove(BASE_APK)

        # 2) aapt 打包资源
        sh([AAPT, "package", "-f", "-m", "-J", GEN, "-M", MANIFEST, "-S", RES,
            "-A", ASSETS, "-I", ANDROID_JAR, "-F", BASE_APK])

        # 3) javac 编译（java 1.8 语法）
        java_srcs = subprocess.check_output(
            ["find", os.path.join(SRC, "java"), GEN, "-name", "*.java"]).decode().split()
        sh(["javac", "-encoding", "UTF-8", "-source", "1.8", "-target", "1.8",
            "-classpath", ANDROID_JAR, "-d", os.path.join(BUILD, "classes")] + java_srcs)

        # 4) d8 打 dex
        cls = subprocess.check_output(
            ["find", os.path.join(BUILD, "classes"), "-name", "*.class"]).decode().split()
        sh(["java", "-cp", D8_JAR, "com.android.tools.r8.D8",
            "--lib", ANDROID_JAR, "--min-api", "24",
            "--output", os.path.join(BUILD, "dexout")] + cls)

        # 5) 塞 dex 进 base.apk
        dex = os.path.join(BUILD, "dexout", "classes.dex")
        with zipfile.ZipFile(BASE_APK, "a") as z:
            z.write(dex, "classes.dex")
        print("dex added:", os.path.getsize(dex), "bytes")

        # 6) apksigner 签名（v1+v2+v3）
        if os.path.exists(OUT_APK): os.remove(OUT_APK)
        sh(["java", "-jar", APKSIGNER_JAR, "sign",
            "--ks", KEYSTORE, "--ks-pass", "pass:" + KS_PASS,
            "--v1-signing-enabled", "true",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "true",
            "--out", OUT_APK, BASE_APK])

        # 7) 复制为版本文件名
        shutil.copy(OUT_APK, OUT_VER_APK)
        print("versioned apk:", OUT_VER_APK)

        # 8) 验证
        sh([AAPT, "dump", "badging", OUT_APK])
        sh(["java", "-jar", APKSIGNER_JAR, "verify", "--verbose", OUT_APK])
        print("\nDONE size:", os.path.getsize(OUT_APK), "bytes")
    finally:
        # 9) 恢复 manifest（零残留）
        open(MANIFEST, "w", encoding="utf-8").write(backup)
        print("manifest restored")

if __name__ == "__main__":
    main()
