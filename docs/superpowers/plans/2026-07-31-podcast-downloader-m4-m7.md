# 播客下载 + 转MP3 实现计划（二）：下载流程改造 + SAF + UI + 端到端

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把已就绪的 transcoder 模块 hook 进下载流程，实现：下载原始音频到临时文件 -> 智能转 MP3 -> 写入用户选的 SAF 目录 `{root}/{showName}/{name}.mp3` -> 更新记录 + 通知展示最终大小。用户可配下载根目录、命名模板、码率、自动转码开关。

**架构：** 不改 downloader 模块（保持通用），在 shared 层介入：`DownloadStarter` 渲染文件名 + 指向 cacheDir 临时目录；`DownloadManager.listenDownloadEvent` 的 `Event.Success` 处执行转码 + 写 SAF + 更新 DB。设置项用基座 DataStore + `@Preference` 模式，落到已有空壳 `TransmissionScreen`。

**技术栈：** Kotlin 2.4.10 / KMP / Room3 / Koin / FileKit 0.14.2（SAF）/ transcoder 模块（计划一已建）/ kotlin.test。

---

## 范围说明

本计划是系列计划的**第二个**，覆盖设计规格 §9 的：

- **里程碑 4：下载流程改造**（hook 转码 + 临时文件 + 写 SAF + 命名模板）
- **里程碑 5：SAF 下载位置 + 设置 UI**（5 个偏好 + TransmissionScreen）
- **里程碑 6：文件大小展示 + 通知**
- **里程碑 7：端到端验证 + release APK**

计划一（里程碑 1-3）已完成：fork + CI 骨架 + transcoder 模块（FFmpegKit 集成 + 37 单测）。本计划把 transcoder 接入下载流程。

> **执行约束（重要）：** 本地无 Android SDK，所有 gradle 构建/测试靠 CI（每轮约 8 分钟）。子代理写代码 + commit + push，CI 验证。当前分支 `feature/podcast-downloader`，BASE = `0c6ebd0a`。

---

## 架构决策（基于代码调研）

| # | 决策 | 依据 |
|---|---|---|
| 1 | **hook 点 = shared 层 DownloadStarter + DownloadManager.listenDownloadEvent**，不在 downloader 模块 | DownloadStarter.download 已加载 article（含 feed.title/article.title），能渲染命名模板；listenDownloadEvent Event.Success 是下载完成点。downloader 模块保持通用（下载到 path/fileName），shared 层负责转码+SAF。 |
| 2 | **下载到 cacheDir 临时文件，转码后写 SAF** | FFmpegKitNext 只读文件系统路径；SAF content:// 不能直接喂 FFmpeg。下载到 cacheDir（文件系统路径）-> 转码 -> FileKit sink 写 SAF 目标。FileKit 0.14.2 支持 SAF（div 导航 + sink 写入 + bookmarkData 持久化），atomicMove 对 SAF 非原子故走 copy。 |
| 3 | **DownloadEntity 加字段**（outputUri、transcodeStatus、finalSize）+ Room 迁移 v1->v2 | 当前 DownloadEntity 只有 path/fileName/totalBytes 等。转码后要记 SAF 输出 URI + 最终大小 + 转码状态。downloader 模块 DownloadDatabase version 1 -> 2，加 Migration1To2。 |
| 4 | **5 个偏好用 commonMain `@Preference object`**，落到 TransmissionScreen 空壳 | 基座偏好模式：DataStore + @Preference + KSP 自动注册 + CompositionLocal 下发。downloader 不依赖 shared（循环依赖），偏好在 shared 读取后通过参数传给下载流程。 |
| 5 | **{seasonNumber} 变量需新增字段** | 基座 RssMediaBean 无 season、RSS 解析无 itunes:season。加 season 字段 + RSS 解析 + Migration（shared AppDatabase v27->v28）。命名模板 EpisodeMetadata 已含 seasonNumber（计划一）。 |
| 6 | **SAF 根目录偏好替代 MediaLibLocation 用于下载** | 下载转码写 SAF 根目录（新偏好 DownloadRootDirPreference）。MediaLibLocation 保留给基座媒体库/播放（不破坏播放功能）。 |

