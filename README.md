# 简洁天气 WeatherTool

> 极简、纯净、零广告的安卓天气应用 · 酷安 @Eartecd
>
> 当前版本：**v9.88.3**（versionCode 114）

零第三方依赖，纯 Java + Android framework 编写（无 Compose / Kotlin），单 APK 约 **500KB**。

---

## 功能

### 天气核心
- 实时天气 + 多日预报：温度、体感、湿度、风速风向、紫外线、日出日落、月相
- 多天气源可切换（Open-Meteo 默认 / 和风 / 心知 / 彩云 / 高德），各源 Key 隔离保存
- 省市区级联搜索，GPS / 网络定位
- 本地缓存，弱网断网不空白

### 推送与后台
- 每日定时天气简报
- 气象预警监控推送（30 分钟一查）
- 自定义提醒：温度、湿度、紫外线阈值触发
- 后台常驻保活，开机 / 更新后自动恢复调度

### 界面
- 2×2 / 2×4 桌面小组件：毛玻璃质感、多尺寸自适应、深浅主题联动
- 预警详情弹窗（等级色条）+「预警信息低饱和显示」开关
- 雷达雨图、自绘动效、主题色板、日出日落弧线
- 大字版 / 115% 界面分支（独立包名，可并存）

---

## 编译

> 当前版本在 **ARM64 Linux 环境编译**（JDK 17 + Android SDK），GitHub Actions 亦可持续集成。

### 方式 A：手工流水线（Linux，与正式发布一致）

```bash
python3 build_release.py
```

流程：注入版本信息 → aapt 打包资源 → javac 编译 → d8 转 dex → apksigner 签名。
产物：`build/WeatherTool_v9.88.3.apk`。

历史分支构建脚本已归档至 `scripts/legacy/`（115% 界面 / 大字版），仅作参考。

### 方式 B：Android Studio（Mac / Windows）

工程含完整 Gradle 配置，直接 Open 后 Sync 构建即可：

```bash
./gradlew assembleRelease
```

### 方式 C：GitHub Actions 自动发布（推荐）

推送 `v*` 标签即触发 `.github/workflows/release.yml` 自动构建并发布到 Releases：

```bash
git tag v9.88 && git push origin v9.88
```

---

## 更新记录

- **v9.88.3**：平板载入 MSN 云图提速——平板 UA 无 Mobile 标记会被按桌面版渲染（瓦片大、资源多、加载慢），注入移动版特征让服务端返回轻量页面
- **v9.88.2**：日志文件按「天」一份、同天闪退/重启复用同一文件追加写入（此前每次启动新建，排查崩溃困难）；Download 保留最近 5 份
- **v9.88.1**：修复无 GPS 硬件设备（如小米平板）获取定位时闪退——此类设备系统无 GPS_PROVIDER，`getLastKnownLocation` / `requestLocationUpdates` 会抛 IllegalArgumentException（非 SecurityException）；已扩大异常捕获 + 监听前 provider 存在性检查；日志标题版本号改为动态读取
- **v9.88**：毛玻璃背景 + 多尺寸自适应（v9.88→9.88.11 共 12 轮迭代，最终版）
  - 小组件：毛玻璃四层质感、2x4 高度三档显隐、4x4 尺寸可调修复、载入崩溃修复
  - 预警：详情弹窗等级色条填满、主页横条开关即时重绘、低饱和水彩配色、文字可读性优化
  - 其他：主页数据来源随天气源动态显示、Open-Meteo 标注默认源、彻底清除 Compose（APK 1.46MB→515KB）
  - 发布：GitHub Actions 自动发布打通（签名 Secrets + 权限 + 镜像 + ARM64 工具链兼容）
- **v9.87-fix1**：修复日出日落/月相在部分 DPI 下挤压换行；小组件内容扩充（2x4 预报行新增降水概率与低温分列、新增日出日落行；2x2 补充风力）；和风天气新增个人专属 API Host 输入框（留空默认官方 devapi.qweather.com，自动补全 https）
- **v9.87**：多天气源切换、定时推送可靠性优化、定位优化、修复后台预警推送失效、ARM64 环境编译

---

## 仓库结构

```
app/                    # 主工程（纯 Java，包名 com.simpleweather.app）
build_release.py        # 正式发布构建流水线（Linux，与 CI 一致）
docs/RELEASE.md         # 构建→正式发布全流程文档（发版前必读）
scripts/legacy/         # 历史构建/发布脚本归档（仅参考）
.github/workflows/      # GitHub Actions 自动构建发布（推送 v* 标签触发）
```

## 工程说明

本仓库为**单一 Android 工程**（`app/`，包名 `com.simpleweather.app`，经典 Java View 界面，Compose 已彻底移除）。

> **MIUIX 独立工程（weathertool-miuix/）已彻底废弃**：源码仅本地保留，不维护、不构建、不纳入本仓库。
