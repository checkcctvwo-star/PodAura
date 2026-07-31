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
 *
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
