package com.appgate.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeSearchTest {
    private val entries = listOf(
        KnowledgeEntry("1", "TikTok search notes", "https://www.tiktok.com/", "Search can require manual verification.", 1L, "web"),
        KnowledgeEntry("2", "Android browser", "upload://browser.md", "WebView knowledge storage and navigation.", 2L, "upload")
    )
    private val skills = listOf(
        SiteSkill("tiktok.com", "Tap search, complete captcha manually if shown, then continue.", 3L)
    )

    @Test
    fun titleMatchRanksFirst() {
        val results = KnowledgeSearch.search("Android browser", entries, skills, 10)
        assertTrue(results.isNotEmpty())
        assertEquals("Android browser", results.first().title)
    }

    @Test
    fun siteSkillCanMatchInstructions() {
        val results = KnowledgeSearch.search("captcha", entries, skills, 10)
        assertTrue(results.any { it.kind == "site-skill" && it.source.contains("tiktok.com") })
    }

    @Test
    fun emptyQueryReturnsNothing() {
        assertTrue(KnowledgeSearch.search("   ", entries, skills, 10).isEmpty())
    }

    @Test
    fun resultLimitIsRespected() {
        val results = KnowledgeSearch.search("search", entries, skills, 1)
        assertEquals(1, results.size)
    }

    @Test
    fun normalizeHostHandlesUrlsAndWww() {
        assertEquals("example.com", KnowledgeSearch.normalizeHost("https://www.example.com/path?q=1"))
        assertEquals("example.com", KnowledgeSearch.normalizeHost("www.example.com"))
    }
}
