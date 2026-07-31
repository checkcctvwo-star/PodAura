package com.skyd.transcoder

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
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
                // Both success and cancel close the flow; failure also closes (caller checks output)
                if (ReturnCode.isSuccess(s.returnCode) || ReturnCode.isCancel(s.returnCode)) {
                    close()
                } else {
                    close()
                }
            },
            { /* log callback, no-op */ },
            { stats: Statistics ->
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
