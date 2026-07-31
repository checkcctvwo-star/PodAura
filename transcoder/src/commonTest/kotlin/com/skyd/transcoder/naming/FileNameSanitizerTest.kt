package com.skyd.transcoder.naming

import kotlin.test.Test
import kotlin.test.assertEquals

class FileNameSanitizerTest {
    @Test
    fun replacesIllegalCharsWithUnderscore() {
        assertEquals("a_b_c", sanitizeFileNameSegment("a/b:c"))
    }

    @Test
    fun collapsesConsecutiveUnderscores() {
        assertEquals("a_b", sanitizeFileNameSegment("a///b"))
    }

    @Test
    fun trimsLeadingTrailingSpacesDotsUnderscores() {
        assertEquals("name", sanitizeFileNameSegment(" . name . "))
    }

    @Test
    fun truncatesToMaxLength() {
        val long = "a".repeat(300)
        val result = sanitizeFileNameSegment(long, maxLength = 200)
        assertEquals(200, result.length)
    }

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals("", sanitizeFileNameSegment(""))
    }

    @Test
    fun onlyIllegalCharsReturnsEmpty() {
        assertEquals("", sanitizeFileNameSegment("///"))
    }

    @Test
    fun sanitizeFileNameWithExtension() {
        assertEquals("name.mp3", sanitizeFileName("name", "mp3"))
    }

    @Test
    fun sanitizeFileNameWithDotExtension() {
        assertEquals("name.mp3", sanitizeFileName("name", ".mp3"))
    }

    @Test
    fun sanitizeFileNameWithoutExtension() {
        assertEquals("name", sanitizeFileName("name", null))
    }

    @Test
    fun sanitizeFileNameTruncatesBaseNotExtension() {
        val long = "a".repeat(300)
        val result = sanitizeFileName(long, "mp3", maxLength = 200)
        assertEquals("a".repeat(200) + ".mp3", result)
    }
}