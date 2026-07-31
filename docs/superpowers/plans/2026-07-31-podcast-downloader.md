# 播客下载 + 转MP3 实现计划（一）：风险验证 + CI骨架 + transcoder 模块

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 验证基座可构建 + FFmpeg 可用于转码，建立 fork 仓库 + CI 出 APK，并交付一个有单元测试覆盖的 transcoder KMP 模块（转码决策 + 命名模板 + FFmpeg 封装）。

**架构：** 新增独立 KMP 模块 `transcoder`（commonMain 纯逻辑 + androidMain 用 FFmpegKit 转码），通过 Koin 注册。本计划不触碰下载落盘逻辑（留给计划二），transcoder 模块可独立编译、独立单测。

**技术栈：** Kotlin 2.4.10 / KMP / AGP 9.3.1 / JDK 25 / compileSdk 37 / Koin 4.2.2 / FFmpegKit（audio 变体）/ kotlin.test。

---

## 范围说明

本计划是系列计划的**第一个**，覆盖设计规格 `docs/superpowers/specs/2026-07-31-podcast-downloader-design.md` §9 的：

- **里程碑 1：风险验证**（gating）
- **里程碑 2：fork 仓库 + CI 骨架**
- **里程碑 3：transcoder 模块**（纯逻辑部分精确 TDD，FFmpeg 集成依赖里程碑 1 结果）

**里程碑 4-7（下载流程改造、SAF 下载位置+设置UI、文件大小展示+通知、端到端验证）不在本计划。** 理由：它们触及基座核心下载落盘逻辑、依赖里程碑 1 的验证结果（FFmpeg 库选型、基座可构建性），且需要更深读取现有下载/媒体库代码。里程碑 1 通过后，另写计划二覆盖 4-7。

---

## 架构决策（基于代码调研，修正规格假设）

调研发现规格若干假设与基座实际不符，实现时按以下决策执行（不修改规格文档，仅在此记录）：

| # | 规格假设 | 调研发现 | 本计划决策 |
|---|---|---|---|
| 1 | §3.1 用 **FFmpegKitNext** | FFmpegKitNext **不发布 Maven 制品**，须本地 Nix 编译（重）。官方前身 `com.arthenica:ffmpeg-kit-audio:6.0-2.LTS` 仍在 Maven Central（不可变），含 libmp3lame、LGPL v3（GPL-3.0 兼容）、API 与 FFmpegKitNext 一致。**且基座 app 已依赖 `com.github.jmir1:ffmpeg-kit:1.18`**（JitPack fork），其 `.so` 被 `packaging.jniLibs.excludes` 排除（mpv-lib 自带 ffmpeg native）。 | 里程碑 1 先验证 jmir1:1.18 能否复用于转码（含 libmp3lame？.so 可用？）；若不能，引入 `com.arthenica:ffmpeg-kit-audio:6.0-2.LTS` 并验证与 mpv-lib 不冲突。FFmpegKit API 调用代码两者一致（同包名 `com.arthenica.ffmpegkit`）。 |
| 2 | §3.2 转码 hook 点 = `DownloadWorker.kt` | `DownloadWorker` 只是 WorkManager 包装 + 通知层；真正下载完成在 `DownloadChecker.tryDownload()`（downloader commonMain）成功后；shared 侧 `DownloadManager.listenDownloadEvent()` 的 `Event.Success`（`shared/.../download/DownloadManager.kt:72`）能拿到 article 元数据，是更合适的 hook 点。 | 本计划 transcoder 模块独立实现，**不接入 hook**（留计划二）。计划二在 shared `listenDownloadEvent` Event.Success 处接入转码。 |
| 3 | §3.2/3.3 SAF content:// 根目录 | 基座 `MediaLibLocationPreference` 存**文件系统路径**非 SAF；但 FileKit 0.14.2 已支持 SAF（`rememberDirectoryPickerLauncher` + `bookmarkData()` 自动 `takePersistableUriPermission` + `div`/`sink` 写入），`atomicMove` 对 SAF 非原子。 | SAF 接入留计划二（里程碑 4-5）。本计划不涉及 SAF。 |
| 4 | §3.3 命名模板含 `{seasonNumber}` | 基座 `RssMediaBean` 无 season 字段，RSS 解析（`Item.kt`）无 `itunes:season`。`episodeNumber` 是 String（`RssMediaBean.episode`）。 | 本计划 `EpisodeMetadata` 含 `seasonNumber` 字段（供模板渲染单测）；实际从 RSS 填充 season 留计划二（加字段 + Migration27To28）。 |
| 5 | §7 测试 `./gradlew :transcoder:test` | downloader 模块零测试；shared 用 kotlin.test（commonTest/jvmTest）；CI 当前**不跑测试**。 | transcoder 用 kotlin.test，commonTest 写纯逻辑单测，CI 加 `test` 步骤。 |

**许可证结论**：`ffmpeg-kit-audio:6.0-2.LTS` 是 LGPL v3.0（POM 声明），audio 变体不含 GPL 库，与基座 GPL-3.0 兼容。满足规格 §2 约束。

---

## 文件结构

本计划新建/修改的文件：

