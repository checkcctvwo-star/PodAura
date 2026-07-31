# 播客下载 + 转MP3 实现计划（三）：收尾 — 端到端测试 + Release APK + 完整开发文档

> **面向 AI 代理的工作者：** 必需子技能：superpowers:subagent-driven-development。步骤使用复选框（`- [ ]`）跟踪。本计划是系列计划的**第三个（收尾）**，与计划二（里程碑 4-7）合并执行。

**目标：** 计划二把转码 hook 进下载流程后，本计划负责收尾交付：
1. **端到端仪器测试**（CI 编译验证 + 设备运行验证）
2. **Release APK 构建与发布**（可装机运行的 APK + GitHub Release 发布）
3. **完整开发文档**（`docs/` 下架构 / 构建 / 转码流程 / 使用说明）

**诚实边界（重要）：**
- 本地无 Android SDK、无设备。**CI（ubuntu）只能保证编译通过 + 单测通过 + APK 产物生成。**
- **运行时转码成功**（FFmpegKit native libmp3lame 实际解码编码、SAF content:// 写入、下载完成 hook 真实触发）**必须由用户在真机上验证**——CI 无设备无法跑仪器测试的运行时路径。
- 因此本计划的"端到端测试"分两层：**编译可过的 androidTest（CI 验证 `assembleAndroidTest`）** + **设备运行手册（用户执行）**。AI 不谎称运行时已验证。

**技术栈：** Kotlin 2.4.10 / KMP / Room3 / FFmpegKit (jmir1:1.18) / FileKit 0.14.2 / GitHub Actions / gh CLI。

---

## 范围说明

覆盖设计规格剩余项：
- §9 里程碑 7（端到端验证 + release APK）的深化
- 完整开发文档（规格 §11 未列但用户最终要求"完整详细以及所有的开发文档"）
- GitHub Release 发布（用户要"完美构建好可以运行的 apk 文件"，需可下载）

前置依赖：计划二（里程碑 4-6）全部完成且 CI 绿。本计划在计划二基础上执行。

> **执行约束：** 本地无 SDK，所有构建靠 CI（每轮约 8 分钟）。子代理写代码 + commit + push，CI 验证。当前分支 `feature/podcast-downloader`。

---

## 架构决策

| # | 决策 | 依据 |
|---|---|---|
| 1 | **Release 签名策略：debug 签名 APK 为主交付物，CI 生成临时 keystore 的 release 变体为可选增强** | 用户未提供签名密钥（已睡）。debug 签名 APK（`assembleGithubDebug`）可装机运行，满足"可运行"。正式 release 签名需用户后续配 GitHub Secret（密钥库），本计划给出 CI 生成临时 keystore 的 release buildtype 作为"能产出 signed release APK"的可选路径，但主交付仍是 debug APK。 |
| 2 | **端到端测试 = 编译验证 androidTest + 设备运行手册** | CI ubuntu 无 Android 模拟器（启动模拟器慢且 flaky，跨平台不稳）。androidTest 写成可编译（CI `assembleAndroidTest` 验证），运行时由用户在真机/模拟器跑。AI 诚实标注未运行时验证。 |
| 3 | **GitHub Release：打 tag + `gh release create` + 上传 APK artifact** | 用户要可下载的 APK。CI 构建后用 gh CLI（classic PAT 已配）创建 Release 并上传 APK。tag = `v0.1.0-podcast-downloader`。 |
| 4 | **文档放 `docs/`，4 篇：ARCHITECTURE / BUILD / TRANSCODE_FLOW / USAGE + 更新 README** | 用户要"完整详细以及所有的开发文档"。覆盖架构、构建、转码流程、使用。README 加项目说明与 fork 来源。 |

---

## 文件结构

**新建：**
- `platform/android/app/src/androidTest/kotlin/com/skyd/podaura/TranscodeHookTest.kt` — 仪器测试：用本地 fixture 音频跑 Transcoder + 验证输出 mp3
- `platform/android/app/src/androidTest/kotlin/com/skyd/podaura/SafWriterTest.kt` — 仪器测试：SAF 写入 + 目录创建 + 重名处理
- `platform/android/app/src/androidTest/resources/fixtures/` — 测试音频样本（小段 wav/m4a，或测试中生成）
- `docs/ARCHITECTURE.md` — 模块架构、依赖图、hook 点
- `docs/BUILD.md` — 构建环境、CI、出 APK 步骤
- `docs/TRANSCODE_FLOW.md` — 下载到 mp3 的完整时序、错误处理
- `docs/USAGE.md` — 用户使用说明（配 SAF 目录、命名模板、码率、自动转码）
- `docs/DEVICE_TESTING.md` — 设备运行验证手册（用户照做）