---

## 文件结构

**新建：**
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/preference/download/DownloadRootDirPreference.kt` - SAF 根目录 URI（String）
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/preference/download/DownloadNamingTemplatePreference.kt` - 命名模板选择（String 预设）
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/preference/download/AutoTranscodeMp3Preference.kt` - 自动转 MP3 开关（Boolean）
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/preference/download/TranscodeBitratePreference.kt` - 码率（Int 预设）
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/preference/download/KeepOriginalAfterTranscodePreference.kt` - 保留原文件（Boolean）
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/TranscodeHook.kt` - 下载完成后转码+写SAF+更新DB 的逻辑
- `shared/src/androidMain/kotlin/com/skyd/podaura/model/repository/download/SafWriter.android.kt` - FileKit SAF 写入（div + sink）
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/EpisodeMetadataMapper.kt` - ArticleWithFeed -> EpisodeMetadata

**修改：**
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/DownloadStarter.kt` - 渲染文件名 + 指向 cacheDir 临时目录 + 传 transcode 配置
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/DownloadManager.kt` - listenDownloadEvent Event.Success 调 TranscodeHook
- `shared/src/commonMain/kotlin/com/skyd/podaura/ui/screen/settings/transmission/TransmissionScreen.kt` - 加 5 个设置项 UI
- `shared/src/commonMain/kotlin/com/skyd/podaura/ui/screen/settings/SettingsList.kt` - 取消注释 TransmissionRoute 项
- `shared/src/commonMain/kotlin/com/skyd/podaura/ui/screen/settings/SettingsDetailPaneNavDisplay.kt` - 补 TransmissionRoute 路由
- `downloader/src/commonMain/kotlin/com/skyd/downloader/db/DownloadEntity.kt` - 加 outputUri/transcodeStatus/finalSize 字段
- `downloader/src/commonMain/kotlin/com/skyd/downloader/db/DownloadDatabase.kt` - version 2 + Migration1To2
- `downloader/src/commonMain/kotlin/com/skyd/downloader/db/DownloadDao.kt` - 若需新查询
- `downloader/src/androidMain/kotlin/com/skyd/downloader/notification/DownloadNotificationManager.kt` - 通知展示最终大小
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/bean/article/RssMediaBean.kt` - 加 season 字段
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/feed/rssparser/rss/Item.kt` - 加 itunesSeason
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/feed/convert/RssExt.kt` - toRssMediaBean 加 season
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/db/migration/Migration27To28.kt` - 新建，加 season 列
- `shared/src/commonMain/kotlin/com/skyd/podaura/model/db/AppDatabase.kt` - version 28
- `shared/src/commonMain/resources/MR/base/strings.xml` + 各语言 - 设置项文案
- `.github/workflows/android.yml` - 里程碑7 加 release APK（若有签名密钥）

---

## 里程碑 4：下载流程改造（hook 转码）

> 核心里程碑。先做 4.1（DownloadEntity 加字段 + 迁移），再 4.2（TranscodeHook + SafWriter），再 4.3（DownloadStarter + DownloadManager 接入）。

### 任务 4.1：DownloadEntity 加字段 + Room 迁移

**文件：**
- 修改：`downloader/src/commonMain/kotlin/com/skyd/downloader/db/DownloadEntity.kt`
- 修改：`downloader/src/commonMain/kotlin/com/skyd/downloader/db/DownloadDatabase.kt`
- 新建：`downloader/src/commonMain/kotlin/com/skyd/downloader/db/Migration1To2.kt`

- [ ] **步骤 1：DownloadEntity 加 3 字段**

```kotlin
@Entity(tableName = DownloadEntity.TABLE_NAME)
data class DownloadEntity(
    @PrimaryKey var id: Int = 0,
    var url: String = "",
    var path: String = "",
    var fileName: String = "",
    var timeQueued: Long = 0,
    var status: String = Status.Init.toString(),
    var totalBytes: Long = 0,
    var downloadedBytes: Long = 0,
    var speedInBytePerMs: Float = 0f,
    var eTag: String = "",
    var workerUuid: String = "",
    var createTime: Long = 0,
    var userAction: String = UserAction.Init.toString(),
    var failureReason: String = "",
    // 新增（里程碑4）
    var outputUri: String = "",          // 转码后最终文件的 SAF URI
    var transcodeStatus: String = "",    // ""=未转码/跳过, "running", "success", "failed"
    var finalSize: Long = 0,             // 最终输出文件大小（字节）
) {
    companion object { const val TABLE_NAME = "Download" }
}
```

- [ ] **步骤 2：新建 Migration1To2**

```kotlin
package com.skyd.downloader.db