**新建（transcoder 模块）：**
- `transcoder/build.gradle.kts` — 模块构建脚本（照抄 downloader 模式，精简去 Room3/Compose）
- `transcoder/src/commonMain/kotlin/com/skyd/transcoder/model/AudioFormat.kt` — 音频格式枚举 + 扩展名/MIME/URL 探测
- `transcoder/src/commonMain/kotlin/com/skyd/transcoder/model/TranscodeConfig.kt` — 转码配置（目标格式 + 码率），含校验
- `transcoder/src/commonMain/kotlin/com/skyd/transcoder/model/TranscodeState.kt` — 转码状态机 + 进度模型
- `transcoder/src/commonMain/kotlin/com/skyd/transcoder/Transcoder.kt` — `expect class Transcoder` + `shouldTranscode` 决策函数
- `transcoder/src/commonMain/kotlin/com/skyd/transcoder/naming/EpisodeMetadata.kt` — 单集元数据
- `transcoder/src/commonMain/kotlin/com/skyd/transcoder/naming/NamingTemplate.kt` — 命名模板枚举 + 渲染
- `transcoder/src/commonMain/kotlin/com/skyd/transcoder/naming/FileNameSanitizer.kt` — 文件名/文件夹名清理
- `transcoder/src/commonMain/kotlin/com/skyd/transcoder/di/TranscoderModule.kt` — Koin 模块
- `transcoder/src/androidMain/kotlin/com/skyd/transcoder/Transcoder.android.kt` — `actual class Transcoder`，FFmpegKit 实现
- `transcoder/src/jvmMain/kotlin/com/skyd/transcoder/Transcoder.jvm.kt` — `actual class Transcoder`，抛 UnsupportedOperationException
- `transcoder/src/commonTest/kotlin/com/skyd/transcoder/model/AudioFormatTest.kt`
- `transcoder/src/commonTest/kotlin/com/skyd/transcoder/model/TranscodeConfigTest.kt`
- `transcoder/src/commonTest/kotlin/com/skyd/transcoder/ShouldTranscodeTest.kt`
- `transcoder/src/commonTest/kotlin/com/skyd/transcoder/naming/NamingTemplateTest.kt`
- `transcoder/src/commonTest/kotlin/com/skyd/transcoder/naming/FileNameSanitizerTest.kt`

**修改：**
- `settings.gradle.kts` — `include` 加 `:transcoder`
- `gradle/libs.versions.toml` — （仅当里程碑 1 决定用 arthenica）加 `ffmpeg-kit-audio` 条目
- `platform/android/app/build.gradle.kts` — dependencies 加 `implementation(projects.transcoder)`
- `shared/src/commonMain/kotlin/com/skyd/podaura/di/Koin.kt` — `modules(...)` 加 `transcoderModule`
- `.github/workflows/android.yml` — 新增 CI 工作流（出 debug APK + 跑测试）

---

## 里程碑 1：风险验证

> **gating**：以下验证通过后才进入里程碑 2-3。本地环境已确认 JDK 25 (Zulu 25.0.4) + Gradle 9.6.1 wrapper 就绪。

### 任务 1.1：基座原版能本地构建

**目的**：确认基座在不改动的情况下能编译出 debug APK（验证 SDK 37 / AGP 9.3.1 / 依赖解析在本地可用）。

- [ ] **步骤 1：构建 GitHub flavor debug APK**

运行（Windows PowerShell 用 `.\gradlew.bat`，CI/Unix 用 `./gradlew`）：
```
./gradlew :platform:android:app:assembleGithubDebug
```
预期：`BUILD SUCCESSFUL`，产物在 `platform/android/app/build/outputs/apk/GitHub/debug/`。

- [ ] **步骤 2：若失败，记录错误并诊断**

常见失败点与应对：
- SDK 37 / build-tools 37.0.0 未下载 → 基座 `android.kotlin.multiplatform.library` 插件应自动下载；若失败，检查 `local.properties` 是否需要 `sdk.dir`。
- JitPack 依赖（`jmir1:ffmpeg-kit`、`aniyomiorg:aniyomi-mpv-lib`）解析失败 → 检查网络/JitPack 可达性；必要时配置阿里云镜像（settings.gradle.kts 第 28 行有注释掉的 `maven.aliyun.com`，可临时启用）。
- 内存不足 → `gradle.properties` 已配 `-Xmx12G`，应足够。

若无法在本地解决，记录错误，转而依赖里程碑 2 的 CI 验证（不阻塞里程碑 3 的纯逻辑 TDD）。

### 任务 1.2：基座原版能跑测试

- [ ] **步骤 1：跑所有 JVM 测试**

运行：
```
./gradlew test
```
预期：`BUILD SUCCESSFUL`，shared 模块的 commonTest/jvmTest 通过（downloader 无测试）。

- [ ] **步骤 2：记录测试基线**

记下通过/失败的测试数。transcoder 模块加测试后，对比确认未引入回归。

### 任务 1.3：调查基座现有 ffmpeg-kit 依赖

**目的**：决定里程碑 3 是复用 `jmir1:ffmpeg-kit:1.18` 还是引入 `arthenica:ffmpeg-kit-audio:6.0-2.LTS`。

- [ ] **步骤 1：查依赖树中的 ffmpeg-kit**

运行：
```
./gradlew :platform:android:app:dependencies --configuration githubDebugRuntimeClasspath
```
找 `com.github.jmir1:ffmpeg-kit:1.18`，确认它被引入。

- [ ] **步骤 2：查 jmir1:ffmpeg-kit:1.18 的变体与许可**

用 WebFetch 查 `https://jitpack.io/com/github/jmir1/ffmpeg-kit/1.18` 与 jmir1 的 GitHub README。确认：
- 是 audio 变体还是 full？是否含 libmp3lame？
- 许可证（LGPL？）
- `.so` 文件清单

- [ ] **步骤 3：查 packaging 排除的影响**

`platform/android/app/build.gradle.kts` 的 `packaging.jniLibs.excludes` 含 `lib/*/libffmpegkit.so`、`lib/*/libffmpegkit_abidetect.so`。确认：jmir1 的这两个 `.so` 被排除后，FFmpegKit 的 Java API（`FFmpegKit.execute`）调用时是否会因缺少 native 库而崩。

- [ ] **步骤 4：分支决策**

