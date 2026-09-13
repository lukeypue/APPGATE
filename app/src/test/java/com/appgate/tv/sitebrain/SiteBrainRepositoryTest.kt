package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteBrainRepositoryTest {
    private class MemoryStore : SiteBrainStore {
        private val data = mutableMapOf<String, String>()
        override fun get(key: String): String? = data[key]
        override fun put(key: String, value: String) { data[key] = value }
    }

    @Test
    fun savesAndReloadsSemanticGraph() {
        val repo = SiteBrainRepository(MemoryStore())
        val host = "example.com"
        val state = SiteBrainState(
            host = host,
            readiness = ReadinessLevel.SEARCH_READY,
            nodes = listOf(
                SiteNode("a", "example.com/", PageType.HOME, "Home", 1, 2),
                SiteNode("b", "example.com/search", PageType.RESULT_LIST, "Results", 1, 2)
            ),
            edges = listOf(
                SiteEdge("edge1", "a", "b", ActionKind.SEARCH, "SEARCH", "Search", SafetyClass.SAFE, listOf("#search"), PageType.RESULT_LIST, "results visible", .8, 2, 0, 123L)
            ),
            unresolvedBranches = 3,
            coverageScore = .5,
            revision = 7
        )
        repo.save(state)
        val loaded = repo.load(host)

        assertEquals(ReadinessLevel.SEARCH_READY, loaded.readiness)
        assertEquals(2, loaded.nodes.size)
        assertEquals("SEARCH", loaded.edges.single().semanticIntent)
        assertEquals(.8, loaded.edges.single().confidence, .001)
        assertEquals(listOf("#search"), loaded.edges.single().locatorHints)
    }

    @Test
    fun verifiedSearchFlowAdvancesReadiness() {
        val store = MemoryStore()
        val repo = SiteBrainRepository(store)
        val home = snapshot("example.com", "home", PageType.HOME)
        val results = snapshot("example.com", "results", PageType.RESULT_LIST)
        repo.recordNode(home)
        repo.recordNode(results)
        repo.recordTransition(
            "example.com",
            home.fingerprint,
            SiteEdge("search", home.fingerprint, null, ActionKind.SEARCH, "SEARCH", "Search", SafetyClass.SAFE, listOf("#search"), PageType.RESULT_LIST, null, .6, 0, 0, null),
            results.fingerprint
        )
        val updated = repo.markSuccess("example.com", "search", "result list appeared", results.fingerprint)
        assertEquals(ReadinessLevel.SEARCH_READY, updated.readiness)
        assertTrue(updated.coverageScore > 0.0)
    }

    private fun snapshot(host: String, fp: String, type: PageType) = PageSnapshot(
        url = "https://$host/$fp", host = host, routeSignature = "$host/$fp", title = fp,
        visibleTextSummary = fp, headings = listOf(fp), elements = emptyList(), pageType = type,
        loginDetected = false, challengeDetected = false, fingerprint = fp
    )
}