import androidx.room3.migration.Migration
import androidx.sqlite3.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Download ADD COLUMN outputUri TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Download ADD COLUMN transcodeStatus TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Download ADD COLUMN finalSize INTEGER NOT NULL DEFAULT 0")
    }
}
```

- [ ] **步骤 3：DownloadDatabase version 2 + 注册迁移**

```kotlin
@Database(entities = [DownloadEntity::class], version = 2)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    companion object
}

fun DownloadDatabase.Companion.instance(
    builder: RoomDatabase.Builder<DownloadDatabase>
): DownloadDatabase {
    val migrations = arrayOf<Migration>(MIGRATION_1_2)
    return builder.addMigrations(*migrations).build()
}
```

- [ ] **步骤 4：Commit**

```
git add downloader/src/commonMain/kotlin/com/skyd/downloader/db/
git commit -m "[feature] Add transcode fields to DownloadEntity + migration v1->v2"
```

### 任务 4.2：SafWriter（FileKit SAF 写入）

**文件：**
- 新建：`shared/src/androidMain/kotlin/com/skyd/podaura/model/repository/download/SafWriter.android.kt`

> 执行前确认：读 `io.github.vinceglb.filekit` 的 `PlatformFile`、`div`、`sink`、`createDirectories`、`exists`、`source` 在 Android UriWrapper 上的实际签名（调研确认可用，但签名以 FileKit 0.14.2 实际为准）。

- [ ] **步骤 1：实现 SafWriter**

```kotlin
package com.skyd.podaura.model.repository.download

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.sink
import io.github.vinceglb.filekit.source
import kotlinx.io.copyTo

/**
 * 把临时文件 [tempFile] 写入 SAF 目标 [rootTreeUri]/{showName}/{fileName}。
 * 自动创建子目录，重名追加 (2)/(3)。
 * 返回最终文件的 SAF URI 字符串。
 */
actual fun writeTranscodedToSaf(
    tempFile: PlatformFile,
    rootTreeUri: String,
    showName: String,
    fileName: String,
): String {
    val root = PlatformFile(rootTreeUri)            // tree URI (UriWrapper)
    val showDir = root div showName
    if (!showDir.exists()) showDir.createDirectories()

    var target = showDir div fileName
    var name = fileName
    var counter = 2
    while (target.exists()) {
        val dot = fileName.lastIndexOf('.')
        name = if (dot > 0) "${fileName.substring(0, dot)} ($counter)${fileName.substring(dot)}"
        else "$fileName ($counter)"
        target = showDir div name
        counter++
    }

    tempFile.source().use { input ->
        target.sink(append = false).use { output ->
            input.copyTo(output)
        }
    }
    return target.path   // SAF document URI 字符串
}
```

> 注意：`PlatformFile.path` 在 UriWrapper 上返回 content:// URI 字符串。`copyTo` 用 kotlinx.io。若签名不符，按 FileKit 0.14.2 实际调整。

- [ ] **步骤 2：commonMain expect 声明**

新建 `shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/SafWriter.kt`：

```kotlin
package com.skyd.podaura.model.repository.download

import io.github.vinceglb.filekit.PlatformFile

