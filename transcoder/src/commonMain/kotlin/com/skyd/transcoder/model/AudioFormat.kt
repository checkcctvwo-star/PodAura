package com.skyd.transcoder.model

enum class AudioFormat(val extension: String, val mimeType: String) {
    MP3("mp3", "audio/mpeg"),
    M4A("m4a", "audio/mp4"),
    AAC("aac", "audio/aac"),
    OPUS("opus", "audio/opus"),
    OGG("ogg", "audio/ogg"),
    WAV("wav", "audio/wav"),
    UNKNOWN("unknown", "application/octet-stream");

    companion object {
        fun fromExtension(ext: String): AudioFormat {
            val lower = ext.lowercase().removePrefix(".")
            return entries.firstOrNull { it.extension == lower } ?: UNKNOWN
        }

        fun fromMimeType(mime: String): AudioFormat {
            val lower = mime.lowercase()
            return entries.firstOrNull { lower.contains(it.mimeType) } ?: UNKNOWN
        }

        fun fromUrl(url: String): AudioFormat {
            val clean = url.substringBefore('?').substringBefore('#')
            val ext = clean.substringAfterLast('.', missingDelimiterValue = "")
            return if (ext.isNotEmpty()) fromExtension(ext) else UNKNOWN
        }
    }
}