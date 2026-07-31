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