expect fun writeTranscodedToSaf(
    tempFile: PlatformFile,
    rootTreeUri: String,
    showName: String,
    fileName: String,
): String
```

- jvmMain actual 抛 UnsupportedOperationException（下载转码仅 Android）。

- [ ] **步骤 3：Commit**

```
git add shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/SafWriter.kt \
        shared/src/androidMain/kotlin/com/skyd/podaura/model/repository/download/SafWriter.android.kt \
        shared/src/jvmMain/kotlin/com/skyd/podaura/model/repository/download/SafWriter.jvm.kt
git commit -m "[feature] Add SafWriter for writing transcoded files to SAF directory"
```

### 任务 4.3：TranscodeHook（下载完成后转码+写SAF+更新DB）

**文件：**
- 新建：`shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/TranscodeHook.kt`
- 新建：`shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/EpisodeMetadataMapper.kt`

- [ ] **步骤 1：EpisodeMetadataMapper**

```kotlin
package com.skyd.podaura.model.repository.download

import com.skyd.podaura.model.bean.article.ArticleWithFeed
import com.skyd.transcoder.naming.EpisodeMetadata
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun ArticleWithFeed.toEpisodeMetadata(): EpisodeMetadata {
    val article = articleWithEnclosure.article
    val pubDate = article.date?.let { ts ->
        runCatching {
            Instant.fromEpochMilliseconds(ts)
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()  // YYYY-MM-DD
        }.getOrNull()
    }
    return EpisodeMetadata(
        showName = feed.title.orEmpty().ifBlank { feed.url },
        episodeTitle = article.title.orEmpty().ifBlank { "untitled" },
        episodeNumber = articleWithEnclosure.media?.episode,
        seasonNumber = articleWithEnclosure.media?.season,
        pubDate = pubDate,
    )
}
```

> 执行前确认：`ArticleWithFeed` 的字段路径（articleWithEnclosure.article / articleWithEnclosure.media / feed）。调研确认结构，但 import 和字段名以实际为准。`kotlinx.datetime` 是否已在 shared 依赖（若无需用 java.time 或基座既有日期工具）。

- [ ] **步骤 2：TranscodeHook**

```kotlin
package com.skyd.podaura.model.repository.download

import com.skyd.downloader.db.DownloadEntity
import com.skyd.downloader.di.Downloader
import com.skyd.podaura.ext.getOrDefault
import com.skyd.podaura.model.db.dao.EnclosureDao
import com.skyd.podaura.model.db.dao.ArticleDao
import com.skyd.podaura.model.preference.dataStore
import com.skyd.podaura.model.preference.download.AutoTranscodeMp3Preference
import com.skyd.podaura.model.preference.download.DownloadNamingTemplatePreference
import com.skyd.podaura.model.preference.download.DownloadRootDirPreference
import com.skyd.podaura.model.preference.download.KeepOriginalAfterTranscodePreference
import com.skyd.podaura.model.preference.download.TranscodeBitratePreference
import com.skyd.fundation.di.get
import com.skyd.transcoder.Transcoder
import com.skyd.transcoder.model.AudioFormat
import com.skyd.transcoder.model.TranscodeConfig
import com.skyd.transcoder.naming.NamingTemplate
import com.skyd.transcoder.shouldTranscode
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.first
import kotlinx.io.files.Path

class TranscodeHook {
    private val transcoder: Transcoder = get()
    private val downloader: Downloader = get()

