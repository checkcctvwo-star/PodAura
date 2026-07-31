# 会话交接文档

- **日期**:2026-07-31
- **来源**:在 `F:\AI-porject\PILIPLUS-NEW` 启动的会话
- **目标**:在 `F:\AI-porject\pod` 继续开发"播客下载 + 手机端转 MP3"安卓应用

## 项目是什么
Fork [PodAura](https://github.com/SkyD666/PodAura)(GPL-3.0,Kotlin Multiplatform:Kotlin/Compose/Room3/KSP/Koin/Ktor/FileKit/MPV)改造为播客下载工具,侧重下载 + 手机端转 MP3。完整设计见:`docs/superpowers/specs/2026-07-31-podcast-downloader-design.md`

## 当前进度
1. ✅ 调研选定基座:PodAura(1246 stars,Kotlin/Compose/MVI,活跃,Material You,RSS+下载+播放)。排除 AntennaPod(臃肿)、Tsacdop(停滞)。
2. ✅ 摸清 PodAura 架构:KMP;`downloader` 模块有 `DownloadWorker.kt`(WorkManager,转码 hook 点);app 在 `platform/android/app`;已依赖 FileKit(SAF)、Koin、Ktor;`targetSdk=37`(已支持 Android 15/16)。
3. ✅ 设计已与用户确认(基础播放 / 智能转换 / RSS+OPML / 预设命名模板等),默认决策记录在规格 §10。
4. ✅ 规格文档已写并提交(commit `57702e6c`)。
5. ✅ PodAura 已全量克隆到 `F:\AI-porject\pod`(origin = 上游 SkyD666/PodAura,尚未 fork 到用户账户)。
6. ⏳ **用户称规格"需要修改"但未指定改什么** → 新会话首要任务:问用户规格要改哪里,或确认无需改。
7. ⏳ PAT:fine-grained PAT 无法 fork(403);用户在另一对话申请 classic PAT(`repo`+`workflow` scope)中。就绪后用 `gh auth status` 确认,再 fork + 配 GitHub Actions。

## 下一步(新会话执行)
1. 先问用户:规格要改什么?(若用户说没问题,继续)
2. 调用 **writing-plans** 技能产出实现计划(里程碑见规格 §9)。
3. 实施:先**风险验证**(FFmpegKitNext 是否有 Maven 预构建包 + 基座原版能否 `./gradlew assembleDebug` + `test`),再 fork+CI 骨架,再加 transcoder 模块等。
4. 遵循用户全局 CLAUDE.md 的技能框架:brainstorming(已完成)→ writing-plans → tdd → implement → code-review → verification-before-completion。

## 关键技术备忘
- ffmpeg-kit 于 2026-07-02 归档 → 用 **FFmpegKitNext**。Android MediaCodec 无 MP3 编码器,必须 FFmpeg。
- 转码走**临时文件**(SAF URI 不能直接喂 FFmpeg):下载到 `cacheDir` 临时文件 → 转码 → `ContentResolver` 写入 SAF 目标 `{root}/{showName}/{name}.mp3`。
- 基座预览工具链(SDK 37 / build-tools 37.0.0 / JDK 25)在 GitHub Actions 标准 runner 上需显式安装。
- GPL-3.0 衍生作品须开源,保留 LICENSE 与上游归属。
- 记忆已迁移到 pod 记忆目录;本项目记忆 `podcast-downloader-project.md` 有完整上下文。

## 环境备忘
- gh CLI 认证:checkcctvwo-star(当前 fine-grained PAT,无法 fork;等 classic PAT)。
- git 身份:pod 仓库已配本地 user(checkcctvwo-star / 226993231+checkcctvwo-star@users.noreply.github.com)。
- 严禁 `gh auth token`(暴露风险);只用 `gh auth status` 查登录状态。