- **若 jmir1:1.18 含 libmp3lame 且其 `.so` 可用（取消 exclude 后与 mpv-lib 不冲突）** → 里程碑 3 复用 `libs.ffmpeg.kit`，不引入新依赖。
- **若 jmir1:1.18 不含 libmp3lame，或 .so 不可用/冲突** → 里程碑 3 引入 `com.arthenica:ffmpeg-kit-audio:6.0-2.LTS`。**注意**：两者同包名 `com.arthenica.ffmpegkit`，同时依赖会冲突，需在 app 的 `ffmpeg-kit`（jmir1）依赖上 `exclude` 或改用 arthenica 替代 jmir1（需确认 mpv-lib 是否依赖 jmir1 的 Java API；若 mpv-lib 自带 ffmpeg native 不依赖 jmir1 Java API，可直接替换）。

### 任务 1.4：FFmpeg 转码可行性验证（可选，重）

**目的**：在真机/模拟器上验证 FFmpegKit 能把 m4a 转 mp3。

- [ ] **步骤 1：写最小 androidTest**

在 `platform/android/app/src/androidTest/kotlin/com/skyd/podaura/TranscodeSmokeTest.kt` 写：放一个 `sample.m4a` 到 `assets`，用 `FFmpegKit.execute("-i $input -c:a libmp3lame -b:a 128k $output")`，断言 `ReturnCode.isSuccess` 且输出文件存在且大小 > 0。

```kotlin
package com.skyd.podaura

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TranscodeSmokeTest {
    @Test
    fun transcodeM4aToMp3() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.cacheDir, "sample.m4a").apply {
            context.assets.open("sample.m4a").use { it.copyTo(outputStream()) }
        }
        val output = File(context.cacheDir, "sample.mp3")
        val cmd = "-i \"${input.absolutePath}\" -c:a libmp3lame -b:a 128k \"${output.absolutePath}\""
        val session = FFmpegKit.execute(cmd)
        assertTrue("returnCode=${session.returnCode}", ReturnCode.isSuccess(session.returnCode))
        assertTrue(output.exists() && output.length() > 0)
    }
}
```

- [ ] **步骤 2：跑仪器测试**

需连接设备/模拟器（API 24+）：
```
./gradlew :platform:android:app:connectedGithubDebugAndroidTest
```
预期：通过。若失败，根据日志（缺 libmp3lame？.so 缺失？）回到任务 1.3 步骤 4 的分支决策。

- [ ] **步骤 3：记录结论并 Commit 决策**

在 `docs/superpowers/plans/2026-07-31-podcast-downloader.md` 的"架构决策"表 #1 补充最终选型结论。Commit：
```
git add docs/superpowers/plans/2026-07-31-podcast-downloader.md
git commit -m "[docs] Record FFmpeg library selection after risk verification"
```

---

## 里程碑 2：fork 仓库 + CI 骨架

### 任务 2.1：fork PodAura

- [ ] **步骤 1：fork**

fine-grained PAT 无法 fork。两种方式任选：
- **（推荐）网页一键 fork**：用户在 `https://github.com/SkyD666/PodAura` 点 Fork 到 `checkcctvwo-star`，仓库名建议 `PiliCast`。
- **classic PAT**：若用户已申请到 classic PAT（`repo`+`workflow` scope），用 `gh auth status` 确认后：
  ```
  gh repo fork SkyD666/PodAura --clone=false --remote=false
  ```
  （在 `https://github.com/checkcctvwo-star/PiliCast` 创建）

- [ ] **步骤 2：改 origin remote**

在 `F:\AI-porject\pod`：
```
git remote rename origin upstream
git remote add origin https://github.com/checkcctvwo-star/PiliCast.git
git remote -v
```
预期：`origin` 指向 `checkcctvwo-star/PiliCast`，`upstream` 指向 `SkyD666/PodAura`。

### 任务 2.2：新增 CI 工作流（出 debug APK + 跑测试）

不改动现有 `.github/workflows/pre_release.yml`（那是上游的 release 流程，需签名密钥）。新增一个工作流，先出 debug APK（用户暂无签名密钥）+ 跑测试。

- [ ] **步骤 1：创建 `.github/workflows/android.yml`**

```yaml
name: Android Build

on:
  push:
    branches: [master]
    paths-ignore:
      - '**.md'
      - 'docs/**'
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6

      - name: Setup JDK 25
        uses: actions/setup-java@v5
        with:
          distribution: zulu
          java-version: 25

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v6

      - name: Run Tests
        run: ./gradlew test

      - name: Assemble Debug APK
        run: ./gradlew :platform:android:app:assembleGithubDebug

      - name: Upload APKs
        uses: actions/upload-artifact@v7
        with:
          name: debug-apk
          path: platform/android/app/build/outputs/apk/GitHub/debug/*.apk
          compression-level: 9

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: test-results
          path: |
            **/build/test-results/**/*.xml
            **/build/reports/tests/**
```

- [ ] **步骤 2：Commit**

```
git add .github/workflows/android.yml
git commit -m "[ci] Add Android build workflow with tests and debug APK artifact"
```

### 任务 2.3：推送并验证 CI

- [ ] **步骤 1：推送到 origin**

```
git push origin master
```

- [ ] **步骤 2：监控 CI**

```
gh run list --repo checkcctvwo-star/PiliCast --limit 3
gh run watch --repo checkcctvwo-star/PiliCast
```
预期：`Android Build` 工作流成功，产出 `debug-apk` artifact。若失败，用 `gh run view <id> --repo checkcctvwo-star/PiliCast --log-failed` 查日志。

- [ ] **步骤 3：下载 APK 验证可安装**

