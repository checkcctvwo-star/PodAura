# 构建说明 (BUILD)

本文件描述播客下载 + 转 MP3 应用（fork of PodAura，Kotlin Multiplatform，GPL-3.0）的构建环境、CI 流程、本地构建命令与常见坑。

应用模块：`platform/android/app`（applicationId `com.skyd.anivu`，namespace `com.skyd.podaura`）。Android 是当前唯一支持下载+转码的 target；iOS/macOS 桌面端 target 存在但不参与 SAF 转码流程。

---

## 1. 构建环境（版本锁定）

版本来自 `gradle/libs.versions.toml` 与 `gradle/wrapper/gradle-wrapper.properties`：

| 组件 | 版本 | 来源 |
|---|---|---|
| JDK | 25 — Azul Zulu 发行版 | CI `actions/setup-java@v5`，`java-version: 25`，`distribution: zulu`（setup-java 解析为最新 Zulu 25.x 构建，例如 25.0.4） |
| Gradle | 9.6.1 | `gradle-wrapper.properties` `distributionUrl=...gradle-9.6.1-bin.zip` |
| Android Gradle Plugin (AGP) | 9.3.1 | `libs.versions.toml` `agp = "9.3.1"` |
| Kotlin | 2.4.10 | `kotlin = "2.4.10"` |
| KSP | 2.3.9 | `ksp = "2.3.9"` |
| Compose Multiplatform | 1.12.0-beta01 | `compose = "1.12.0-beta01"` |
| Compose Material3 | 1.12.0-alpha03 | `compose-material3 = "1.12.0-alpha03"` |
| compileSdk | 37（minorApiLevel 0） | `platform/android/app/build.gradle.kts` |
| targetSdk | 37 | 同上 |
| minSdk | 24 | 同上 |
| Build Tools | 37.0.0 | `buildToolsVersion = "37.0.0"` |
| NDK | 29.0.14206865 | `ndkVersion = "29.0.14206865"` |
| Java source/target compatibility | VERSION_25 | `compileOptions` |
| Core library desugaring | 启用 | `desugar_jdk_libs` 2.1.5 |

转码/播放相关依赖（`libs.versions.toml` `[libraries]`）：

| 依赖 | 坐标 | 版本 |
|---|---|---|
| FFmpegKit | `com.github.jmir1:ffmpeg-kit` | 1.18 |
| MPV lib | `com.github.aniyomiorg:aniyomi-mpv-lib` | 1.18.n |
| mediamp | `org.openani.mediamp:mediamp-mpv` | 0.2.1 |
| FileKit | `io.github.vinceglb:filekit-core` / `filekit-dialogs-compose` | 0.14.2 |
| Room3 | `androidx.room3:room3-runtime` / `-paging` / `-compiler` | 3.0.0 |
| Koin | `io.insert-koin:koin-*` | 4.2.2 |
| Kermit | `co.touchlab:kermit` | 2.1.0 |
| kotlinx-coroutines | `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0 |
| kotlinx-datetime | `org.jetbrains.kotlinx:kotlinx-datetime` | 0.8.0 |

仓库（`settings.gradle.kts`，`dependencyResolutionManagement` 采用 `FAIL_ON_PROJECT_REPOS`）：`mavenLocal()`、`google()`、`mavenCentral()`、`jitpack.io`（`https://jitpack.io`）；`pluginManagement` 额外含 `gradlePluginPortal()`。启用的特性预览：`TYPESAFE_PROJECT_ACCESSORS`、`STABLE_CONFIGURATION_CACHE`。

模块（`settings.gradle.kts` `include(...)`）：`:shared`、`:fundation`、`:htmlrender`、`:downloader`、`:transcoder`、`:ksp:processor`、`:ksp:annotation`、`:platform:android:app`、`:platform:android:benchmark`、`:compottie:core`、`:compottie:main`。

---

## 2. 本地构建前提

- **JDK 25**（Azul Zulu 推荐，与 CI 一致）。
- **Android SDK 37** + **Build-Tools 37.0.0** + **NDK 29.0.14206865**。
- 足够磁盘空间（FFmpegKit / MPV native 库较大；按 ABI 拆包构建 universal APK）。

> **重要说明：维护者的本地机器没有安装 Android SDK，因此所有构建均在 CI 上完成。** 本地若未配齐上述 SDK，不要尝试 `assembleGithubDebug`，直接依赖 CI 产物即可。

---

## 3. CI 工作流

文件：`.github/workflows/android.yml`（workflow name: `Android Build`）。

**触发：**
- `push` 到分支 `master` 或 `feature/podcast-downloader`；
- `paths-ignore`: `**.md`、`docs/**`（文档变更不触发构建）；
- `workflow_dispatch`（手动触发）。

**运行环境：** `ubuntu-latest`。

**步骤：**

| 步骤 | Action / 命令 |
|---|---|
| Checkout | `actions/checkout@v6` |
| Setup JDK 25 | `actions/setup-java@v5`（`distribution: zulu`，`java-version: 25`） |
| Setup Gradle | `gradle/actions/setup-gradle@v6` |
| Run Tests | `./gradlew test` |
| Assemble Debug APK | `./gradlew :platform:android:app:assembleGithubDebug` |
| Upload APKs | `actions/upload-artifact@v7`，artifact name `debug-apk`，path `platform/android/app/build/outputs/apk/GitHub/debug/*.apk`，`compression-level: 9` |
| Upload Test Results | `actions/upload-artifact@v7`（`if: always()`），artifact name `test-results`，path `**/build/test-results/**/*.xml`、`**/build/reports/tests/**` |