    /**
     * 下载完成后：探测格式 -> 转 MP3（若需）-> 写 SAF -> 更新 DownloadEntity。
     * @param entity 下载完成的 DownloadEntity（path/fileName 是 cacheDir 临时文件）
     */
    suspend fun onDownloadSuccess(entity: DownloadEntity) {
        val dataStore = dataStore
        val autoTranscode = dataStore.getOrDefault(AutoTranscodeMp3Preference)
        val rootUri = dataStore.getOrDefault(DownloadRootDirPreference)
        if (!autoTranscode || rootUri.isEmpty()) {
            // 不转码或未配 SAF 根目录：保留原文件不动（或按基座原逻辑 addNewFile）
            return
        }

        val articleId = get<EnclosureDao>().getMediaArticleId(entity.url)
        val article = articleId?.let { get<ArticleDao>().getArticleWithFeed(it).first() }
        val metadata = article?.toEpisodeMetadata() ?: return

        val template = NamingTemplate.valueOf(dataStore.getOrDefault(DownloadNamingTemplatePreference))
        val bitrate = dataStore.getOrDefault(TranscodeBitratePreference)
        val keepOriginal = dataStore.getOrDefault(KeepOriginalAfterTranscodePreference)
        val config = TranscodeConfig(bitrateKbps = bitrate)

        val sourceFormat = AudioFormat.fromUrl(entity.url)
        val tempInput = PlatformFile(Path(entity.path, entity.fileName).toString())
        val outputName = template.render(metadata) + ".mp3"

        val finalUri: String
        val finalSize: Long

        if (shouldTranscode(sourceFormat, config)) {
            // 转码到 cacheDir 临时 mp3
            val tempOutput = PlatformFile(Path(entity.path, "$outputName.tmp").toString())
            transcoder.transcode(
                input = tempInput.path,
                output = tempOutput.path,
                config = config,
            ).collect { /* progress, 可更新 transcodeStatus */ }

            finalUri = writeTranscodedToSaf(tempOutput, rootUri, metadata.showName, outputName)
            finalSize = PlatformFile(finalUri).size()
            // 删除临时输出
            // tempOutput.delete()  // 按 FileKit API
        } else {
            // 已是 MP3 或未知：直接写原文件到 SAF
            finalUri = writeTranscodedToSaf(tempInput, rootUri, metadata.showName, outputName)
            finalSize = PlatformFile(finalUri).size()
        }

        // 更新 DownloadEntity
        downloader.find(entity.id) { e ->
            // 通过 DAO 更新 outputUri/transcodeStatus/finalSize
            // 需 DownloadDao 加 update 方法或复用现有
        }

        // 清理 cacheDir 临时原文件（若不保留）
        if (!keepOriginal) {
            // tempInput.delete()
        }
    }
}
```

> 执行前确认：`Downloader` 类的 API（find/update）、DownloadDao 的更新方法、`PlatformFile(path).size()` / `delete()` 在 FileKit 0.14.2 的签名、`dataStore` 的 import 路径、`Transcoder` 在 Koin 的注册（计划一已注册 transcoderModule）。`kotlinx.io.files.Path` 与 FileKit PlatformFile 的转换。

- [ ] **步骤 3：Commit**

```
git add shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/TranscodeHook.kt \
        shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/EpisodeMetadataMapper.kt
git commit -m "[feature] Add TranscodeHook for post-download transcode + SAF write"
```

### 任务 4.4：DownloadStarter + DownloadManager 接入

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/DownloadStarter.kt`
- 修改：`shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/DownloadManager.kt`

- [ ] **步骤 1：DownloadStarter 改用 cacheDir 临时目录 + 渲染文件名**

```kotlin
override suspend fun download(url: String, type: String? = null) {
    withContext(Dispatchers.IO) {
        val articleId = get<EnclosureDao>().getMediaArticleId(url)
        val article = articleId?.let { get<ArticleDao>().getArticleWithFeed(it).first() }
        // 下载到 cacheDir 临时目录（文件系统路径，FFmpeg 可读）
        val saveDir = Const.DOWNLOAD_TEMP_DIR   // 新增，见任务 5.1 或 fundation Const
        val fileName = article?.toEpisodeMetadata()?.let { meta ->
            // 用原 URL 扩展名 + 简化名（最终名在 TranscodeHook 渲染）
            FileUtil.getFileNameFromUrl(url)    // 临时文件用 URL basename
        } ?: FileUtil.getFileNameFromUrl(url)
        get<IDownloadManager>().download(url = url, path = saveDir, fileName = fileName)
    }
}
```

