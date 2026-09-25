# 简洁天气 WeatherTool

> 极简、纯净、零广告的 Android 天气应用 · 作者：酷安 @Eartecd

**当前版本 v10.1**（versionCode 151）· 单 APK 约 **545 KB** · 纯 Java 编写，无第三方运行时依赖

---

## 特性

### 天气核心
- 实时天气与多日预报：温度、体感、湿度、风、紫外线、日出日落、月相
- 五类天气源可切换：Open-Meteo（默认，免 Key 开箱即用）、和风天气、心知天气、彩云天气、高德天气
- 省市区三级级联搜索，GPS / 网络定位
- 本地缓存，弱网或断网时读取上一次的天气数据

### 提醒与后台
- 每日定时天气简报（精确闹钟；未授予精确闹钟权限时自动降级为不精确闹钟，并在设置页给出直达系统设置的入口）
- 气象预警监控（每 15 分钟一轮，黄色及以上预警提醒）
- 自定义阈值提醒：温度、湿度、紫外线
- 后台静默刷新：天气与预警每 15 分钟更新一次，**不常驻任何后台服务**
- 开机与覆盖安装后自动恢复定时任务

### 界面
- 桌面小组件 2×2 / 2×4：毛玻璃质感，深浅主题联动，布局随尺寸自适应
- 极简模式：卡片、详情、背景一键统一视觉
- 壁纸自定义：自选图片 + 内置裁剪工具（拖动 / 缩放 / 旋转 / 网格辅助）
- 动态渐变背景、日出日落弧线、月相、指南针、紫外线日曲线
- 顶栏搜索 / 刷新 / 设置统一为液态玻璃圆形按钮，跟随背景取色
- 预警详情弹窗（等级色条）+「预警信息低饱和显示」开关

### 悬浮底栏（可选）
- 把主页 / 降雨图 / 设置三个入口收到底部悬浮栏，选中项高亮
- 液态玻璃质感：底栏按所在页面的底色取色，含高光、描边与悬浮阴影
- 页面上滑时底栏自动收起，下滑复现；不习惯可在外观设置里关掉，恢复原来的顶栏入口

### 降雨图
- 内置页面形式，与主页、设置横向切换，切页时底栏始终可用
- WebView 全程复用：只在定位或手动城市变化时重新加载

---

## 下载与安装

正式版 APK 见 [Releases](https://github.com/S1LENCE-D/WeatherTool/releases/latest)。

- 最低支持 Android 7.0（API 24）
- 每个 Release 附带的 Source code 压缩包即为该版本的完整源码
- 覆盖安装即可升级，本地设置与数据保留

---

## 编译

### 环境要求

| 项目 | 版本 |
| --- | --- |
| JDK | 17 |
| Android Gradle Plugin | 9.3.1 |
| Gradle | 9.5.0（wrapper 已内置，无需单独安装） |
| compileSdk | 37 |
| minSdk / targetSdk | 24 / 33 |

工程为纯 Java 实现（46 个源文件，约 1.7 万行），无第三方运行时依赖，除 Android SDK 外无需额外组件。

### 方式一：命令行

```bash
# 指向本地 Android SDK
echo "sdk.dir=/path/to/android-sdk" > local.properties

./gradlew assembleRelease
# 未配置签名环境变量时产物为：
# app/build/outputs/apk/release/app-release-unsigned.apk
```

### 方式二：Android Studio

直接 Open 工程根目录，等待 Gradle Sync 完成后 Build → Generate Signed Bundle / APK。

### 方式三：GitHub Actions 自动发布

推送 `v*` 标签即触发 `.github/workflows/release.yml`，自动构建并创建 Release：

```bash
git tag v10.1 && git push origin v10.1
```

也可在仓库 Actions 页面手动运行（`workflow_dispatch`，填入版本号）。

### 关于签名

CI 从仓库 Secrets 读取签名配置；未配置时输出未签名包，仅供测试。

| Secret | 说明 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | keystore 文件的 base64（`openssl base64 -in release.jks \| tr -d '\n'`） |
| `SIGNING_KEYSTORE_PASS` | keystore 密码 |
| `SIGNING_KEY_ALIAS` | 密钥别名 |
| `SIGNING_KEY_PASS` | 密钥密码 |

本地构建时设置同名环境变量即可使用正式签名。

### 在 ARM64 Linux 上编译

Google 官方仅提供 x86_64 版的 `aapt2` / `zipalign`，因此 `gradle.properties` 中通过 `android.aapt2FromMavenOverride` 与 `android.zipalignFromMavenOverride` 指向社区交叉编译版本（[Commit451/android-arm-build-tools](https://github.com/Commit451/android-arm-build-tools)）。

CI 运行在 x86_64，`release.yml` 会预建软链接使该固定路径可用，两种主机都能构建。在 x86_64 主机上本地构建时，删除这两行即可。

---

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 请求天气数据、判断网络可用性 |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | 定位当前城市 |
| `ACCESS_LOCATION_EXTRA_COMMANDS` | AGPS 注入 `force_time` / `force_xtra`，加速冷启动 |
| `ACCESS_BACKGROUND_LOCATION` | 仅声明，便于需要时在系统设置中手动开启「始终允许」 |
| `SYSTEM_ALERT_WINDOW` | 断网 / 网络异常时的全屏提示遮罩（不联网也能看到提示） |
| `POST_NOTIFICATIONS` / `VIBRATE` | 天气简报与预警通知 |
| `SCHEDULE_EXACT_ALARM` / `RECEIVE_BOOT_COMPLETED` | 定时播报准时触发（未授权则降级为不精确闹钟）、重启后恢复任务 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 引导加入电池优化白名单，保证后台提醒可靠 |
| `WRITE_EXTERNAL_STORAGE`（仅 Android 9 及以下） | 导出诊断日志 |

应用不含统计、广告或埋点 SDK，不上报用户数据；天气请求直连所选天气源。

## 数据来源与声明

天气数据来源于用户选中的天气服务商，相关版权与使用条款归各服务商所有。默认的 Open-Meteo 免 Key 即可使用，其余四家需在「设置 → 天气源」中填入自备的 API Key / Token。

---

## 更新记录

完整版本历史见 [CHANGELOG.md](CHANGELOG.md)；最新版 v10.1 带来可选悬浮底栏、内置降雨图页面，以及重写的设置页。

## 仓库结构

```
app/                    # 应用模块（纯 Java，包名 com.simpleweather.app）
  src/main/java/        # Java 源码（46 个文件）
  src/main/assets/      # 内置字体与网页资源
  src/main/res/         # 布局、绘制、图标等资源
.github/workflows/      # GitHub Actions 自动构建与发布
```

## 反馈与贡献

Issue 与 Pull Request 均欢迎。提交代码前请先执行 `./gradlew assembleRelease` 确认工程可编译。

## 许可证

本项目采用 [MIT 许可证](LICENSE)。