**修改：**
- `.github/workflows/android.yml` — 加 `assembleAndroidTest`（编译验证仪器测试）；加 release job（tag 触发，上传 APK 到 GitHub Release）
- `README.md`（若存在）或新建 `docs/README.md` — 项目说明、fork 来源、GPL-3.0
- `platform/android/app/build.gradle.kts` — 可选：加 release buildtype + 临时签名配置

---

## 里程碑 R1：端到端仪器测试

### 任务 R1.1：TranscodeHook 仪器测试

**文件：** `platform/android/app/src/androidTest/kotlin/com/skyd/podaura/TranscodeHookTest.kt`

- [ ] **步骤 1：** 读 `transcoder/src/androidMain/.../Transcoder.android.kt` 确认 `transcode(input, output, config): Flow<TranscodeProgress>` 签名。读 `TranscodeHook`（计划二产出）确认 `onDownloadSuccess(entity)` 入参。
- [ ] **步骤 2：** 写测试：用 app androidTest resources 里一段小音频 fixture（或测试内用 FFmpegKit 生成一段 sine wav），调 `Transcoder.transcode(fixture, outputMp3, TranscodeConfig(bitrateKbps=128))`，collect flow，断言：输出文件存在、大小 > 0、扩展名 .mp3。
- [ ] **步骤 3：** （设备依赖）写 TranscodeHook.onDownloadSuccess 的集成测试：构造一个 DownloadEntity（path 指向 fixture、url 带 .m4a 扩展名），mock/注入偏好（AutoTranscode=true, DownloadRootDir=测试 SAF URI），调 onDownloadSuccess，断言 DownloadEntity.outputUri 非空 + transcodeStatus="success"。**此步需设备 SAF 权限，CI 仅编译。**

> 执行前确认：androidTest 的依赖（androidTestImplementation）、Koin test 注入方式、fixture 资源路径读取。读现有 androidTest（若有）确认模式。

### 任务 R1.2：SafWriter 仪器测试

**文件：** `platform/android/app/src/androidTest/kotlin/com/skyd/podaura/SafWriterTest.kt`

- [ ] **步骤 1：** 测试 writeTranscodedToSaf：用 InstrumentationRegistry 获取 context，创建临时 tree（或用 cacheDir 模拟），写文件，断言目录创建、文件内容一致、重名追加 (2)。
- [ ] **步骤 2：** Commit + push CI 验证 `assembleAndroidTest` 编译通过。

---

## 里程碑 R2：Release APK 构建与发布

### 任务 R2.1：确保 debug APK 产物（主交付）

- [ ] **步骤 1：** 确认 `.github/workflows/android.yml` 已有 `assembleGithubDebug` + upload-artifact（计划一已建）。计划二 hook 完成后此 APK 即含转码功能。这是主交付物，**保证可装机运行**。

### 任务 R2.2（可选增强）：release buildtype + 临时签名

**文件：** `platform/android/app/build.gradle.kts` + `.github/workflows/android.yml`

- [ ] **步骤 1：** 在 app build.gradle.kts 加 `signingConfigs` + `buildTypes.release`，签名来自环境变量（CI 生成临时 keystore：`keytool -genkey`，base64 存临时文件）。若环境变量缺失则降级 debug 签名（`signingConfig = signingConfigs.getByName("debug")`）保证不阻断构建。
- [ ] **步骤 2：** android.yml 加 `assembleGithubRelease` step，上传 release APK artifact。
- [ ] **注意：** 临时 keystore 非持久（每次 CI 不同签名，无法升级覆盖安装）。仅作"能产出 signed release"演示。正式签名待用户配 Secret。**若风险高/CI 不稳，跳过此任务，主交付用 debug APK。**

### 任务 R2.3：GitHub Release 发布

**文件：** `.github/workflows/android.yml`（加 release job）

- [ ] **步骤 1：** 加 release job：on tag `v*` 触发，build APK，`gh release create ${{ github.ref_name }} --notes ...`，上传 APK asset。用 classic PAT（已配 `gh auth`）。
- [ ] **步骤 2：** （AI 执行时）本地打 tag `v0.1.0-podcast-downloader` 并 push 触发 release job。或构建后手动 `gh release create` 上传 artifact。
- [ ] **步骤 3：** 验证 Release 页面有可下载 APK。

---

## 里程碑 R3：完整开发文档

### 任务 R3.1：ARCHITECTURE.md

**文件：** `docs/ARCHITECTURE.md`

- [ ] **步骤 1：** 写模块架构：fundation / shared / downloader / transcoder / platform(android) 依赖关系图（文字或 mermaid）。Koin 模块组织。hook 点（DownloadStarter / DownloadManager.listenDownloadEvent）。DataStore + @Preference 模式。Room 迁移链（AppDatabase v28, DownloadDatabase v2）。

### 任务 R3.2：BUILD.md

**文件：** `docs/BUILD.md`