用户从 Actions 页面下载 `debug-apk` artifact，解压后在手机安装（arm64-v8a 变体），确认能启动（原版 PodAura 界面）。

---

## 里程碑 3：transcoder 模块

> 依赖里程碑 1 任务 1.3 的 FFmpeg 库选型结论。纯逻辑任务（3.2、3.3）不依赖 FFmpeg，可先做。

### 任务 3.1：模块骨架

- [ ] **步骤 1：`settings.gradle.kts` 加 include**

在 `include(...)` 列表加 `:transcoder`：

```kotlin
include(
    ":shared",
    ":fundation",
    ":htmlrender",
    ":downloader",
    ":transcoder",
    ":ksp:processor",
    ":ksp:annotation",
    ":platform:android:app",
    ":platform:android:benchmark",
    ":compottie:core",
    ":compottie:main"
)
```

- [ ] **步骤 2：创建 `transcoder/build.gradle.kts`**

照抄 `downloader/build.gradle.kts` 模式，精简（无 Room3/Compose），androidMain 加 FFmpeg 依赖：

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "com.skyd.transcoder"
        minSdk = 24
        compileSdk {
            version = release(37) { minorApiLevel = 0 }
        }
        buildToolsVersion = "37.0.0"
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.kermit)
        }

        androidMain.dependencies {
            // 按里程碑 1 任务 1.3 步骤 4 的结论二选一：
            // 复用 jmir1:1.18：
            implementation(libs.ffmpeg.kit)
            // 或引入 arthenica audio（需先在 libs.versions.toml 加条目）：
            // implementation(libs.ffmpeg.kit.audio)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }

    compilerOptions {
        optIn.addAll(
            "kotlinx.coroutines.DelicateCoroutinesApi",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}
```

> 若里程碑 1 选 arthenica，先在 `gradle/libs.versions.toml` 的 `[versions]` 加 `ffmpeg-kit-audio = "6.0-2.LTS"`，`[libraries]` 加 `ffmpeg-kit-audio = { module = "com.arthenica:ffmpeg-kit-audio", version.ref = "ffmpeg-kit-audio" }`，并在 app 的 jmir1 `ffmpeg-kit` 依赖上排除或替换（见任务 1.3 步骤 4）。

- [ ] **步骤 3：app 模块依赖 transcoder**

在 `platform/android/app/build.gradle.kts` 的 `dependencies` 块加：

```kotlin
implementation(projects.transcoder)
```

- [ ] **步骤 4：确认模块可编译**

```
./gradlew :transcoder:compileCommonMainKotlinMetadata
```
预期：成功（此时模块内还没有源文件，仅验证构建脚本正确）。

### 任务 3.2：AudioFormat + TranscodeConfig + shouldTranscode（TDD）

- [ ] **步骤 1：写失败测试 `AudioFormatTest.kt`**

创建 `transcoder/src/commonTest/kotlin/com/skyd/transcoder/model/AudioFormatTest.kt`：

```kotlin
package com.skyd.transcoder.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioFormatTest {
    @Test
    fun fromExtensionLowercase() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromExtension("mp3"))
    }

    @Test
    fun fromExtensionUppercaseWithDot() {
        assertEquals(AudioFormat.M4A, AudioFormat.fromExtension(".M4A"))
    }

    @Test
    fun fromExtensionUnknown() {
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.fromExtension("xyz"))
    }

    @Test
    fun fromExtensionEmpty() {
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.fromExtension(""))
    }

    @Test
    fun fromUrlStripsQueryAndFragment() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromUrl("https://x.com/a.mp3?token=1#frag"))
    }

    @Test
    fun fromUrlNoExtension() {
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.fromUrl("https://x.com/a"))
    }

    @Test
    fun fromMimeTypeMp3() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromMimeType("audio/mpeg"))
    }

    @Test
    fun fromMimeTypeAac() {
        assertEquals(AudioFormat.AAC, AudioFormat.fromMimeType("audio/aac"))
    }

    @Test
    fun fromMimeTypeUnknown() {
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.fromMimeType("video/mp4"))
    }
}
```

- [ ] **步骤 2：写失败测试 `TranscodeConfigTest.kt`**

创建 `transcoder/src/commonTest/kotlin/com/skyd/transcoder/model/TranscodeConfigTest.kt`：

```kotlin
package com.skyd.transcoder.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranscodeConfigTest {
    @Test
    fun defaultValues() {
        val c = TranscodeConfig.DEFAULT
        assertEquals(AudioFormat.MP3, c.targetFormat)
        assertEquals(128, c.bitrateKbps)
    }

    @Test
    fun validBitratesAccepted() {
        setOf(64, 96, 128, 192).forEach { TranscodeConfig(bitrateKbps = it) }
    }

    @Test
    fun invalidBitrateLowRejected() {
        assertFailsWith<IllegalArgumentException> { TranscodeConfig(bitrateKbps = 32) }
    }

    @Test
    fun invalidBitrateHighRejected() {
        assertFailsWith<IllegalArgumentException> { TranscodeConfig(bitrateKbps = 256) }
    }

    @Test
    fun nonMp3TargetRejected() {
        assertFailsWith<IllegalArgumentException> { TranscodeConfig(targetFormat = AudioFormat.M4A) }
    }
}
```

- [ ] **步骤 3：写失败测试 `ShouldTranscodeTest.kt`**

创建 `transcoder/src/commonTest/kotlin/com/skyd/transcoder/ShouldTranscodeTest.kt`：

```kotlin
package com.skyd.transcoder