> 执行前确认：`Const.DOWNLOAD_TEMP_DIR` 需新增（fundation Const.android.kt，基于 cacheDir，仿 TEMP_PICTURES_DIR）。`FileUtil.getFileNameFromUrl` 在 downloader 模块（commonMain，可访问？shared 依赖 downloader，可调）。或用本地实现。

- [ ] **步骤 2：DownloadManager.listenDownloadEvent Event.Success 调 TranscodeHook**

修改 `DownloadManager.kt` 的 `listenDownloadEvent`：

```kotlin
is Event.Success -> {
    val articleId = get<EnclosureDao>().getMediaArticleId(event.entity.url)
    if (articleId != null) {
        // 转码 + 写 SAF（新增）
        runCatching { get<TranscodeHook>().onDownloadSuccess(event.entity) }
            .onFailure { log.e(throwable = it) { "TranscodeHook failed" } }
        // 保留基座原逻辑（addNewFile 到媒体库）若不冲突
    }
}
```

- [ ] **步骤 3：Koin 注册 TranscodeHook**

在 `shared/.../di/` 的某 module（如 repositoryModule）加 `single { TranscodeHook() }`。

- [ ] **步骤 4：Commit + push CI 验证**

```
git add shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/DownloadStarter.kt \
        shared/src/commonMain/kotlin/com/skyd/podaura/model/repository/download/DownloadManager.kt
git commit -m "[feature] Hook transcode into download flow (cacheDir temp + SAF write)"
git push origin feature/podcast-downloader
```

CI 应通过编译（运行时转码靠设备测试，里程碑7）。

---

## 里程碑 5：SAF 下载位置 + 设置 UI

### 任务 5.1：5 个偏好类 + Const.DOWNLOAD_TEMP_DIR

**文件：** 5 个偏好类（`shared/src/commonMain/.../model/preference/download/`）+ `fundation/.../config/Const.android.kt` 加 DOWNLOAD_TEMP_DIR。

- [ ] **步骤 1：5 个偏好类**（仿 BackgroundPlayPreference / DateStylePreference 模式）

```kotlin
// DownloadRootDirPreference.kt
@Preference
object DownloadRootDirPreference : BasePreference<String>() {
    override val key = stringPreferencesKey("downloadRootDir")
    override val default = ""
}

// AutoTranscodeMp3Preference.kt
@Preference
object AutoTranscodeMp3Preference : BasePreference<Boolean>() {
    override val key = booleanPreferencesKey("autoTranscodeMp3")
    override val default = true
}

// TranscodeBitratePreference.kt
@Preference
object TranscodeBitratePreference : BasePreference<Int>() {
    const val BITRATE_64 = 64; const val BITRATE_96 = 96
    const val BITRATE_128 = 128; const val BITRATE_192 = 192
    val values = listOf(BITRATE_64, BITRATE_96, BITRATE_128, BITRATE_192)
    override val key = intPreferencesKey("transcodeBitrate")
    override val default = BITRATE_128
}

// KeepOriginalAfterTranscodePreference.kt
@Preference
object KeepOriginalAfterTranscodePreference : BasePreference<Boolean>() {
    override val key = booleanPreferencesKey("keepOriginalAfterTranscode")
    override val default = false
}

// DownloadNamingTemplatePreference.kt
@Preference
object DownloadNamingTemplatePreference : BasePreference<String>() {
    const val TITLE_SHOW = "TitleAndShow"
    const val SHOW_NUMBER_TITLE = "ShowNumberTitle"
    const val DATE_TITLE = "DateAndTitle"
    val values = arrayOf(TITLE_SHOW, SHOW_NUMBER_TITLE, DATE_TITLE)
    override val key = stringPreferencesKey("downloadNamingTemplate")
    override val default = TITLE_SHOW
}
```

> 执行前确认：`@Preference` 注解的包（`com.skyd.podaura.model.preference` 或 ksp.annotation）、`stringPreferencesKey`/`booleanPreferencesKey`/`intPreferencesKey` 的 import（`androidx.datastore.preferences.core`）。读 `BackgroundPlayPreference.kt` 确认完整模式。

