package com.appgate.sitebrainlab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgePackTest {
    @Test
    fun roundTripsSemanticKnowledge() {
        val pack = SiteKnowledgePack(
            schemaVersion = 1,
            domain = "cars.ksl.com",
            hostAliases = listOf("www.ksl.com"),
            generatedAtEpochMs = 1_789_000_000_000,
            coveragePercent = 67,
            readiness = "SEARCH_READY",
            nodes = listOf(PackNode("results-1", "RESULTS", "/search/make/Ford", "Ford Expedition results")),
            transitions = listOf(PackTransition("home", "results-1", "SEARCH", "Search", 0.91, true)),
            boundaries = listOf(PackBoundary("login", "HUMAN_OR_AUTH_REQUIRED"))
        )

        val decoded = KnowledgePackCodec.decode(KnowledgePackCodec.encode(pack))

        assertEquals(pack, decoded)
        assertEquals("cars.ksl.com", decoded.domain)
        assertTrue(decoded.transitions.single().verified)
    }
}
