# AGENTS.md —— 提交前规约（WeatherTool）

> 本文件约束 AI 助手（黍）在本仓库的一切写操作。**放在仓库里，随代码走**；
> 新增或修改本文件本身，须先经博士确认。

---

## 一、每次提交前必做（自检清单）

### 1. 认清田界
- `git rev-parse --show-toplevel` 确认仓库、`git branch --show-current` 确认分支（默认 `main`）。
- 一次只动一个仓库、一类事，不跨仓库混合提交。

### 2. 看清改动
- `git status --short` 与 `git diff` 全文过一遍，逐行确认没有"顺手改"的夹带。
- `git status --ignored --short` 复核，确保没有把 `build/`、`.gradle/`、`local.properties` 纳入。

### 3. 查毒（敏感信息扫描）
- 暂存区扫描：
  ```bash
  git diff --cached | grep -nEi 'ghp_|github_pat_|token|password|passwd|secret|BEGIN [A-Z ]*PRIVATE KEY|\.jks|keystore|base64'
  ```
- 令牌、密钥、keystore、私密绝对路径、内网地址**一律不得入库**。
- **已知例外**：`gradle.properties` 中的 `/opt/android-sdk/build-tools/arm64-37.0.0/...`
  是本机 ARM64 构建与 CI 兼容所需，已在 README 说明，允许保留。

### 4. 验土（构建验证）
- 改动 Java / 资源 / Gradle 脚本后，必须编得出来：
  ```bash
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
  export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
  ./gradlew :app:assembleRelease --no-daemon --console=plain
  ```
- 期望 `BUILD SUCCESSFUL`，产物 `app/build/outputs/apk/release/app-release-unsigned.apk`（约 520–545 KB）。
- 只改文档时可跳过构建，但汇报中须注明"未构建验证"。

### 5. 不碰发版开关
- **不打 tag、不推 tag**——推 `v*` 即触发 Actions 发版。
- **不改 `versionCode` / `versionName`**，除非博士明确要求出新版本；且**先问本次用哪个版本号**。
  v10.1 起正式版与测试包版本号解绑：debug 产物自带 `-test` 后缀，测试包不再跟着正式版一起跳号。
- **不改 `.github/workflows/*.yml`**（令牌的 Workflows 写权限未实测；需要时先问）。

### 6. 提交粒度与信息
- 一次提交只做一类事，沿用现有风格：`feat:` / `fix:` / `docs:` / `chore:` + 中文摘要。
- 不 `--amend` 已推送的提交、不 `push --force`、不改写历史、不删远程分支或 tag。

### 7. 收尾复核
- `git log --oneline -3`、`git status` 干净；推送后 `git ls-remote --heads origin` 核对远端一致。

---

## 二、动手前必须先问博士

- **发版**：打 tag、创建 Release、改版本号（**先问用哪个号**）、重发历史版本。
- **破坏性操作**：删除仓库文件 / 分支 / tag、`--force`、`reset --hard`、改仓库可见性或名称。
- **权限与凭证**：任何涉及令牌、Secrets、账号设置、CI 权限的操作。
- **对外承诺**：许可证、隐私与数据说明、README 中的功能与兼容性承诺。
- **依赖与结构**：新增第三方依赖、改包名 / 目录结构 / 公开接口 / `applicationId`。
- **无法本地验证的改动**：签名行为、CI 行为、真机行为。
- **事实不明或文档自相矛盾**（如命令名、接口名不确定）——先问，不擅自猜。
- **超出本次任务范围的**目录、接口与配置。

---

## 三、汇报格式（每次改完）

1. 受影响文件清单，逐个说明改了什么；
2. 验证命令与结果（构建输出摘要）；
3. 明确标注：哪些是**源码事实**，哪些是我的**推断**；
4. 需博士决定的事项（若有，置于最前）；
5. 未做、未验证的部分，一并交代。

---

*立此规约者：黍（炎国农业天师，罗德岛访客）。田要年年巡，规要次次守。*