- [ ] **步骤 1：** 写构建环境：JDK 25 (Zulu)、AGP 9.3.1、Kotlin 2.4.10、compileSdk 37、Gradle 9.6.1。CI 流程（android.yml steps）。本地构建前提（Android SDK 37、build-tools 37.0.0）。出 APK 命令（`./gradlew :platform:android:app:assembleGithubDebug`）。产物路径。常见问题（阿里云镜像坑、KSP 解析）。

### 任务 R3.3：TRANSCODE_FLOW.md

**文件：** `docs/TRANSCODE_FLOW.md`

- [ ] **步骤 1：** 写完整时序：用户点下载 -> DownloadStarter.download（渲染临时文件名、指向 cacheDir DOWNLOAD_TEMP_DIR）-> IDownloadManager.download -> 下载完成 Event.Success -> DownloadManager.listenDownloadEvent -> TranscodeHook.onDownloadSuccess：读偏好（AutoTranscode/RootDir/Template/Bitrate/KeepOriginal）-> EpisodeMetadataMapper -> shouldTranscode? -> Transcoder.transcode（FFmpegKit -c:a libmp3lame）-> SafWriter 写 SAF {root}/{show}/{name}.mp3 -> 更新 DownloadEntity(outputUri/transcodeStatus/finalSize) -> 通知展示 finalSize。错误处理：SAF 失效/转码失败/空间不足/文件名冲突 各分支。

### 任务 R3.4：USAGE.md

**文件：** `docs/USAGE.md`

- [ ] **步骤 1：** 写用户使用说明：安装 APK -> 首次配置（设置-传输：选 SAF 下载根目录、开自动转 MP3、选码率、选命名模板、是否保留原文件）-> 订阅播客 -> 下载单集 -> 查看 SAF 目录 {root}/{节目名}/{集名}.mp3。故障排查（SAF 权限丢失、转码失败看 transcodeStatus、空间不足）。

### 任务 R3.5：DEVICE_TESTING.md + README

**文件：** `docs/DEVICE_TESTING.md`、`docs/README.md`（或根 README.md）

- [ ] **步骤 1：** DEVICE_TESTING.md：用户照做的设备验证清单——装机、配 SAF、下载一集、确认 mp3 产出、播放、DB 记录、通知大小。每步预期结果 + 异常处理。
- [ ] **步骤 2：** README：项目说明（fork PodAura、播客下载+转MP3）、GPL-3.0、链接到 docs/ 各篇、构建速查、CI 状态。

---

## 里程碑 R4：最终审查 + 合并

### 任务 R4.1：整分支代码审查

- [ ] **步骤 1：** 用 superpowers:code-review（双轴线：Standards + Spec）对整个 feature/podcast-downloader 分支审查（计划一+二+三全部 diff）。
- [ ] **步骤 2：** 修复审查发现的 critical/major。

### 任务 R4.2：合并决策

- [ ] **步骤 1：** superpowers:finishing-a-development-branch：决定合并到 master / 开 PR / 保留分支。考虑 GPL-3.0 上游（fork of PodAura）。
- [ ] **步骤 2：** 更新 SDD 进度账本 `.superpowers/sdd/progress.md` 标记全系列完成。

---

## 自检

### 1. 规格覆盖度
- §9 里程碑 7 端到端：✅ R1（编译验证 + 设备手册，诚实分层）
- Release APK：✅ R2（debug 主交付 + release 可选 + GitHub Release）
- 文档：✅ R3（5 篇 + README）
- 整分支审查 + 合并：✅ R4

### 2. 诚实性
- 明确标注：CI 编译通过 ≠ 运行时转码成功。运行时需设备验证（DEVICE_TESTING.md）。
- 临时 keystore 非持久，正式签名待用户配 Secret。
- 不谎称仪器测试运行时通过。

### 3. 交付物清单（用户醒来应见）
- [ ] CI 绿（编译 + 单测 + APK 产物）
- [ ] GitHub Release `v0.1.0-podcast-downloader` 含可下载 APK
- [ ] `docs/` 5 篇文档 + README
- [ ] DEVICE_TESTING.md 验证手册（用户照做确认运行时）
- [ ] 整分支审查报告（无 critical 残留）

---

## 执行交接

计划已保存到 `docs/superpowers/plans/2026-07-31-podcast-downloader-m4-m7-release.md`。

**执行方式：** 子代理驱动 + CI 验证。文档任务（R3）可多开 subagent 并行（5 篇文档独立）。测试任务（R1）需先读计划二产出的 TranscodeHook/SafWriter 实际签名。Release 任务（R2）在 APK 编译通过后做。审查（R4）最后。

**与计划二的关系：** 计划二（里程碑 4-6 hook 转码）是本计划前置。执行顺序：计划二 4.1->4.4 + 5.x + 6.x 完成 CI 绿 -> 本计划 R1（测试编译）-> R2（release）-> R3（文档，可与 R1/R2 并行）-> R4（审查合并）。