> CI（ubuntu）只能保证：编译通过 + 单元测试通过 + Debug APK 产物生成。**运行时转码成功必须由用户在真机验证**（见 `docs/DEVICE_TESTING.md`）——CI 无设备，无法跑仪器测试的运行时路径。

---

## 4. 构建命令

```bash
# 单元测试（所有模块 commonTest/jvmTest）
./gradlew test

# Debug APK（主交付物，debug 签名，可装机运行）
./gradlew :platform:android:app:assembleGithubDebug

# （可选）编译验证仪器测试包，不在 CI 默认跑
./gradlew :platform:android:app:assembleAndroidTest
```

`assembleGithubDebug` 中的 `Github` 对应 product flavor `GitHub`（`flavorDimensions += "version"`，`create("GitHub")`）。Debug buildType 带 `applicationIdSuffix = ".debug"`。

### 产物路径与命名

- 目录：`platform/android/app/build/outputs/apk/GitHub/debug/`
- 文件名模式（`androidComponents.onVariants` 重命名）：`PodAura_${versionName}_${abi}_${buildType}_${flavorName}.apk`
  - 例如：`PodAura_0.1.0_arm64-v8a_debug_GitHub.apk`
- ABI 拆包（`splits.abi`，`isUniversalApk = true`）：`arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 各一个 APK，外加一个 universal APK。
- 通用安装建议：选 `universal` APK，或与真机 ABI 匹配的拆包 APK。

---

## 5. Release 签名

当前**主交付物是 Debug 签名 APK**（`assembleGithubDebug` 产物），可直接安装运行。

Release buildType（`platform/android/app/build.gradle.kts`）：
- `optimization.enable = true`；
- 仅当根目录存在 `signing.properties` 文件时，才创建 `release` signingConfig 并赋给 release buildType。
- `signing.properties` 需提供：`KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
- **当前未提供该 keystore**，因此 release 变体无签名配置，不能直接产出可安装的 release APK。

可选增强（见 `docs/superpowers/plans/2026-07-31-podcast-downloader-m4-m7-release.md` 计划三 任务 R2.2）：在 CI 用 `keytool -genkey` 生成临时 keystore，base64 存为环境变量，加 `assembleGithubRelease` 步骤产出 signed release APK。
- 注意：临时 keystore 非持久（每次 CI 签名不同，无法升级覆盖安装），仅作"能产出 signed release"演示。
- **正式 release 签名需用户后续配置 GitHub Secret（持久 keystore）。**

GitHub Release 发布（计划三 任务 R2.3）：打 tag `v*` 触发 release job，用 `gh release create` 上传 APK asset（tag 示例：`v0.1.0-podcast-downloader`）。

---

## 6. 已知问题与坑（Gotchas）

1. **不要添加阿里云 maven 镜像。** `settings.gradle.kts` 中 `https://maven.aliyun.com/repository/public` 已被注释掉——加上它会破坏 KSP 插件解析（KSP 插件从 `google()` / `gradlePluginPortal()` 解析，镜像同步不全）。保持现有仓库列表即可。

2. **FileKit `PlatformFile.div` 是 `operator` 函数，不是 infix。** 调用必须用点号写法：`root.div(showName)`，不要写 `root div showName`。参见 `shared/src/androidMain/.../SafWriter.android.kt`。

3. **Room3 使用 `androidx.sqlite` 的 `SQLiteConnection` 新迁移 API，不是 `SupportSQLiteDatabase`。** 迁移写法为 `Migration(start, end) { connection -> connection.execSQL("ALTER TABLE ...") }`。参见 `downloader/src/commonMain/.../db/Migration1To2.kt`（v1→v2 加 `outputUri` / `transcodeStatus` / `finalSize` 三列）。

4. **`expect/actual` classes 需要 `-Xexpect-actual-classes` 编译参数。** `shared/build.gradle.kts` 已在 `compilerOptions.freeCompilerArgs` 中添加。编译器会输出 beta 警告，**无害**，可忽略。

5. **`-Xskip-prerelease-check` 已在 app 模块启用**（`platform/android/app/build.gradle.kts`），因为 Compose 1.12.0-beta01 / Kotlin 2.4.10 / AGP 9.3.1 组合涉及预发布依赖。不要移除该参数。

6. **`signing.properties` 不存在时 `readProperties()` 返回 `null`**，release signingConfig 不创建——此时跑 release 变体会失败或产出未签名包。本地无该文件属正常，主交付用 debug APK。

7. **mediamp 0.2.1 误把 Compose 的 JUnit UI 测试栈作为 runtime 依赖发布。** `shared/build.gradle.kts` 已对 `jvm*` configuration `exclude(group = "org.jetbrains.compose.ui", module = "ui-test-junit4")`，避免桌面端 ProGuard 的 ASM 引用未解析。不要移除该排除规则。

8. **`paths-ignore: docs/**` 与 `**.md`**：本文档及 `docs/` 下其他文件的修改不会触发 CI。改代码后才会构建。

---

## 7. 快速验证（CI 产物）

1. 推送代码到 `master` 或 `feature/podcast-downloader`（非 `.md` / 非 `docs/**` 的改动）。
2. CI 跑 `test` + `assembleGithubDebug`。
3. 从 Actions 运行页下载 `debug-apk` artifact，解压得到 `PodAura_*_debug_GitHub.apk`。
4. 真机安装并按 `docs/DEVICE_TESTING.md` 验证运行时转码。
