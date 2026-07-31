package com.skyd.transcoder.naming

/**
 * Metadata for a single podcast episode, used to render naming templates.
 * All fields are pre-formatted strings (caller is responsible for date formatting, etc.).
 */
data class EpisodeMetadata(
    val showName: String,
    val episodeTitle: String,
    val episodeNumber: String? = null,
    val seasonNumber: String? = null,
    val pubDate: String? = null,
)