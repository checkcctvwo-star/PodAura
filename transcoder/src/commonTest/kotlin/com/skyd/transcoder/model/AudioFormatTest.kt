package com.skyd.transcoder.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AudioFormatTest {
    @Test
    fun fromExtensionLowercase() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromExtension("mp3"))
    }

    @Test
    fun fromExtensionUppercaseWithDot() {
        assertEquals(AudioFormat.M4A, AudioFormat.fromExtension(".M4A"))
    }

    @Test
    fun fromExtensionUnknown() {
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.fromExtension("xyz"))
    }

    @Test
    fun fromExtensionEmpty() {
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.fromExtension(""))
    }

    @Test
    fun fromUrlStripsQueryAndFragment() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromUrl("https://x.com/a.mp3?token=1#frag"))
    }

    @Test
    fun fromUrlNoExtension() {
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.fromUrl("https://x.com/a"))
    }

    @Test
    fun fromMimeTypeMp3() {
        assertEquals(AudioFormat.MP3, AudioFormat.fromMimeType("audio/mpeg"))
    }

    @Test
    fun fromMimeTypeAac() {
        assertEquals(AudioFormat.AAC, AudioFormat.fromMimeType("audio/aac"))
    }

    @Test
    fun fromMimeTypeUnknown() {
        assertEquals(AudioFormat.UNKNOWN, AudioFormat.fromMimeType("video/mp4"))
    }
}