import com.skyd.transcoder.model.AudioFormat
import com.skyd.transcoder.model.TranscodeConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldTranscodeTest {
    @Test
    fun mp3SourceSkipsTranscode() {
        assertFalse(shouldTranscode(AudioFormat.MP3, TranscodeConfig.DEFAULT))
    }

    @Test
    fun m4aSourceTranscodes() {
        assertTrue(shouldTranscode(AudioFormat.M4A, TranscodeConfig.DEFAULT))
    }

    @Test
    fun opusSourceTranscodes() {
        assertTrue(shouldTranscode(AudioFormat.OPUS, TranscodeConfig.DEFAULT))
    }

    @Test
    fun unknownSourceSkipsTranscode() {
        assertFalse(shouldTranscode(AudioFormat.UNKNOWN, TranscodeConfig.DEFAULT))
    }
}
```

- [ ] **步骤 4：运行测试验证失败**

```
./gradlew :transcoder:jvmTest
```
预期：FAIL（`AudioFormat` 等类未定义，编译错误）。

- [ ] **步骤 5：实现 `AudioFormat.kt`**

创建 `transcoder/src/commonMain/kotlin/com/skyd/transcoder/model/AudioFormat.kt`：

```kotlin
package com.skyd.transcoder.model

enum class AudioFormat(val extension: String, val mimeType: String) {
    MP3("mp3", "audio/mpeg"),
    M4A("m4a", "audio/mp4"),
    AAC("aac", "audio/aac"),
    OPUS("opus", "audio/opus"),
    OGG("ogg", "audio/ogg"),
    WAV("wav", "audio/wav"),
    UNKNOWN("unknown", "application/octet-stream");

    companion object {
        fun fromExtension(ext: String): AudioFormat {
            val lower = ext.lowercase().removePrefix(".")
            return entries.firstOrNull { it.extension == lower } ?: UNKNOWN
        }

        fun fromMimeType(mime: String): AudioFormat {
            val lower = mime.lowercase()
            return entries.firstOrNull { lower.contains(it.mimeType) } ?: UNKNOWN
        }

        fun fromUrl(url: String): AudioFormat {
            val clean = url.substringBefore('?').substringBefore('#')
            val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
            return if (ext.isNotEmpty()) fromExtension(ext) else UNKNOWN
        }
    }
}
```

- [ ] **步骤 6：实现 `TranscodeConfig.kt`**

创建 `transcoder/src/commonMain/kotlin/com/skyd/transcoder/model/TranscodeConfig.kt`：

```kotlin
package com.skyd.transcoder.model

data class TranscodeConfig(
    val targetFormat: AudioFormat = AudioFormat.MP3,
    val bitrateKbps: Int = 128,
) {
    init {
        require(targetFormat == AudioFormat.MP3) {
            "Only MP3 target is supported, was $targetFormat"
        }
        require(bitrateKbps in VALID_BITRATES) {
            "bitrateKbps must be one of $VALID_BITRATES, was $bitrateKbps"
        }
    }

    companion object {
        val VALID_BITRATES = setOf(64, 96, 128, 192)
        val DEFAULT = TranscodeConfig()
    }
}
```

- [ ] **步骤 7：实现 `shouldTranscode`**

创建 `transcoder/src/commonMain/kotlin/com/skyd/transcoder/Transcoder.kt`（commonMain 部分，先只放决策函数，`expect class` 在步骤 9 补）：

```kotlin
package com.skyd.transcoder

import com.skyd.transcoder.model.AudioFormat
import com.skyd.transcoder.model.TranscodeConfig

/**
 * Whether the source audio should be transcoded to the target format.
 * Returns false for MP3 source (already target) and UNKNOWN (cannot decide, skip to avoid lossy re-encode of unknown).
 */
fun shouldTranscode(sourceFormat: AudioFormat, config: TranscodeConfig): Boolean {
    return sourceFormat != AudioFormat.UNKNOWN && sourceFormat != config.targetFormat
}
```

- [ ] **步骤 8：运行测试验证通过**

```
./gradlew :transcoder:jvmTest
```
预期：PASS（全部测试通过）。

- [ ] **步骤 9：Commit**

```
git add transcoder/src/commonMain/kotlin/com/skyd/transcoder/model/AudioFormat.kt \
        transcoder/src/commonMain/kotlin/com/skyd/transcoder/model/TranscodeConfig.kt \
        transcoder/src/commonMain/kotlin/com/skyd/transcoder/Transcoder.kt \
        transcoder/src/commonTest/kotlin/com/skyd/transcoder/model/AudioFormatTest.kt \
        transcoder/src/commonTest/kotlin/com/skyd/transcoder/model/TranscodeConfigTest.kt \
        transcoder/src/commonTest/kotlin/com/skyd/transcoder/ShouldTranscodeTest.kt
git commit -m "[feature] Add transcoder audio format detection and transcode config"
```

### 任务 3.3：命名模板 + 文件名清理（TDD）

- [ ] **步骤 1：写失败测试 `NamingTemplateTest.kt`**

创建 `transcoder/src/commonTest/kotlin/com/skyd/transcoder/naming/NamingTemplateTest.kt`：

```kotlin
package com.skyd.transcoder.naming

import kotlin.test.Test
import kotlin.test.assertEquals

class NamingTemplateTest {
    private val meta = EpisodeMetadata(
        showName = "Daily Tech",
        episodeTitle = "EP1: Intro",
        episodeNumber = "1",
        seasonNumber = "2",
        pubDate = "2026-07-31",
    )

    @Test
    fun titleAndShowRendersDefault() {
        // ":" is illegal -> replaced with "_"
        assertEquals("EP1_ Intro - Daily Tech", NamingTemplate.TitleAndShow.render(meta))
    }

    @Test
    fun showNumberTitleRenders() {
        assertEquals("Daily Tech - E1 - EP1_ Intro", NamingTemplate.ShowNumberTitle.render(meta))
    }

