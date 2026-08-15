#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""WeatherTool v4.0 构建脚本：aapt -> javac -> d8 -> zip -> apksigner"""
import os, shutil, subprocess, zipfile, sys

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
BASE_APK = os.path.join(BUILD, "base.apk")
OUT_APK = os.path.join(BUILD, "WeatherTool.apk")
# v9.87：正式版构建，文件名不带 test 标签
VERSION = "9.87"
OUT_VER_APK = os.path.join(BUILD, "WeatherTool_v" + VERSION + "-locfix.apk")

def sh(cmd, **kw):
    print("$", " ".join(cmd) if isinstance(cmd, list) else cmd)
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.stdout: print(r.stdout[-3000:])
    if r.stderr: print("STDERR:", r.stderr[-3000:])
    if r.returncode != 0:
        raise SystemExit(f"FAILED ({r.returncode}): {cmd}")
    return r

# 1) 清理
for p in [GEN, os.path.join(BUILD, "classes"), os.path.join(BUILD, "dexout")]:
    shutil.rmtree(p, ignore_errors=True)
os.makedirs(GEN, exist_ok=True)
os.makedirs(os.path.join(BUILD, "classes"), exist_ok=True)
os.makedirs(os.path.join(BUILD, "dexout"), exist_ok=True)
if os.path.exists(BASE_APK): os.remove(BASE_APK)

# 2) aapt 打包资源（-A assets 打包字体）
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

# 6.5) v9.70：复制为带版本号文件名
shutil.copy(OUT_APK, OUT_VER_APK)
print("versioned apk:", OUT_VER_APK)

# 7) 验证
sh([AAPT, "dump", "badging", OUT_APK])
sh(["java", "-jar", APKSIGNER_JAR, "verify", "--verbose", OUT_APK])
print("\nDONE size:", os.path.getsize(OUT_APK), "bytes")
