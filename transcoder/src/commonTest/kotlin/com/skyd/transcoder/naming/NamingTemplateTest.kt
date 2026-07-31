package com.skyd.transcoder.naming

import kotlin.test.Test
import kotlin.test.assertEquals

class NamingTemplateTest {
    private val meta = EpisodeMetadata(
        showName = "Daily Tech",
        episodeTitle = "EP1: Intro",
        episodeNumber = "1",
        seasonNumber = "2",
        pubDate = "2026-07-31",
    )

    @Test
    fun titleAndShowRendersDefault() {
        // ":" is illegal -> replaced with "_"
        assertEquals("EP1_ Intro - Daily Tech", NamingTemplate.TitleAndShow.render(meta))
    }

    @Test
    fun showNumberTitleRenders() {
        assertEquals("Daily Tech - E1 - EP1_ Intro", NamingTemplate.ShowNumberTitle.render(meta))
    }

    @Test
    fun dateAndTitleRenders() {
        assertEquals("2026-07-31 - EP1_ Intro", NamingTemplate.DateAndTitle.render(meta))
    }

    @Test
    fun missingEpisodeNumberReplacedWithEmpty() {
        val m = meta.copy(episodeNumber = null)
        assertEquals("Daily Tech - E - EP1_ Intro", NamingTemplate.ShowNumberTitle.render(m))
    }

    @Test
    fun missingSeasonNumberReplacedWithEmpty() {
        val m = meta.copy(seasonNumber = null)
        // seasonNumber not used in any preset, but ensure no crash
        assertEquals("EP1_ Intro - Daily Tech", NamingTemplate.TitleAndShow.render(m))
    }

    @Test
    fun missingPubDateReplacedWithEmpty() {
        val m = meta.copy(pubDate = null)
        assertEquals("- EP1_ Intro", NamingTemplate.DateAndTitle.render(m))
    }

    @Test
    fun illegalCharsInShowNameReplaced() {
        val m = meta.copy(showName = "Show/Name?")
        assertEquals("EP1_ Intro - Show_Name", NamingTemplate.TitleAndShow.render(m))
    }

    @Test
    fun defaultTemplateIsTitleAndShow() {
        assertEquals(NamingTemplate.TitleAndShow, NamingTemplate.DEFAULT)
    }
}