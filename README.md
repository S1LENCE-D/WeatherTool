# 简洁天气 WeatherTool

> 极简、纯净、零广告的安卓天气应用 · 酷安 @Eartecd
>
> 当前版本：**v9.87-fix1**（versionCode 98）

零第三方依赖，纯 Java + Android framework 编写，单 APK 约 800KB。

---

## 功能

### 天气核心
- 实时天气 + 多日预报：温度、体感、湿度、风速风向、紫外线、日出日落、月相
- 多天气源可切换（Open-Meteo / 和风 / 心知 / 彩云 / 高德）
- 省市区级联搜索，GPS / 网络定位
- 本地缓存，弱网断网不空白

### 推送与后台
- 每日定时天气简报
- 气象预警监控推送（30 分钟一查）
- 自定义提醒：温度、湿度、紫外线阈值触发
- 后台常驻保活，开机 / 更新后自动恢复调度

### 界面
- 2×2 / 2×4 桌面小组件
- 雷达雨图、自绘动效、主题色板
- 大字版 / 115% 界面分支（独立包名，可并存）

---

## 编译

> 当前版本在 **ARM64 Linux 环境编译**（JDK 17 + Android SDK）。

### 方式 A：手工流水线（Linux，与正式发布一致）

```bash
python3 build_release.py
```

流程：注入版本信息 → aapt 打包资源 → javac 编译 → d8 转 dex → apksigner 签名。
产物：`build/WeatherTool_v9.87.apk`。

另有分支构建：`python3 build_p115.py`（115% 界面）、`python3 build_large.py`（大字版）。

### 方式 B：Android Studio（Mac / Windows）

工程含完整 Gradle 配置，直接 Open 后 Sync 构建即可：

```bash
./gradlew assembleRelease
```

---

## 更新记录

- **v9.87-fix1**：修复日出日落/月相在部分 DPI 下挤压换行；小组件内容扩充（2x4 预报行新增降水概率与低温分列、新增日出日落行；2x2 补充风力）；和风天气新增个人专属 API Host 输入框（留空默认官方 devapi.qweather.com，自动补全 https）
- **v9.87**：多天气源切换、定时推送可靠性优化、定位优化、修复后台预警推送失效、ARM64 环境编译

---

## 工程说明

本仓库为**单一 Android 工程**（`app/`，包名 `com.simpleweather.app`，经典 Java View + Compose 界面）。

> **MIUIX 独立工程（weathertool-miuix/）已彻底废弃**：源码仅本地保留，不维护、不构建、不纳入本仓库。
