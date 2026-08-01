package com.skyd.podaura.model.repository.download

import co.touchlab.kermit.Logger
import com.skyd.downloader.db.DownloadDao
import com.skyd.downloader.db.DownloadEntity
import com.skyd.fundation.di.get
import com.skyd.podaura.ext.getOrDefault
import com.skyd.podaura.model.db.dao.ArticleDao
import com.skyd.podaura.model.db.dao.EnclosureDao
import com.skyd.podaura.model.preference.dataStore
import com.skyd.podaura.model.preference.download.AutoTranscodeMp3Preference
import com.skyd.podaura.model.preference.download.DownloadNamingTemplatePreference
import com.skyd.podaura.model.preference.download.DownloadRootDirPreference
import com.skyd.podaura.model.preference.download.KeepOriginalAfterTranscodePreference
import com.skyd.podaura.model.preference.download.TranscodeBitratePreference
import com.skyd.transcoder.Transcoder
import com.skyd.transcoder.model.AudioFormat
import com.skyd.transcoder.model.TranscodeConfig
import com.skyd.transcoder.naming.NamingTemplate
import com.skyd.transcoder.naming.sanitizeFileName
import com.skyd.transcoder.shouldTranscode
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.delete
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.io.files.Path

/**
 * Post-download hook: transcodes the downloaded audio to MP3 (when needed), writes the result into
 * the user-selected SAF directory, and updates the [DownloadEntity] with the final URI, size, and
 * transcode status.
 *
 * Invoked by [DownloadManager] on [com.skyd.downloader.download.Event.Success] when both
 * [AutoTranscodeMp3Preference] is enabled and [DownloadRootDirPreference] is non-empty. The hook
 * resolves the article/feed metadata via the enclosure url, renders the output file name from the
 * configured [NamingTemplate], transcodes with [Transcoder] unless the source is already MP3 (or
 * its format is unknown), and finally streams the bytes into SAF via [writeTranscodedToSaf].
 *
 * On any failure the entity's `transcodeStatus` is marked `"failed"` and the error is logged; the
 * caller ([DownloadManager]) additionally wraps the whole call in its own `runCatching`.
 */
class TranscodeHook {
    private val transcoder: Transcoder = get()

    suspend fun onDownloadSuccess(entity: DownloadEntity) {
        val autoTranscode = dataStore.getOrDefault(AutoTranscodeMp3Preference)
        val rootUri = dataStore.getOrDefault(DownloadRootDirPreference)
        // DownloadManager already gates on these, but guard defensively in case this hook is
        // invoked directly.
        if (!autoTranscode || rootUri.isEmpty()) return

        runCatching {
            val articleId = get<EnclosureDao>().getMediaArticleId(entity.url)
                ?: return@runCatching
            val article = get<ArticleDao>().getArticleWithFeed(articleId).first()
                ?: return@runCatching
            val metadata = article.toEpisodeMetadata()

            val template = NamingTemplate.valueOf(
                dataStore.getOrDefault(DownloadNamingTemplatePreference)
            )
            val bitrate = dataStore.getOrDefault(TranscodeBitratePreference)
            val keepOriginal = dataStore.getOrDefault(KeepOriginalAfterTranscodePreference)
            val config = TranscodeConfig(bitrateKbps = bitrate)

            val sourceFormat = AudioFormat.fromUrl(entity.url)
            val outputFileName = sanitizeFileName(
                name = template.render(metadata),
                extension = "mp3",
                maxLength = 200,
            )
            val tempInputPath = Path(entity.path, entity.fileName).toString()

            val finalUri: String
            val finalSize: Long

            if (shouldTranscode(sourceFormat, config)) {
                val tempOutputPath = Path(entity.path, "$outputFileName.tmp").toString()
                transcoder.transcode(
                    input = tempInputPath,
                    output = tempOutputPath,
                    config = config,
                ).collect { /* progress ignored */ }
                finalUri = writeTranscodedToSaf(
                    tempFile = PlatformFile(tempOutputPath),
                    rootTreeUri = rootUri,
                    showName = metadata.showName,
                    fileName = outputFileName,
                )
                finalSize = PlatformFile(tempOutputPath).size()
                // The SAF copy is what we keep; remove the transcoded temp file.
                runCatching { PlatformFile(tempOutputPath).delete(mustExist = false) }
            } else {
                // Source is already MP3 (or format unknown): copy the downloaded file straight to SAF.
                finalUri = writeTranscodedToSaf(
                    tempFile = PlatformFile(tempInputPath),
                    rootTreeUri = rootUri,
                    showName = metadata.showName,
                    fileName = outputFileName,
                )
                finalSize = PlatformFile(tempInputPath).size()
            }

            get<DownloadDao>().update(
                entity.copy(
                    outputUri = finalUri,
                    transcodeStatus = "success",
                    finalSize = finalSize,
                )
            )

            if (!keepOriginal) {
                runCatching { PlatformFile(tempInputPath).delete(mustExist = false) }
            }
        }.onFailure {
            Logger.e(throwable = it) { "TranscodeHook failed for download ${entity.id}" }
            runCatching {
                get<DownloadDao>().update(entity.copy(transcodeStatus = "failed"))
            }
        }
    }
}
