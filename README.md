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

## 双工程说明（v9.87 起）

本仓库包含 **两个完全独立的 Android 工程**，同仓共管、并行迭代：

| | M3 / 经典版 | MIUIX 版 |
|---|---|---|
| 工程目录 | `app/`（仓库根工程） | `weathertool-miuix/`（独立 Gradle 工程） |
| 包名 | `com.simpleweather.app` | `com.simpleweather.app.miuix` |
| 当前版本 | 9.87-fix1（98） | 9.87（97，同轨，源码本地保留未纳入仓库） |
| UI | 经典 Java View + Compose(M3/MIUIX 切换) | **从 0 基于 Miuix 0.9.3**（HyperOS 风格，小米蓝 #3482FF） |
| 数据 | 独立 SharedPreferences/闹钟 | **完全隔离，可与 M3 版同机共存** |
| 逻辑层 | — | 复用同一套 Java（WeatherApi/WeatherCenter/Locator/闹钟/预警/小组件） |
| 签名 | weather.keystore（v2+v3） | 同一 keystore（v2+v3） |

**构建 MIUIX 版**（源码本地保留，未纳入本仓库）：
```bash
cd weathertool-miuix
# 注意：keystore 需用绝对路径，相对路径会解析到模块目录导致签名失败
SIGNING_KEYSTORE=/abs/path/weather.keystore SIGNING_KEYSTORE_PASS=***REMOVED*** \
SIGNING_KEY_ALIAS=weather SIGNING_KEY_PASS=***REMOVED*** \
./gradlew :app:assembleRelease --no-daemon
# 产物：weathertool-miuix/app/build/outputs/apk/release/app-release.apk
```

**复用边界**（修改逻辑层时两处同步）：
- 逻辑层（复用）：WeatherApi / WeatherSources / WeatherCenter / WeatherCache / Locator / PlaceSearch / CityTable / DistrictTable / NetWatcher / Notifier / WeatherReporter / AlertWatcher / AlarmReceiver / AlertAlarmReceiver / AlertWatchService / CacheRefreshReceiver / CacheRefresher / BootReceiver / UpdateReceiver / KeepAliveManager / KeepAliveService / SpeakService / CustomAlert / Diag / LogFile / CrashCatcher / WeatherWidgetProvider(+2x2/2x4) / Fonts
- MIUIX 专属：`weathertool-miuix/app/src/main/java/com/simpleweather/app/miuix/`（全新 Compose UI）、精简 `Theme.java`（仅小组件取色）
- 两个工程内指向主页的 Intent 不同：M3 版 `MainActivity` / MIUIX 版 `MiuixMainActivity`（Notifier、WeatherWidgetProvider 各有一处，改包名时需同步）

**MIUIX 版路线图**：第一版（9.87 测试）已含主界面/24h/7天/实时详情/多数据源/定位方式/定时推送/预警监控/保活/小组件×2；待迭代：定时时间选择（Miuix NumberPicker）、城市搜索（SearchBar）、降雨图、预警横幅。