    @Test
    fun dateAndTitleRenders() {
        assertEquals("2026-07-31 - EP1_ Intro", NamingTemplate.DateAndTitle.render(meta))
    }

    @Test
    fun missingEpisodeNumberReplacedWithEmpty() {
        val m = meta.copy(episodeNumber = null)
        assertEquals("Daily Tech - E - EP1_ Intro", NamingTemplate.ShowNumberTitle.render(m))
    }

    @Test
    fun missingSeasonNumberReplacedWithEmpty() {
        val m = meta.copy(seasonNumber = null)
        // seasonNumber not used in any preset, but ensure no crash
        assertEquals("EP1_ Intro - Daily Tech", NamingTemplate.TitleAndShow.render(m))
    }

    @Test
    fun missingPubDateReplacedWithEmpty() {
        val m = meta.copy(pubDate = null)
        assertEquals(" - EP1_ Intro", NamingTemplate.DateAndTitle.render(m))
    }

    @Test
    fun illegalCharsInShowNameReplaced() {
        val m = meta.copy(showName = "Show/Name?")
        assertEquals("EP1_ Intro - Show_Name", NamingTemplate.TitleAndShow.render(m))
    }

    @Test
    fun defaultTemplateIsTitleAndShow() {
        assertEquals(NamingTemplate.TitleAndShow, NamingTemplate.DEFAULT)
    }
}
```

- [ ] **步骤 2：写失败测试 `FileNameSanitizerTest.kt`**

创建 `transcoder/src/commonTest/kotlin/com/skyd/transcoder/naming/FileNameSanitizerTest.kt`：

```kotlin
package com.skyd.transcoder.naming

import kotlin.test.Test
import kotlin.test.assertEquals

class FileNameSanitizerTest {
    @Test
    fun replacesIllegalCharsWithUnderscore() {
        assertEquals("a_b_c", sanitizeFileNameSegment("a/b:c"))
    }

    @Test
    fun collapsesConsecutiveUnderscores() {
        assertEquals("a_b", sanitizeFileNameSegment("a///b"))
    }

    @Test
    fun trimsLeadingTrailingSpacesDotsUnderscores() {
        assertEquals("name", sanitizeFileNameSegment(" . name . "))
    }

    @Test
    fun truncatesToMaxLength() {
        val long = "a".repeat(300)
        val result = sanitizeFileNameSegment(long, maxLength = 200)
        assertEquals(200, result.length)
    }

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals("", sanitizeFileNameSegment(""))
    }

    @Test
    fun onlyIllegalCharsReturnsEmpty() {
        assertEquals("", sanitizeFileNameSegment("///"))
    }

    @Test
    fun sanitizeFileNameWithExtension() {
        assertEquals("name.mp3", sanitizeFileName("name", "mp3"))
    }

    @Test
    fun sanitizeFileNameWithDotExtension() {
        assertEquals("name.mp3", sanitizeFileName("name", ".mp3"))
    }

    @Test
    fun sanitizeFileNameWithoutExtension() {
        assertEquals("name", sanitizeFileName("name", null))
    }

    @Test
    fun sanitizeFileNameTruncatesBaseNotExtension() {
        val long = "a".repeat(300)
        val result = sanitizeFileName(long, "mp3", maxLength = 200)
        assertEquals("a".repeat(200) + ".mp3", result)
    }
}
```

- [ ] **步骤 3：运行测试验证失败**

```
./gradlew :transcoder:jvmTest
```
预期：FAIL（`EpisodeMetadata`、`NamingTemplate`、`sanitizeFileNameSegment` 未定义）。

- [ ] **步骤 4：实现 `EpisodeMetadata.kt`**

创建 `transcoder/src/commonMain/kotlin/com/skyd/transcoder/naming/EpisodeMetadata.kt`：

```kotlin
package com.skyd.transcoder.naming

/**
 * Metadata for a single podcast episode, used to render naming templates.
 * All fields are pre-formatted strings (caller is responsible for date formatting, etc.).
 */
data class EpisodeMetadata(
    val showName: String,
    val episodeTitle: String,
    val episodeNumber: String? = null,
    val seasonNumber: String? = null,
    val pubDate: String? = null,
)
```

- [ ] **步骤 5：实现 `FileNameSanitizer.kt`**

创建 `transcoder/src/commonMain/kotlin/com/skyd/transcoder/naming/FileNameSanitizer.kt`：

```kotlin
package com.skyd.transcoder.naming

/**
 * Sanitize a single path segment (file or folder name): replace illegal chars `\ / : * ? " < > |`
 * with `_`, collapse consecutive underscores, trim leading/trailing spaces/dots/underscores,
 * truncate to [maxLength].
 */
internal fun sanitizeFileNameSegment(input: String, maxLength: Int = 200): String {
    var s = input.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    s = s.replace(Regex("_+"), "_")
    s = s.trim().trim('.').trim('_').trim()
    if (s.length > maxLength) s = s.take(maxLength)
    return s
}

/**
 * Sanitize a file name with optional extension. Extension is preserved (not truncated).
 */
fun sanitizeFileName(name: String, extension: String? = null, maxLength: Int = 200): String {
    val base = sanitizeFileNameSegment(name, maxLength)
    val ext = extension?.trim()?.trimStart('.')
    return if (ext.isNullOrEmpty()) base else "$base.$ext"
}
```

- [ ] **步骤 6：实现 `NamingTemplate.kt`**

创建 `transcoder/src/commonMain/kotlin/com/skyd/transcoder/naming/NamingTemplate.kt`：

```kotlin
package com.skyd.transcoder.naming

enum class NamingTemplate(val template: String) {
    TitleAndShow("{episodeTitle} - {showName}"),
    ShowNumberTitle("{showName} - E{episodeNumber} - {episodeTitle}"),
    DateAndTitle("{pubDate} - {episodeTitle}");

