# 简洁天气 WeatherTool

> 极简、纯净、零广告的安卓天气应用 · 酷安 @Eartecd
>
> 当前版本：**v9.87**（versionCode 97）· 正式版

零第三方依赖，纯 Java + Android framework 手写 UI（无 AndroidX / Gradle 依赖），单 APK 约 800KB。

---

## ✨ 功能特性

### 天气核心
- **实时天气 + 多日预报**：当前温度、体感、湿度、风速风向、紫外线、日出日落、月相等完整气象要素
- **多天气源可切换**：内置 Open-Meteo（默认）/ 和风 / 心知 / 彩云 / 高德 五类数据源，统一取数口径（`WeatherSources`）
- **省市区级联搜索**：输入省/市/区名智能匹配，如「浙江」→ 浙江省及全部地级市，「深圳宝安」→ 市+区混合匹配
- **GPS + 网络定位**：AGPS 注入优化（`sendExtraCommand` force_time / force_xtra），移植 ROM / 无 GMS 机型冷启动定位加速（v9.87-fix）
- **本地天气缓存**：弱网 / 断网 / 定位失败不空白，先渲染上次成功数据；自动清理历史缓存防膨胀
- **天气数据仓库**（`WeatherCenter`）：拉取 + 写缓存统一链路，主页、定时推送、小组件取数口径一致

### 推送与后台
- **每日定时天气简报**：自选时间推送，精确闹钟（`setExactAndAllowWhileIdle`）保证 Doze / 省电下准时触发
- **气象预警监控**：每 30 分钟检查，黄色及以上预警自动推送（声音 + 震动）；精确闹钟调度 + 权限降级链（v9.87-fix）
- **自定义气象提醒**：温度（高/低）、湿度（高/低）、紫外线（高）阈值触发通知
- **后台缓存刷新**：每小时静默拉取最新天气写缓存
- **后台常驻保活**：前台服务 + 低优先级通知，划掉后台任务后推送与刷新照常
- **开机 / 覆盖安装自动恢复**：闹钟调度在重启、App 更新后自动重挂，不会静默失效

### 界面与交互
- **桌面小组件**：2×2（当日）、2×4（多日）双布局，独立 Provider
- **天气语音播报**：朗读当前天气简报
- **雷达雨图**：降雨雷达地图页
- **自绘动效**：太阳弧线、月相、紫外线日曲线、风向罗盘、天气动态背景、玻璃拟态高光
- **设置面板**：横向分页滑动（无 AndroidX 自研实现），Material 风格开关
- **主题色板**：多套配色方案可切换
- **字体优化**：Google Sans（数字/英文）+ Material Icons（天气图标），中文自动回退
- **大字版 / 115% 界面分支**：为视力友好与不同屏幕密度提供的独立构建分支（不同包名，可并存安装）

### 工程与稳定性
- **零第三方依赖**：不引入任何 AndroidX / 网络库 / JSON 库（使用系统 org.json），纯净可控
- **崩溃捕获**：全局异常捕获，崩溃现场可导出
- **诊断日志**：定位 / 推送链路日志，可导出分享排查
- **网络状态监听**：断网自动提示与重试

---

## 🛠️ 编译

### 编译环境声明

> **当前版本（v9.87）在 ARM64 Linux 环境编译**（aarch64 架构，JDK 17 + Android SDK）。
> 产物经 v1+v2+v3 三重签名验证，与历史版本同一 keystore 签名，可平滑覆盖升级。

### 方式 A：手工流水线（ARM64 Linux，推荐，与正式发布一致）

依赖：JDK 17、Android SDK（`platforms;android-30` + `build-tools;35.0.0`）、`aapt`、`weather.keystore` 签名文件。

```bash
python3 build_release.py
```

流水线五步：
1. **manifest 临时注入**版本信息（package / versionCode 97 / versionName 9.87 / minSdk 24 / targetSdk 33），构建后自动恢复，源码零残留
2. **aapt 打包资源**（res + assets）
3. **javac 编译**全部 Java 源码（Java 1.8 语法）
4. **d8 转 dex**（min-api 24）
5. **apksigner 签名**（v1+v2+v3，正式 keystore）

产物：`build/WeatherTool_v9.87.apk`（约 800KB）

分支构建：
```bash
python3 build_p115.py    # 115% 界面分支（v9.91P，独立包名）
python3 build_large.py   # 大字版分支（独立包名）
```

### 方式 B：Gradle / Android Studio（Mac / Windows）

工程含完整 Gradle 骨架（AGP 8.5.2 / Gradle 8.7 / compileSdk 34 / minSdk 24 / targetSdk 33 / Java 17），Android Studio 直接 Open 即可 Sync 构建：

```bash
./gradlew assembleRelease
```

---

## 🚀 发布流程（GitHub 开源）

- 每次发版：本地 `build_release.py` 构建正式 APK → 更新 README 功能清单与版本记录 → 提交推码 → 打 tag → 创建 GitHub Release（挂 APK + 源码包）
- 自动化设施：`.github/workflows/release.yml`（推送 tag 自动云端构建，可选 Secrets 签名）、`scripts/release_local.bat / .sh`（本地一键发布，需 gh CLI）
- **维护约定：每次更新版本后同步更新本 README**（版本号、功能特性、更新记录）

---

## 📋 更新记录

### v9.87（当前正式版 · versionCode 97）
- 多天气源管理器：内置五类数据源可切换（Open-Meteo 默认 / 和风 / 心知 / 彩云 / 高德）
- 每日定时推送升级精确闹钟（Doze 下准时触发）
- 后台缓存刷新（每小时）+ 后台常驻保活（前台服务）
- 设置面板横向分页重构
- v9.87-fix：AGPS 注入定位优化（移植 ROM / 无 GMS 加速冷启动）
- v9.87-fix：预警监控调度升级精确闹钟 + 权限降级链（修复后台推送失效）
- v9.87-fix：后台推送链路诊断打点（WeatherDiag，仅 logcat 输出，无功能副作用）
- ARM64 Linux 环境编译

### v9.86 及更早
- 天气核心、级联搜索、语音播报、桌面小组件、预警监控（v9.78 重构）、缓存清理（v9.73）等长期积累功能

---

## 📁 目录结构

```
weathertool/
├── app/src/main/          # 全部源码（42 个 Java 类 + res + assets）
│   └── java/com/simpleweather/app/
├── build_release.py       # 正式版构建脚本（aapt → javac → d8 → apksigner）
├── build_p115.py          # 115% 界面分支构建脚本
├── build_large.py         # 大字版分支构建脚本
├── scale_patch/           # 界面缩放补丁源码
├── .github/workflows/     # GitHub Actions 自动发布流水线
├── scripts/               # 本地一键发布脚本
├── app/build.gradle       # Gradle 构建配置（AGP 8.5.2）
└── weather.keystore       # 正式签名（不入库，本地保留）
```
