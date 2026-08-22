# 简洁天气 WeatherTool

> 极简、纯净、零广告的安卓天气应用 · 酷安 @Eartecd
>
> 当前版本：**v9.88.3**（versionCode 115）

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

- **v9.88.3**：🔄 后台静默更新——天气/预警每 15 分钟自动保持最新，定时播报错过自动补发（当天不重复），预警监控更灵敏，全程无通知栏痕迹
- **v9.88.2**：📁 日志按「天」归档，同天崩溃/重启共用一份，排查问题更方便
- **v9.88.1**：🧭 修复无 GPS 硬件设备（如小米平板）定位时闪退
- **v9.88**：✨ 毛玻璃新视觉——小组件毛玻璃质感与多尺寸自适应、预警等级色条与配色优化、APK 体积大幅精简、数据来源动态显示
- **v9.87-fix1**：🕐 修复日出日落/月相显示挤压；🪟 小组件内容扩充（降水概率、低温、日出日落、风力）；☁️ 和风天气支持个人专属 API Host
- **v9.87**：🌐 多天气源切换、🔔 后台预警推送可靠性提升、📍 定位优化

---

## 仓库结构

```
app/                    # 主工程（纯 Java，包名 com.simpleweather.app）
.github/workflows/      # GitHub Actions 自动构建发布（推送 v* 标签触发）
```

## 工程说明

本仓库为**单一 Android 工程**（`app/`，包名 `com.simpleweather.app`，经典 Java View 界面，Compose 已彻底移除）。