    fun render(metadata: EpisodeMetadata): String {
        var result = template
        result = result.replace("{showName}", metadata.showName)
        result = result.replace("{episodeTitle}", metadata.episodeTitle)
        result = result.replace("{episodeNumber}", metadata.episodeNumber.orEmpty())
        result = result.replace("{seasonNumber}", metadata.seasonNumber.orEmpty())
        result = result.replace("{pubDate}", metadata.pubDate.orEmpty())
        return sanitizeFileNameSegment(result)
    }

    companion object {
        val DEFAULT: NamingTemplate = TitleAndShow
    }
}
```

- [ ] **步骤 7：运行测试验证通过**

```
./gradlew :transcoder:jvmTest
```
预期：PASS。

- [ ] **步骤 8：Commit**

```
git add transcoder/src/commonMain/kotlin/com/skyd/transcoder/naming/ \
        transcoder/src/commonTest/kotlin/com/skyd/transcoder/naming/
git commit -m "[feature] Add naming template rendering and file name sanitizer"
```

### 任务 3.4：TranscodeState + transcode expect/actual

- [ ] **步骤 1：实现 `TranscodeState.kt`**

创建 `transcoder/src/commonMain/kotlin/com/skyd/transcoder/model/TranscodeState.kt`：

```kotlin
package com.skyd.transcoder.model

data class TranscodeProgress(
    val processedSeconds: Double,
    val totalSeconds: Double?,
    val sizeBytes: Long,
)

sealed class TranscodeState {
    data object Idle : TranscodeState()
    data class Running(val progress: TranscodeProgress) : TranscodeState()
    data class Success(val outputSize: Long) : TranscodeState()
    data class Failed(val reason: String) : TranscodeState()
}
```

- [ ] **步骤 2：改 `Transcoder.kt` 加 expect class**

替换 `transcoder/src/commonMain/kotlin/com/skyd/transcoder/Transcoder.kt`：

```kotlin
package com.skyd.transcoder

import com.skyd.transcoder.model.AudioFormat
import com.skyd.transcoder.model.TranscodeConfig
import com.skyd.transcoder.model.TranscodeProgress
import kotlinx.coroutines.flow.Flow

/**
 * Whether the source audio should be transcoded to the target format.
 * Returns false for MP3 source (already target) and UNKNOWN (cannot decide, skip).
 */
fun shouldTranscode(sourceFormat: AudioFormat, config: TranscodeConfig): Boolean {
    return sourceFormat != AudioFormat.UNKNOWN && sourceFormat != config.targetFormat
}

/**
 * Transcodes [input] audio file to [output] as MP3 at the configured bitrate.
 * Emits [TranscodeProgress] while running. Completes when done (collect the flow to completion).
 * @param input absolute filesystem path to source audio
 * @param output absolute filesystem path to target .mp3 (will be overwritten)
 */
expect class Transcoder() {
    suspend fun transcode(
        input: String,
        output: String,
        config: TranscodeConfig = TranscodeConfig.DEFAULT,
    ): Flow<TranscodeProgress>
}
```

- [ ] **步骤 3：实现 androidMain actual**

创建 `transcoder/src/androidMain/kotlin/com/skyd/transcoder/Transcoder.android.kt`：

```kotlin
package com.skyd.transcoder

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.skyd.transcoder.model.TranscodeConfig
import com.skyd.transcoder.model.TranscodeProgress
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

actual class Transcoder actual constructor() {
    actual suspend fun transcode(
        input: String,
        output: String,
        config: TranscodeConfig,
    ): Flow<TranscodeProgress> = callbackFlow {
        val command = "-i \"$input\" -c:a libmp3lame -b:a ${config.bitrateKbps}k \"$output\""
        val session: FFmpegSession = FFmpegKit.executeAsync(
            command,
            { s ->
                if (ReturnCode.isSuccess(s.returnCode)) {
                    close()
                } else if (ReturnCode.isCancel(s.returnCode)) {
                    close()
                } else {
                    close()
                }
            },
            { /* log callback, no-op */ },
            { stats ->
                trySend(
                    TranscodeProgress(
                        processedSeconds = stats.time,
                        totalSeconds = null,
                        sizeBytes = stats.size,
                    )
                )
            },
        )
        awaitClose { FFmpegKit.cancel(session.sessionId) }
    }
}
```

- [ ] **步骤 4：实现 jvmMain actual**

创建 `transcoder/src/jvmMain/kotlin/com/skyd/transcoder/Transcoder.jvm.kt`：

```kotlin
package com.skyd.transcoder

import com.skyd.transcoder.model.TranscodeConfig
import com.skyd.transcoder.model.TranscodeProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

actual class Transcoder actual constructor() {
    actual suspend fun transcode(
        input: String,
        output: String,
        config: TranscodeConfig,
    ): Flow<TranscodeProgress> = flow {
        throw UnsupportedOperationException("Transcoder is only supported on Android")
    }
}
```

- [ ] **步骤 5：编译验证**

```
./gradlew :transcoder:compileDebugKotlinAndroid :transcoder:compileKotlinJvm
```
预期：成功。（若里程碑 1 选 arthenica 且包名冲突，此处会报错，回任务 1.3 步骤 4 处理。）

- [ ] **步骤 6：Commit**

```
git add transcoder/src/commonMain/kotlin/com/skyd/transcoder/model/TranscodeState.kt \
        transcoder/src/commonMain/kotlin/com/skyd/transcoder/Transcoder.kt \
        transcoder/src/androidMain/kotlin/com/skyd/transcoder/Transcoder.android.kt \
        transcoder/src/jvmMain/kotlin/com/skyd/transcoder/Transcoder.jvm.kt
