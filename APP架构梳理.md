# 简洁天气 WeatherTool · APP 架构梳理

> 梳理日期：2026-08-13 ｜ 当前版本：9.87（versionCode 97）｜ 包名：com.simpleweather.app
> 技术栈：纯 Java 手写 UI，无 Gradle / 无 AndroidX / 无第三方依赖，minSdk 24 / targetSdk 33，编译平台 android-30

---

## 一、项目结构

```
weathertool/
├── app/src/main/
│   ├── AndroidManifest.xml      # 组件与权限声明
│   ├── java/com/simpleweather/app/   # 40 个 Java 类（约 12900 行）
│   ├── res/
│   │   ├── anim/                # 滑入滑出动画（rain / settings_sheet）
│   │   ├── drawable/            # 手绘 XML 图形、深浅双主题背景、小组件图标
│   │   ├── layout/              # activity_main / activity_rain_map / dialog_report / dialog_settings / widget_2x2 / widget_2x4
│   │   ├── values/styles.xml
│   │   └── xml/                 # widget_info_2x2 / widget_info_2x4
│   └── assets/
│       ├── fonts/               # Google Sans + Material Icons 字体
│       └── view.html            # 降雨图 WebView 宿主页
├── build/
│   ├── WeatherTool_v9.87.apk    # ★ 当前正式发布版
│   ├── 更新日志_V9.87.txt
│   └── archive/                 # 历史版本 APK 归档（v9.86~v9.91P）
├── build_v4.py                  # ★ 标准构建脚本（正式发布用）
├── build_large.py / build_p105.py / build_p115.py   # 旧版/机型专用构建脚本（备用）
├── scale_patch/ScaleApp.java    # 分辨率缩放补丁源文件（备用）
└── weather.keystore             # 签名密钥（***REMOVED***，勿丢失）
```

---

## 二、Android 组件清单（Manifest）

| 类型 | 组件 | 职责 |
|---|---|---|
| Activity | MainActivity | 主界面（天气展示 + 全屏设置面板） |
| Activity | RainMapActivity | 二级页：MSN 降雨图（WebView 雷达回波） |
| Service | SpeakService | 定时天气通知：强制定位 → 拉天气 → 推送简报 |
| Service | AlertWatchService | 预警检查（闹钟触发，检查完自停，无常驻） |
| Service | KeepAliveService | v9.88 后台常驻（前台服务保活，可开关） |
| Receiver | BootReceiver | 开机恢复天气闹钟 + 预警周期闹钟调度 |
| Receiver | AlarmReceiver | 每日天气简报闹钟触发 |
| Receiver | AlertAlarmReceiver | 预警周期闹钟触发（30 分钟） |
| Receiver | CacheRefreshReceiver | v9.87 后台缓存刷新闹钟（goAsync + 子线程） |
| Receiver | UpdateReceiver | 应用更新后恢复闹钟（系统覆盖安装会清闹钟） |
| Receiver | WeatherWidgetProvider2x2 / 2x4 | 桌面小组件（基类 WeatherWidgetProvider 不注册） |

权限：INTERNET、网络状态、SYSTEM_ALERT_WINDOW、定位（精/粗）、开机广播、通知、振动、忽略电池优化、前台服务、精确闹钟

---

## 三、核心类职责（按功能域）

### 1. 界面层
| 类 | 职责 |
|---|---|
| MainActivity（4702 行） | 主界面核心：自动定位 → Open-Meteo 拉取 → 实时/24h/7天渲染，30 秒自动刷新；城市管理、主题切换、全屏设置面板（showSettingsDialog）、预测性返回回退后沿用 onBackPressed |
| RainMapActivity | 降雨图 WebView 页 |
| SettingsPager | v9.87 自绘横向分页容器（无 AndroidX，手写替代 ViewPager） |
| WeatherBackView | 动态天气背景：晴（太阳光晕/光斑）/雨（粒子）/雪/云等呼应天气 |
| SunArcView / MoonPhaseView / UvDayView / CompassView | 日出日落弧线、月相、UV 24h 走势、罗盘风向标 |
| GlassDrawable | 手搓毛玻璃背景（模糊快照 + 区域裁切） |
| M3Switch / WheelView | 自绘 Material 3 开关 / 滚轮选择器 |

### 2. 数据层
| 类 | 职责 |
|---|---|
| WeatherSource（接口） / WeatherCenter（单例仓库） | 天气数据统一入口：所有"拉天气"调用方（主页/推送/小组件）走同一条「拉取 + 写缓存」链路 |
| WeatherSources | v9.87 多天气源管理器：内置 Open-Meteo（默认）/ 和风 / 心知 / 彩云 / 高德，支持自定义 |
| WeatherApi | Open-Meteo 接口封装（免费免 key，一次请求含实时/24h/7天/日出日落） |
| WeatherCache | SharedPreferences 本地缓存：打开先渲染上次数据，弱网不空白 |
| CityTable / DistrictTable | 城市表 / 2991 个区县坐标表（WGS-84），IP 定位换算坐标用 |
| PlaceSearch | v9.51 级联搜索（省份 → 地级市 → 区县） |

