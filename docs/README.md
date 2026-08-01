# PodAura 播客下载 + 转 MP3 (fork)

基于 [PodAura](https://github.com/SkyD666/PodAura) (GPL-3.0) 的 Kotlin Multiplatform 安卓应用，在基座的 RSS 订阅与播放能力之上，新增**播客下载 + 自动转码 MP3 + 写入用户选择的 SAF 目录**功能。

[![Android Build](https://github.com/checkcctvwo-star/PodAura/actions/workflows/android.yml/badge.svg)](https://github.com/checkcctvwo-star/PodAura/actions/workflows/android.yml)

## 上游与许可

本仓库是 [SkyD666/PodAura](https://github.com/SkyD666/PodAura) 的 fork（fork 仓库：[checkcctvwo-star/PodAura](https://github.com/checkcctvwo-star/PodAura)），遵循 **GPL-3.0** 协议开源。详见 [LICENSE](../LICENSE)。

## 功能特性

- 订阅播客 RSS，下载单集
- 自动转码为 MP3（FFmpegKit + libmp3lame）
- 按节目名 / 集名 / 日期命名模板组织文件
- 写入用户选择的 SAF 目录（`{root}/{showName}/{name}.mp3`）
- 可配置码率：64 / 96 / 128 / 192 kbps
- 可选保留原始下载文件
- 沿用基座 MPV 播放器

## 技术栈

- Kotlin 2.4.10 / Kotlin Multiplatform (KMP)
- Compose Multiplatform
- Room3
- Koin
- FileKit 0.14.2（SAF 目录选择与写入）
- FFmpegKit + libmp3lame（音频转码）
- MPV（播放器）
- JDK 25 / AGP 9.3.1 / compileSdk 37

## 模块结构

| 模块 | 职责 |
|---|---|
| `fundation` | 基础工具与常量 |
| `shared` | 业务逻辑、下载流程 hook、设置 UI |
| `downloader` | 通用下载能力（WorkManager + 通知） |
| `transcoder` | 转码决策 + 命名模板 + FFmpegKit 封装（本 fork 新增） |
| `platform/android` | Android app 入口 |

详见 [架构文档](ARCHITECTURE.md)。

## 文档索引

- [架构](ARCHITECTURE.md) - 模块 / 依赖图 / hook 点
- [构建](BUILD.md) - 环境 / CI / 出 APK
- [转码流程](TRANSCODE_FLOW.md) - 下载到 MP3 时序 / 错误处理
- [使用说明](USAGE.md) - 配置 / 下载 / 故障排查
- [设备验证](DEVICE_TESTING.md) - 真机测试清单

## 构建速查

```bash
./gradlew :platform:android:app:assembleGithubDebug
```

产物路径：`platform/android/app/build/outputs/apk/GitHub/debug/*.apk`。详见 [构建文档](BUILD.md)。

## CI

GitHub Actions 工作流 [.github/workflows/android.yml](../.github/workflows/android.yml)：

- **触发**：push 到 `master` / `feature/podcast-downloader`（忽略 `**.md` 与 `docs/**` 路径变更），或手动 `workflow_dispatch`
- **步骤**：Setup JDK 25 (Zulu) → `./gradlew test` → `./gradlew :platform:android:app:assembleGithubDebug` → 上传 `debug-apk` artifact + 测试结果

## 当前状态

转码功能已实现并通过 CI 编译；运行时需真机验证（见 [设备验证](DEVICE_TESTING.md)）。

## 诚实声明

本 fork 由 AI（Claude Code）辅助开发。CI 仅保证编译通过、单元测试通过及 APK 产物生成；转码运行时验证（FFmpegKit native libmp3lame 解码编码、SAF `content://` 写入、下载完成 hook 真实触发）需人工在真机上完成。