- [ ] **步骤 2：Const.DOWNLOAD_TEMP_DIR**

在 `fundation/src/androidMain/kotlin/com/skyd/fundation/config/Const.android.kt` 仿 `TEMP_PICTURES_DIR`：

```kotlin
actual val Const.DOWNLOAD_TEMP_DIR: String
    get() = File(get<Context>().cacheDir.path, "downloads").apply { if (!exists()) mkdirs() }.path
```

commonMain Const 加 `expect val DOWNLOAD_TEMP_DIR: String`。

- [ ] **步骤 3：Commit**

### 任务 5.2：TransmissionScreen 5 个设置项 UI

**文件：** 修改 `TransmissionScreen.kt` + `SettingsList.kt`（取消注释）+ `SettingsDetailPaneNavDisplay.kt`（路由）+ strings.xml。

- [ ] **步骤 1：TransmissionScreen 加 5 项**

```kotlin
group(text = { getString(Res.string.transmission_screen_config_category) }) {
    // 1. 下载根目录（SAF 选目录）
    BaseSettingsItem(
        title = { Text(stringResource(Res.string.download_root_dir)) },
        description = { Text(DownloadRootDirPreference.current.ifBlank { stringResource(Res.string.not_set) }) },
        onClick = { /* launch rememberDirectoryPickerLauncher, onResult put DownloadRootDirPreference */ },
    )
    // 2. 自动转 MP3 开关
    SwitchSettingsItem(
        title = { Text(stringResource(Res.string.auto_transcode_mp3)) },
        checked = AutoTranscodeMp3Preference.current,
        onCheckedChange = { AutoTranscodeMp3Preference.put(scope, it) },
    )
    // 3. 码率（CheckableListMenu 预设）
    BaseSettingsItem(title = { Text(stringResource(Res.string.transcode_bitrate)) }, onClick = { /* menu */ })
    // 4. 命名模板（CheckableListMenu 预设）
    BaseSettingsItem(title = { Text(stringResource(Res.string.naming_template)) }, onClick = { /* menu */ })
    // 5. 保留原文件开关
    SwitchSettingsItem(
        title = { Text(stringResource(Res.string.keep_original_after_transcode)) },
        checked = KeepOriginalAfterTranscodePreference.current,
        onCheckedChange = { KeepOriginalAfterTranscodePreference.put(scope, it) },
    )
}
```

> 执行前确认：读 `AppearanceScreen.kt` 的 `SwitchSettingsItem` + `CheckableListMenu` + `BaseSettingsItem` 用法、`rememberDirectoryPickerLauncher` 的 FileKit API、`scope` 的获取（rememberCoroutineScope）。SAF 选目录后调 `bookmarkData()` 持久化权限（存 URI 到 DownloadRootDirPreference）。

- [ ] **步骤 2：SettingsList 取消注释 TransmissionRoute + 路由注册**

> 执行前确认：读 `SettingsList.kt` 行 135-146 的注释块 + `SettingsDetailPaneNavDisplay.kt` 的路由注册模式。

- [ ] **步骤 3：strings.xml 加文案**

在 `shared/src/commonMain/resources/MR/base/strings.xml` 加 `download_root_dir`、`auto_transcode_mp3`、`transcode_bitrate`、`naming_template`、`keep_original_after_transcode`、`not_set` 等。

- [ ] **步骤 4：Commit + push CI 验证**

### 任务 5.3：seasonNumber 数据支持（命名模板变量）

**文件：** `RssMediaBean.kt` 加 season + `Item.kt` 加 itunesSeason + `RssExt.kt` 加 season 映射 + `Migration27To28.kt` + `AppDatabase.kt` version 28。

- [ ] **步骤 1-5：** 仿 `episode` 字段模式（调研已给 4 处改动）。照抄 `Migration11To12.kt`（加 episode 列的迁移）模式建 `Migration27To28.kt` 加 season 列。

- [ ] **步骤 6：Commit + push CI 验证**

---

## 里程碑 6：文件大小展示 + 通知

