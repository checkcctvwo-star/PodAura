package com.skyd.transcoder.model

data class TranscodeConfig(
    val targetFormat: AudioFormat = AudioFormat.MP3,
    val bitrateKbps: Int = 128,
) {
    init {
        require(targetFormat == AudioFormat.MP3) {
            "Only MP3 target is supported, was $targetFormat"
        }
        require(bitrateKbps in VALID_BITRATES) {
            "bitrateKbps must be one of $VALID_BITRATES, was $bitrateKbps"
        }
    }

    companion object {
        val VALID_BITRATES = setOf(64, 96, 128, 192)
        val DEFAULT = TranscodeConfig()
    }
}