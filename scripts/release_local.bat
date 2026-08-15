@echo off
rem ============================================================
rem WeatherTool 本地一键发布脚本 (Windows)
rem 用法：release_local.bat [版本号]
rem   不传版本号时自动从 app/build.gradle 读取 versionName
rem 流程：构建 release APK -> git 提交推码 -> 打 tag -> GitHub Release 附 APK
rem 前置：已安装 gh CLI (https://cli.github.com) 并登录 gh auth login
rem ============================================================
setlocal enabledelayedexpansion
cd /d "%~dp0.."

if "%~1"=="" (
    for /f "usebackq tokens=2 delims='" %%v in (`findstr /c:"versionName" app\build.gradle`) do set VER=%%v
    if "!VER!"=="" (echo [错误] 无法自动读取版本号，请手动传参: release_local.bat 9.88 & exit /b 1)
) else (
    set VER=%~1
)

rem 清理版本号（仅保留数字和点，用于 tag/文件名）
for /f %%v in ('powershell -NoProfile -Command "$v='%VER%'; $v -replace '[^0-9.]',''"') do set TAG=%%v
if "!TAG!"=="" (echo [错误] 版本号解析失败 & exit /b 1)
echo [1/5] 版本号: %VER%  ^(tag: v%TAG%^)

echo [2/5] 构建 Release APK...
call gradlew.bat assembleRelease --no-daemon || (echo [错误] 构建失败 & exit /b 1)

echo [3/5] 提交并推送源码...
git add -A || exit /b 1
git commit -m "release v%TAG%" || echo (无新提交，继续)
git push || exit /b 1

echo [4/5] 打 tag 并推送...
git tag v%TAG% || exit /b 1
git push origin v%TAG% || exit /b 1

echo [5/5] 创建 GitHub Release...
set APK=app\build\outputs\apk\release\app-release.apk
if not exist "%APK%" (echo [提示] 未找到 %APK%，请检查构建输出 & exit /b 0)
gh release create v%TAG% "%APK%" --title "WeatherTool v%TAG%" --notes "本地一键发布 v%TAG%" || exit /b 1

echo.
echo [完成] https://github.com/你的用户名/WeatherTool/releases/tag/v%TAG%
endlocal