### 3. 定位与网络
| 类 | 职责 |
|---|---|
| Locator | v9.22 定位工具：2 分钟内 last known 且精度 ≤150m 秒回，GPS/网络/IP 并行 |
| NetWatcher | NetworkCallback 实时监听"可上网"（以 VALIDATED 为准，Wi-Fi 无外网也算断网） |

### 4. 提醒与通知
| 类 | 职责 |
|---|---|
| CustomAlert | v9.87test 自定义气象提醒：温度/湿度/紫外线阈值规则 |
| AlertWatcher | v9.78 预警监控静态管理：开关/地区快照/去重/周期闹钟调度 |
| Notifier | v9.75 统一通知中枢：渠道创建、通知构建与发送 |
| WeatherReporter | 定时天气通知：设置存储 + AlarmManager 每日调度 |
| SpeakService / AlarmReceiver | 简报拉取与定时触发 |

### 5. 后台与保活
| 类 | 职责 |
|---|---|
| CacheRefresher / CacheRefreshReceiver | v9.87 后台缓存自动刷新（替代主界面 Handler 定时器，规避后台启动限制） |
| KeepAliveManager / KeepAliveService | v9.88 常驻开关与前台服务（划掉任务后推送/刷新照常） |
| CrashCatcher | 全局崩溃捕获：crash.log 写入外部目录 + 公共下载目录 |

### 6. 小组件与工具
| 类 | 职责 |
|---|---|
| WeatherWidgetProvider（基类）+ 2x2 / 2x4 | 桌面小组件渲染与刷新（固定布局，拉伸只放大内容区） |
| Theme | 主题管理：深色/浅色/跟随系统，全 App 颜色统一经此取用 |
| Fonts | Google Sans + Material Icons 字形，中文回退系统字体 |

---

## 四、关键数据流

```
① 启动流：MainActivity → Locator 定位 → WeatherCenter(WeatherSources) 拉取
          → WeatherCache 写缓存 → 渲染 UI（背景/实时/24h/7天/UV/月相…）
② 后台刷新流：AlarmManager 闹钟 → CacheRefreshReceiver → 子线程拉取 → 写缓存
③ 定时简报流：WeatherReporter 调度 → AlarmReceiver → SpeakService → 定位+拉取 → Notifier 推送
④ 预警流：AlertWatcher 周期闹钟 → AlertAlarmReceiver → AlertWatchService → 检查阈值 → 通知
⑤ 小组件流：WeatherWidgetProvider 刷新 → WeatherCenter 拉取 → 渲染 widget 布局
```

---

## 五、V9.87 设置面板结构（本轮改版重点）

- 形态：**全屏 Dialog**（非系统悬浮窗）——窗口 MATCH_PARENT、dimAmount 0、无圆角
- 结构：一级列表（外观 / 定位与后台 / 天气源 / 自定义提醒）→ 二级页面横向滑动切换
- 全面屏适配：窗口延伸系统栏（LAYOUT_FULLSCREEN + decorFitsSystemWindows(false)），状态栏与底部手势条区域铺主题底色，图标深浅随主题设 LIGHT_* flag；内容按 insets 避让状态栏/手势条/键盘
- 交互：胶囊「完成」按钮、按压波纹、锁定字号防系统放大、切换防抖防误触、滑入滑出动画
- 实现要点：`handleSettingsBack` 处理返回键；build*Page 签名 `(boolean dark, Dialog d, SettingsPager pager)`

---

## 六、构建流程（build_v4.py，约 7 秒）

```
aapt package（编译资源，-A assets 打字体）
  → javac -source 1.8 编译（java 目录 + gen 生成的 R.java）
  → d8 --min-api 24 打 dex
  → dex 塞入 base.apk
  → apksigner 签名（v1 + v2 + v3，weather.keystore / ***REMOVED***）
  → 复制为 build/WeatherTool_v{版本}.apk
```

关键点：编译平台统一 android-30（老 aapt1 只能解析 30 资源表）；发布校验 = `aapt dump badging`（版本号/包名）+ `apksigner verify --verbose`（签名）。

---

## 七、版本与归档

- 当前正式版：**WeatherTool_v9.87.apk**（9.87 · 酷安@Eartecd / versionCode 97，MD5 df7c326547a4136d7919342875b016c0，v2/v3 签名通过）
- 历史版本：build/archive/（v9.86 及更早正式版、各 test 版、P105/P115 机型版）
- 发布约定：不擅自升级 versionCode；正式版文件名不带 test 后缀
