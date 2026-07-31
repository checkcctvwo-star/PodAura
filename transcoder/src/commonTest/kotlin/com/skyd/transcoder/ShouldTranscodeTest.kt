package com.skyd.transcoder

import com.skyd.transcoder.model.AudioFormat
import com.skyd.transcoder.model.TranscodeConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShouldTranscodeTest {
    @Test
    fun mp3SourceSkipsTranscode() {
        assertFalse(shouldTranscode(AudioFormat.MP3, TranscodeConfig.DEFAULT))
    }

    @Test
    fun m4aSourceTranscodes() {
        assertTrue(shouldTranscode(AudioFormat.M4A, TranscodeConfig.DEFAULT))
    }

    @Test
    fun opusSourceTranscodes() {
        assertTrue(shouldTranscode(AudioFormat.OPUS, TranscodeConfig.DEFAULT))
    }

    @Test
    fun unknownSourceSkipsTranscode() {
        assertFalse(shouldTranscode(AudioFormat.UNKNOWN, TranscodeConfig.DEFAULT))
    }
}