### 任务 6.1：下载记录 UI 展示最终大小 + 转码状态

**文件：** 下载列表项 UI（`shared/.../ui/screen/.../download/`，执行前确认具体文件）。

- [ ] **步骤 1：** 下载列表项展示 status（下载中/转码中/完成）+ 原始大小 -> 最终大小（DownloadEntity.finalSize）。`DownloadInfoBean` 加 finalSize/transcodeStatus 字段（DownloadManager.toDownloadInfoBean 映射）。

### 任务 6.2：通知展示最终大小

**文件：** `DownloadNotificationManager.kt`。

- [ ] **步骤 1：** `sendDownloadSuccessNotification` 展示 finalSize（而非 totalBytes）。读 `DownloadNotificationManager.kt` 确认方法签名。

- [ ] **步骤 2：Commit + push CI**

---

## 里程碑 7：端到端验证 + release APK

### 任务 7.1：端到端仪器测试（可选，需设备）

- [ ] **步骤 1：** 写 androidTest：订阅 RSS -> 下载单集 -> 验证 SAF 目标 `{root}/{show}/{name}.mp3` 存在 + 可播放 + DB 记录正确。需设备/模拟器。

### 任务 7.2：release APK

- [ ] **步骤 1：** 若用户提供签名密钥（存 GitHub Actions secret），改 android.yml 加 `assembleGithubRelease` + 签名。否则继续 debug APK。

### 任务 7.3：最终整分支审查 + 合并

- [ ] **步骤 1：** 用 superpowers:requesting-code-review 对整个 feature/podcast-downloader 分支做整分支审查。
- [ ] **步骤 2：** 修复审查发现。
- [ ] **步骤 3：** superpowers:finishing-a-development-branch 决定合并/PR。

---

## 自检

### 1. 规格覆盖度
- §3.1 transcoder 模块：✅ 计划一完成
- §3.2 转码流程 hook：✅ 任务 4.3-4.4
- §3.3 存储与命名（SAF + 按节目台分文件夹 + 命名模板）：✅ 任务 4.2（SAF）+ 4.3（命名）+ 5.2（设置）
- §3.4 文件大小展示：✅ 任务 6.1
- §3.5 播放：沿用基座，不增强（非目标）
- §5 错误处理（SAF 失效/转码失败/空间不足/文件名冲突）：✅ TranscodeHook runCatching + SafWriter 重名处理；空间不足检查需补（任务 4.3 加）
- §10 默认决策：✅ 5 个偏好默认值对齐（码率128、自动转码开、保留原文件否、命名 TitleAndShow）

### 2. 占位符扫描
多处标"执行前确认：读 X 文件确认 Y"。这些是有具体文件和确认点的步骤（基于调研但签名需以实际为准），非空占位符。CI 验证 + 设备测试兜底。

### 3. 类型一致性
- `TranscodeHook` 用 `Transcoder`（计划一注册）、`TranscodeConfig`/`AudioFormat`/`shouldTranscode`/`NamingTemplate`/`EpisodeMetadata`（计划一）。
- `DownloadEntity` 新字段 outputUri/transcodeStatus/finalSize 在 4.1 加，6.1 用。
- `EpisodeMetadataMapper` 产出 `EpisodeMetadata`（计划一），字段名对齐。
- 5 个偏好类 `@Preference` + BasePreference，KSP 自动注册。

---

## 执行交接

计划已保存到 `docs/superpowers/plans/2026-07-31-podcast-downloader-m4-m7.md`。

**执行方式：** 子代理驱动 + CI 验证（同计划一）。任务 4.1-4.4 是核心，建议按顺序（4.1 加字段 -> 4.2 SafWriter -> 4.3 TranscodeHook -> 4.4 接入），每任务 commit + push CI 验证。

**注意：** 里程碑 4 触及核心下载逻辑，风险高于计划一。TranscodeHook 的 DownloadEntity 更新、SAF 写入 API、ArticleWithFeed 字段路径需执行时读代码确认。建议每个任务子代理先读相关文件再改。
