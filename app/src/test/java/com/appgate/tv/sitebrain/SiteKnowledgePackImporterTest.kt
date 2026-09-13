package com.appgate.tv.sitebrain

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class SiteKnowledgePackImporterTest {
    private fun e(v: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(v.toByteArray())
    private fun pack(confidence: Double = 0.8, schema: Int = 1) = """
        SITEBRAIN|$schema|${e("ebay.com")}|2000|75|${e("SEARCH_READY")}
        NODE|${e("results")}|${e("RESULTS")}|${e("/sch/i.html")}|${e("Search results")}
        EDGE|${e("home")}|${e("results")}|${e("SEARCH")}|${e("Search")}|$confidence|true
        BOUNDARY|${e("/signin")}|${e("HUMAN_OR_AUTH_REQUIRED")}
    """.trimIndent()

    @Test fun importsVerifiedSharedKnowledge() {
        val merged = SiteKnowledgePackImporter().mergeEncoded(pack(), SiteBrainState.empty("ebay.com"))
        assertEquals(ReadinessLevel.SEARCH_READY, merged.readiness)
        assertEquals(PageType.RESULT_LIST, merged.nodes.single().pageType)
        assertTrue(merged.edges.single().confidence == 0.8)
        assertEquals(1, merged.unresolvedBranches)
    }

    @Test fun strongerLocalVerifiedEvidenceWins() {
        val localEdge = SiteEdge("local", "home", "local-results", ActionKind.SEARCH, "SEARCH", "Search", SafetyClass.SAFE, emptyList(), null, "live", 0.95, 4, 0, 3000)
        val local = SiteBrainState.empty("ebay.com").copy(edges = listOf(localEdge))
        val merged = SiteKnowledgePackImporter().mergeEncoded(pack(0.8), local)
        assertEquals("local", merged.edges.single().id)
        assertEquals(0.95, merged.edges.single().confidence, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSchema() {
        SiteKnowledgePackImporter().mergeEncoded(pack(schema = 99), SiteBrainState.empty("ebay.com"))
    }
}
