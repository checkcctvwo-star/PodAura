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