package com.skyd.transcoder.model

data class TranscodeProgress(
    val processedSeconds: Double,
    val totalSeconds: Double?,
    val sizeBytes: Long,
)

sealed class TranscodeState {
    data object Idle : TranscodeState()
    data class Running(val progress: TranscodeProgress) : TranscodeState()
    data class Success(val outputSize: Long) : TranscodeState()
    data class Failed(val reason: String) : TranscodeState()
}
