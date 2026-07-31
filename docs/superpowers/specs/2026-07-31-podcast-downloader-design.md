# 播客下载 + 手机端转 MP3 工具 设计规格

- **日期**:2026-07-31
- **基座**:Fork [PodAura](https://github.com/SkyD666/PodAura)(Kotlin Multiplatform,GPL-3.0)
- **目标平台**:Android(Android 15/16 已由基座满足:`targetSdk=37`,`minSdk=24`)
- **状态**:设计已批准,待实现

## 1. 目标与非目标

### 目标
1. **侧重下载**:订阅播客 RSS -> 下载单集到用户指定位置。
2. **手机端转 MP3**:下载后在设备本地把非 MP3 源转码为 MP3(智能转换,见 §3)。
3. **码率可选**:转码时选码率,展示最终文件大小。
4. **自定义命名**:用户从预设模板选择节目文件名;默认 = 单集标题 + 节目台名。
5. **按节目台分文件夹**:每个播客节目台一个目录。
6. **自定义下载位置**:用户通过 SAF 选根目录。
7. **基础播放**:播放/暂停/进度/倍速(沿用基座,不增强)。
8. **交钥匙交付**:fork 仓库 + GitHub Actions 云端编译 APK + 跑测试,用户直接下载可装 APK。

### 非目标(YAGNI,明确排除)
- iOS / macOS / 桌面目标(基座是 KMP,我们只维护 Android,iOS/macOS/jvm 源集保留但不碰)
- 内置播客目录搜索(仅 RSS URL + OPML 导入)
- 高级播放器功能(章节/睡眠定时/均衡器/播放位置云同步)
- AAC/M4A 等非 MP3 输出格式(本期只做 MP3)
- 视频播客转码

## 2. 约束

- **GPL-3.0**:基座许可证,衍生作品必须 GPL-3.0 开源。保留 `LICENSE` 与上游归属。
- **Android 15/16**:基座 `compileSdk=37`、`targetSdk=37`、`minSdk=24`,已满足。无需降级或额外适配。
- **工具链**:JDK 25、Kotlin、Compose Multiplatform、Room3、KSP、Koin、Ktor、FileKit、MPV 播放器(均为基座既有)。

## 3. 核心功能详述

### 3.1 智能转码(transcoder 模块,新增)

**新模块 `transcoder`**(KMP,职责单一:音频转码):
- `commonMain`:
  - `expect fun transcode(input: String, output: String, bitrateKbps: Int): Flow<TranscodeProgress>`(input/output 为文件系统绝对路径,见 §3.2 临时文件说明;类型对齐基座既有路径抽象,实现时确认)
  - 配置模型:`TranscodeConfig(targetFormat=MP3, bitrateKbps)`
  - 决策函数:`fun shouldTranscode(sourceFormat: AudioFormat, config): Boolean`(MP3 源返回 false)
  - 状态模型:`TranscodeState { Idle, Running(progress), Success(outputSize), Failed(reason) }`
- `androidMain`:
  - `actual fun transcode(...)` 用 **FFmpegKitNext** 执行 `-i input -c:a libmp3lame -b:a {bitrate}k output.mp3`
  - 进度通过 FFmpegKit 的 statistics 回调推送到 `Flow`
  - **只引入 FFmpegKitNext 的 `audio` 变体**(不含视频编码器,减小体积)
- 通过 **Koin** 注册(`transcoder` 模块 + `downloader` 模块均用 Koin)

**目标格式**:仅 MP3。
**码率预设**:64 / 96 / 128 / 192 kbps,默认 128。
**源格式探测**:扩展名优先(`.mp3/.m4a/.aac/.opus/.ogg/.wav`),回退到 MIME/HTTP Content-Type。

### 3.2 转码流程(Hook 进 downloader 模块)

**Hook 点**:`downloader/src/androidMain/.../download/DownloadWorker.kt`(WorkManager)。

**SAF 与 FFmpeg 兼容**:用户选的根目录是 SAF URI(`content://`),FFmpegKitNext 只能读文件系统路径。因此转码走**临时文件**:
- 下载原始音频到 app 缓存目录(`cacheDir`)的临时文件(文件系统路径)
- 若需转码:FFmpegKitNext 读临时输入 -> 写临时输出 `.mp3`
- 把最终文件(原始 MP3 或转码后 MP3)通过 `ContentResolver` 写入 SAF 目标 `{root}/{showName}/{name}.mp3`
- 删除临时文件

下载成功后的流程:
1. 下载原始音频到临时文件
2. 探测源格式
3. 已是 MP3 -> **跳过转码,直接写原始文件到 SAF 目标**(无损质、省电)
4. 非 MP3 -> 调 `transcoder` 转 MP3 @ 用户码率 -> 写输出到 SAF 目标
5. 转码成功 -> **默认删除原临时文件**(可在设置中改为保留原始副本到 SAF)-> 更新 `DownloadDatabase` 记录:输出路径(SAF URI)、最终大小、状态=完成
6. 转码失败 -> 通知用户失败原因,不阻塞其他下载

**全局开关**:设置中"自动转 MP3"开/关 + 选码率。设一次,每次下载自动执行。

### 3.3 存储与命名

**下载根目录**:用户通过 **SAF**(用基座已依赖的 FileKit)选择文件夹,持久化 URI 权限(`takePersistableUriPermission`)。

**目录结构**(按节目台分文件夹):
```
{用户选的根目录}/{播客节目台名}/{文件名}.mp3
```

**命名模板**(预设几个,默认 = 单集标题 + 节目台名):
- 可用变量:`{showName}` 节目台名 / `{episodeTitle}` 单集标题 / `{episodeNumber}` 编号 / `{pubDate}` 发布日期(YYYY-MM-DD) / `{seasonNumber}` 季
- 预设:
  1. `{episodeTitle} - {showName}`(默认)
  2. `{showName} - E{episodeNumber} - {episodeTitle}`
  3. `{pubDate} - {episodeTitle}`
- 文件名/文件夹名清理:对**文件名和 `{showName}` 文件夹名**都替换非法字符 `\ / : * ? " < > |` 为 `_`;去除首尾空格与点;单段限长 200 字符;重名自动追加 `(2)`、`(3)`。

### 3.4 文件大小展示

- 下载记录 UI + 完成通知显示**最终 MP3 文件大小**。
- 下载列表项显示:状态(下载中 / 转码中 / 完成)、原始大小 -> 最终大小。

### 3.5 播放

沿用 PodAura 的 MPV 播放器,只保留播放/暂停/进度/倍速。不加新功能。

## 4. 数据流

```
RSS URL 添加 / OPML 导入
  -> PodAura 既有 feed 解析(订阅、节目列表)
  -> 用户点单集下载
  -> DownloadWorker 下载原始音频
  -> [智能转码] 非 MP3 -> MP3 @ 码率
  -> 写入 {root}/{showName}/{name}.mp3
  -> 更新 DownloadDatabase(路径 / 大小 / 状态)
  -> 通知(含最终大小)

设置项(存于 DataStore,沿用基座偏好机制):
  - 下载根目录(SAF URI)
  - 命名模板选择
  - 自动转 MP3 开关 + 码率
  - 转码后是否保留原始文件(默认否)
```

## 5. 错误处理

| 场景 | 处理 |
|---|---|
| SAF URI 失效(用户撤销) | 提示重新选择目录;下载暂停直到重选 |
| 转码失败 | 保留原文件,通知失败原因,不阻塞其他下载 |
| 存储空间不足 | 下载/转码前检查可用空间,不足则提示 |
| 网络中断 | WorkManager 自动重试(基座既有机制) |
| 文件名冲突 | 自动追加序号 `(2)`、`(3)` |
| 转码中途被取消 | 清理半成品输出文件,保留原文件 |

## 6. CI/CD 与交付

- **仓库**:fork PodAura 到 `checkcctvwo-star`(具体仓库名待定,建议 `PiliCast`)。
- **GitHub Actions 工作流**`.github/workflows/android.yml`:
  - 触发:push 到主分支 + 手动
  - 步骤:`actions/setup-java`(JDK 25 Temurin)-> `gradle/actions/setup-gradle` -> `./gradlew test` -> `./gradlew assembleRelease`
  - 产物:APK 上传为 workflow artifact + (可选)GitHub Release
  - 签名:release APK 用仓库 secrets 里的 keystore 签名(用户后续提供,或先出 debug APK)
- **本地测试**:我在本地 `./gradlew test` 跑通 commonMain/JVM 单元测试。

## 7. 测试策略

### 单元测试(commonMain/JVM,`./gradlew :transcoder:test` 等)
- 命名模板渲染:变量替换、缺失变量处理、非法字符清理、长度截断、重名序号
- 智能转换决策:`shouldTranscode` 对各源格式的返回
- 码率/格式配置模型
- 文件名清理边界用例

### Android 仪器测试(`connectedAndroidTest`,CI 可选)
- 对小样本音频实际转码(m4a -> mp3),验证输出可播放 + 大小合理
- SAF 目录持久化与写入

### 端到端
- 下载 -> 转码 -> 文件落位到 `{root}/{show}/{name}.mp3` -> DB 记录正确 -> 通知显示大小

## 8. 风险与缓解

| 风险 | 影响 | 缓解(实现第一批验证) |
|---|---|---|
| FFmpegKitNext 为 source-only 发行 | 高:若无预构建 Maven 包,需自建 NDK 编译,大幅增加复杂度 | **第一个里程碑**:确认是否有可用 Gradle/Maven 制品;若无,评估纯 LAME JNI 编码器方案(体积更小,仅 MP3) |
| 基座用预览版 SDK 37 / build-tools 37.0.0 / JDK 25 | 中:GitHub Actions 标准 runner 不自带,CI 可能失败 | CI 中显式安装 JDK 25 + 接受预览 SDK 许可;必要时临时锁定到稳定 compileSdk 36 验证 |
| APK 体积增大(FFmpegKitNext + MPV) | 中:与"不臃肿"诉求冲突 | 评估包体;只打包需要的 ABI(arm64-v8a 为主);用 FFmpegKitNext 的 `audio` 变体(不含视频编码器) |
| 基座下载/命名的现有逻辑未知 | 低:可能在 `DownloadManager`/配置类 | 实现时先读现有代码,在原有基础上改而非重写 |
| fine-grained PAT 无法 fork/创建仓库 | 高:阻塞云端 CI | 用户需 fork(网页一键)或提供 classic PAT(`repo`+`workflow` scope);本地开发不阻塞 |

## 9. 实现里程碑(概览,详细计划由 writing-plans 产出)

1. **风险验证**:FFmpegKitNext 可用性 + 基座原版能本地构建/跑测试
2. **fork 仓库 + CI 骨架**:能编译出原版 debug APK
3. **transcoder 模块**:FFmpegKitNext 集成 + 单元测试(决策/命名)
4. **下载流程改造**:DownloadWorker hook + 智能转码 + 按节目台分文件夹 + 命名模板
5. **SAF 下载位置 + 设置 UI**
6. **文件大小展示 + 通知**
7. **端到端验证 + release APK**

## 10. 默认决策记录(用户已确认)

- 转码后默认删原文件(可设为保留)
- 码率 64/96/128/192,默认 128
- 目标格式仅 MP3
- 命名默认 `{episodeTitle} - {showName}`
- 只维护 Android 目标
- 接受 GPL-3.0
- 交付物为可装 APK(云端编译 + 测试),用户不写代码