git commit -m "[feature] Add FFmpegKit-based transcoder implementation (androidMain)"
```

### 任务 3.5：Koin 注册

- [ ] **步骤 1：实现 `TranscoderModule.kt`**

创建 `transcoder/src/commonMain/kotlin/com/skyd/transcoder/di/TranscoderModule.kt`：

```kotlin
package com.skyd.transcoder.di

import com.skyd.transcoder.Transcoder
import org.koin.dsl.module

val transcoderModule = module {
    single { Transcoder() }
}
```

- [ ] **步骤 2：在 shared 的 Koin 注册**

修改 `shared/src/commonMain/kotlin/com/skyd/podaura/di/Koin.kt`，在 `modules(...)` 加 `transcoderModule`（参照现有 `downloaderModule`、`downloaderDatabaseModule` 的注册方式）：

```kotlin
import com.skyd.transcoder.di.transcoderModule

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(
        ioModule, databaseModule, dataStoreModule, pagingModule,
        repositoryModule, viewModelModule,
        downloaderModule,
        downloaderDatabaseModule,
        transcoderModule,
    )
}
```

> 注意：shared 模块的 `build.gradle.kts` 需加 `implementation(projects.transcoder)`（若尚未有）。检查 `shared/build.gradle.kts` 的 `commonMain.dependencies`，若无 `implementation(projects.transcoder)` 则加上。

- [ ] **步骤 3：编译整个项目**

```
./gradlew assembleGithubDebug
```
预期：`BUILD SUCCESSFUL`，transcoder 模块编入 APK。

- [ ] **步骤 4：跑全部测试确认无回归**

```
./gradlew test
```
预期：基座测试 + transcoder 测试全通过。

- [ ] **步骤 5：Commit**

```
git add transcoder/src/commonMain/kotlin/com/skyd/transcoder/di/TranscoderModule.kt \
        shared/src/commonMain/kotlin/com/skyd/podaura/di/Koin.kt
git commit -m "[feature] Register transcoder module in Koin"
```

- [ ] **步骤 6：推送触发 CI**

```
git push origin master
```
预期：CI `Android Build` 成功，APK artifact 产出，测试通过。

---

## 自检

### 1. 规格覆盖度

对照规格 §9 里程碑：
- 里程碑 1（风险验证）：✅ 任务 1.1-1.4 覆盖（FFmpegKitNext 可用性 → 调研确认无 Maven 包，改用 arthenica:audio 或复用 jmir1；基座能本地构建 → 任务 1.1；跑测试 → 任务 1.2）。
- 里程碑 2（fork + CI 骨架）：✅ 任务 2.1-2.3 覆盖（能编译出原版 debug APK + 跑测试）。
- 里程碑 3（transcoder 模块）：✅ 任务 3.1-3.5 覆盖（FFmpegKitNext 集成 + 单元测试：决策/命名/配置/文件名清理）。
- 里程碑 4-7：⏳ 明确不在本计划，留计划二。理由已说明（触及核心下载逻辑 + 依赖里程碑 1 结果）。

对照规格 §3.1 transcoder 模块 commonMain 接口：
- `expect fun transcode(...)` → 实现为 `expect class Transcoder.transcode(...)`（用类而非顶层函数，便于 Koin 注入；语义等价）。✅
- `TranscodeConfig` → ✅ 任务 3.2
- `shouldTranscode` → ✅ 任务 3.2
- `TranscodeState` → ✅ 任务 3.4
- androidMain 用 FFmpegKitNext 执行 `-c:a libmp3lame -b:a {bitrate}k` → ✅ 任务 3.4（用 FFmpegKit API，与 FFmpegKitNext 一致）
- 进度通过 statistics 回调推送 Flow → ✅ 任务 3.4（callbackFlow + statisticsCallback）
- 只引入 audio 变体 → ✅ 任务 1.3 步骤 4 确认
- Koin 注册 → ✅ 任务 3.5

### 2. 占位符扫描

无"待定/TODO/后续实现"。任务 1.3 步骤 4 的"二选一"是有具体判断条件的分支决策，非占位符。任务 3.1 步骤 2 的 FFmpeg 依赖"二选一"同理，基于里程碑 1 结论。

### 3. 类型一致性

- `TranscodeConfig(targetFormat, bitrateKbps)`、`TranscodeConfig.DEFAULT`、`VALID_BITRATES` 在任务 3.2 定义，3.4 使用，一致。
- `AudioFormat.MP3/M4A/...` 在 3.2 定义，3.4 Transcoder.android 用 `config.bitrateKbps`，一致。
- `TranscodeProgress(processedSeconds, totalSeconds, sizeBytes)` 在 3.4 定义，androidMain actual 构造一致。
- `EpisodeMetadata(showName, episodeTitle, episodeNumber, seasonNumber, pubDate)` 在 3.3 定义，`NamingTemplate.render` 使用一致。
- `sanitizeFileNameSegment` internal，`sanitizeFileName` public，命名一致。
- `shouldTranscode` 在 Transcoder.kt（commonMain）定义，测试在 ShouldTranscodeTest，签名一致。
- `Transcoder` expect class 构造 `Transcoder()`，actual class `actual constructor()`，Koin `single { Transcoder() }`，一致。

---

## 执行交接

计划已完成并保存到 `docs/superpowers/plans/2026-07-31-podcast-downloader.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

**建议先执行里程碑 1（风险验证）**，因其是 gating：结果会确认 FFmpeg 库选型（影响任务 3.1/3.4），并确认基座可构建（影响 CI）。里程碑 1 的任务 1.1-1.3 可在本会话内联执行（跑 gradle 命令 + 查依赖）；任务 1.4 需设备/模拟器，可选或留 CI。

选哪种方式？
