# 简洁天气 WeatherTool — V9.87 正式版源码工程

> **版本**：9.87 · 酷安@Eartecd（versionCode 97）
> **定位**：纯系统 API 实现的天气 App，**零第三方依赖**（仅 org.json，Android 内置）
> **要求**：Android Studio Koala (2024.1.1) 及以上；JDK 17（AS 自带）

## 📦 快速开始（Mac / Windows 通用）

1. **Android Studio** → `Open` → 选中本目录（`weathertool/`）；
2. 等待 Gradle Sync 完成（首次会自动下载 Gradle 8.7 + AGP 8.5.2 + SDK 34，需联网，约几分钟）；
3. 若提示缺 SDK 组件，按提示安装 `Android SDK Platform 34`；
4. `Build` → `Build APK(s)` 或直接点 ▶ 装到已连接的真机/模拟器。

## 🔧 命令行构建（可选）

在 Android Studio 里 Sync 过一次后，`gradle wrapper` 会自动补齐 wrapper，之后可用：

```bash
./gradlew assembleDebug    # 产物在 app/build/outputs/apk/debug/
./gradlew assembleRelease
```

或手动安装 Gradle 后直接 `gradle assembleDebug`。

## 📁 工程结构

```
weathertool/
├── settings.gradle / build.gradle / gradle.properties   # Gradle 工程配置
├── gradle/wrapper/gradle-wrapper.properties             # 指定 Gradle 8.7
└── app/
    ├── build.gradle                                     # 应用配置（minSdk24/targetSdk33/compileSdk34）
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml                          # 权限与四大组件
        ├── java/com/simpleweather/app/                  # 全部源码（40+ 类）
        ├── res/                                         # 布局/动画/背景/小组件
        ├── assets/                                      # 字体与 WebView 资源
        └── (java_old/ 为历史遗留地图代码，不参与编译)
```

## 🚀 自动发布（GitHub 开源）

工程内置两套发布流水线，正式版构建后一键开源源码 + APK：

### 方式一：GitHub Actions 全自动（推荐）
1. 把工程推到 GitHub（公开仓库 = 开源）：
   ```bash
   git init && git add -A && git commit -m "init"
   git branch -M main
   git remote add origin https://github.com/你的用户名/WeatherTool.git
   git push -u origin main
   ```
2. （可选）配置正式签名 Secrets：仓库 Settings → Secrets and variables → Actions，添加：
   `SIGNING_KEYSTORE_BASE64`（keystore 的 base64）、`SIGNING_KEYSTORE_PASS`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASS`；
3. 之后每次发版只需推送 tag：
   ```bash
   git tag v9.88 && git push origin v9.88
   ```
   或到 Actions 页手动运行「Release Build」。流水线自动构建 Release APK 并发布到 Releases（含源码包）。

### 方式二：本地一键发布
构建 + 推码 + 打 tag + 发 Release 一条龙（需安装 gh CLI 并 `gh auth login`）：
- Windows：`scriptselease_local.bat`（或 `release_local.bat 9.88`）
- macOS/Linux：`./scripts/release_local.sh`（或 `./scripts/release_local.sh 9.88`）

### 签名说明
- 本地默认无签名构建（与旧版一致）；要正式签名，构建前设置环境变量：
  `SIGNING_KEYSTORE`（jks 路径）、`SIGNING_KEYSTORE_PASS`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASS`；
- GitHub Actions 配好 Secrets 即自动正式签名，未配置则出 debug 签名包（仅测试用）。

## 🧭 定位相关（v9.87-fix 埋点）

- `Locator.java`：AGPS 注入（`sendExtraCommand` force_time / force_xtra）；
- `MainActivity.java`：GNSS 卫星状态回调（`GnssStatus.Callback`）与定位诊断日志；
- `LogFile.java`：诊断日志输出到 `Download/WeatherTool_log_*.log`；
- 相关权限已按 Android 12/13 规范声明（含 `ACCESS_LOCATION_EXTRA_COMMANDS`、后台定位声明）。

## 🏷 版本历史

- **9.87**：正式版（本工程对应源码），含定位诊断埋点与 AGPS 注入修复；
- 更早版本见 `build/archive/` 历史 APK 与 `APP架构梳理.md`。

---

*本工程由 V9.87 正式版源码 + Gradle 骨架整理而成，可直接编译出与 `WeatherTool_v9.87-locfix.apk` 功能一致的安装包。*
