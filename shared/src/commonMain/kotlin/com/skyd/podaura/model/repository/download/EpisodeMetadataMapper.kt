package com.skyd.podaura.model.repository.download

import com.skyd.podaura.model.bean.article.ArticleWithFeed
import com.skyd.transcoder.naming.EpisodeMetadata
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Maps an [ArticleWithFeed] (article + feed + rss media) to the transcoder's [EpisodeMetadata].
 *
 * Fields are pre-formatted here so a [com.skyd.transcoder.naming.NamingTemplate] can render them
 * directly:
 * - [EpisodeMetadata.showName] falls back to the feed url when the feed title is blank.
 * - [EpisodeMetadata.episodeTitle] falls back to `"untitled"` when the article title is blank.
 * - [EpisodeMetadata.episodeNumber] / [EpisodeMetadata.seasonNumber] are passed through from
 *   [com.skyd.podaura.model.bean.article.RssMediaBean] as-is (already `String?`).
 * - [EpisodeMetadata.pubDate] is the article date (epoch millis) formatted as an ISO local date
 *   string (`yyyy-MM-dd`) in the system timezone, or `null` when the article has no date.
 */
fun ArticleWithFeed.toEpisodeMetadata(): EpisodeMetadata {
    val article = articleWithEnclosure.article
    val media = articleWithEnclosure.media
    val pubDate = article.date?.let { timestamp ->
        runCatching {
            Instant.fromEpochMilliseconds(timestamp)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
                .toString()
        }.getOrNull()
    }
    return EpisodeMetadata(
        showName = feed.title.orEmpty().ifBlank { feed.url },
        episodeTitle = article.title.orEmpty().ifBlank { "untitled" },
        episodeNumber = media?.episode,
        seasonNumber = media?.season,
        pubDate = pubDate,
    )
}
