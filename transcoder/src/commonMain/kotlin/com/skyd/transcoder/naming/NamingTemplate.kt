package com.skyd.transcoder.naming

enum class NamingTemplate(val template: String) {
    TitleAndShow("{episodeTitle} - {showName}"),
    ShowNumberTitle("{showName} - E{episodeNumber} - {episodeTitle}"),
    DateAndTitle("{pubDate} - {episodeTitle}");

    fun render(metadata: EpisodeMetadata): String {
        var result = template
        result = result.replace("{showName}", metadata.showName)
        result = result.replace("{episodeTitle}", metadata.episodeTitle)
        result = result.replace("{episodeNumber}", metadata.episodeNumber.orEmpty())
        result = result.replace("{seasonNumber}", metadata.seasonNumber.orEmpty())
        result = result.replace("{pubDate}", metadata.pubDate.orEmpty())
        return sanitizeFileNameSegment(result)
    }

    companion object {
        val DEFAULT: NamingTemplate = TitleAndShow
    }
}