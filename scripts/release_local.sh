#!/usr/bin/env bash
# ============================================================
# WeatherTool 本地一键发布脚本 (macOS / Linux)
# 用法：./scripts/release_local.sh [版本号]
#   不传版本号时自动从 app/build.gradle 读取 versionName
# 流程：构建 release APK -> git 提交推码 -> 打 tag -> GitHub Release 附 APK
# 前置：已安装 gh CLI (https://cli.github.com) 并登录 gh auth login
# ============================================================
set -e
cd "$(dirname "$0")/.."

if [ -n "$1" ]; then
    VER="$1"
else
    VER=$(grep -o "versionName '[^']*'" app/build.gradle | sed "s/versionName '//;s/'//")
fi
TAG=$(echo "$VER" | tr -cd '0-9.')
[ -n "$TAG" ] || { echo "[错误] 版本号解析失败"; exit 1; }

echo "[1/5] 版本号: $VER  (tag: v$TAG)"
echo "[2/5] 构建 Release APK..."
./gradlew assembleRelease --no-daemon

echo "[3/5] 提交并推送源码..."
git add -A
git commit -m "release v$TAG" || echo "(无新提交，继续)"
git push

echo "[4/5] 打 tag 并推送..."
git tag "v$TAG" || true
git push origin "v$TAG"

echo "[5/5] 创建 GitHub Release..."
APK="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
    echo "[提示] 未找到 $APK，请检查构建输出"
    exit 0
fi
gh release create "v$TAG" "$APK" --title "WeatherTool v$TAG" --notes "本地一键发布 v$TAG"

echo
echo "[完成] https://github.com/你的用户名/WeatherTool/releases/tag/v$TAG"
