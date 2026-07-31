package com.skyd.transcoder.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TranscodeConfigTest {
    @Test
    fun defaultValues() {
        val c = TranscodeConfig.DEFAULT
        assertEquals(AudioFormat.MP3, c.targetFormat)
        assertEquals(128, c.bitrateKbps)
    }

    @Test
    fun validBitratesAccepted() {
        setOf(64, 96, 128, 192).forEach { TranscodeConfig(bitrateKbps = it) }
    }

    @Test
    fun invalidBitrateLowRejected() {
        assertFailsWith<IllegalArgumentException> { TranscodeConfig(bitrateKbps = 32) }
    }

    @Test
    fun invalidBitrateHighRejected() {
        assertFailsWith<IllegalArgumentException> { TranscodeConfig(bitrateKbps = 256) }
    }

    @Test
    fun nonMp3TargetRejected() {
        assertFailsWith<IllegalArgumentException> { TranscodeConfig(targetFormat = AudioFormat.M4A) }
    }
}