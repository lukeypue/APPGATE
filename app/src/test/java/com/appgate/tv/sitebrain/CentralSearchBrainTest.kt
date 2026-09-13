package com.appgate.tv.sitebrain

import com.appgate.tv.ParsedSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CentralSearchBrainTest {
    @Test
    fun selectsMultipleVerifiedRoutesAndDeepVerificationWave() {
        val brain = SiteBrainState(
            host = "example.com",
            readiness = ReadinessLevel.DEEP_SEARCH_READY,
            nodes = emptyList(),
            edges = listOf(
                edge("a", ActionKind.OPEN_CATEGORY, "Vacation Rentals", .9),
                edge("b", ActionKind.OPEN_CATEGORY, "Recreational Property", .82),
                edge("c", ActionKind.OPEN_DETAIL, "Listing details", .8)
            ),
            unresolvedBranches = 2,
            coverageScore = .88,
            revision = 3
        )
        val parsed = ParsedSearch(
            raw = "WorldMark timeshare rental with ocean view",
            coreQuery = "WorldMark timeshare rental",
            maxPrice = null,
            maxMileage = null,
            requiredTerms = listOf("ocean view"),
            conceptTerms = listOf("worldmark", "timeshare", "rental")
        )

        val waves = CentralSearchBrain.plan(parsed, listOf(brain))
        assertTrue(waves.isNotEmpty())
        assertTrue(waves.flatMap { it.paths }.any { it.label == "Vacation Rentals" })
        assertTrue(waves.flatMap { it.paths }.any { it.label == "Recreational Property" })
        assertTrue(waves.any { it.index == 3 && it.paths.any(SearchPath::requiresDeepVerification) })
        assertEquals(2, waves.first().unresolvedBranches)
    }

    private fun edge(id: String, kind: ActionKind, label: String, confidence: Double) = SiteEdge(
        id = id, fromFingerprint = "home", toFingerprint = "dest$id", actionKind = kind,
        semanticIntent = "${kind.name}:$label", label = label, safetyClass = SafetyClass.SAFE,
        locatorHints = listOf("#$id"), expectedPageType = if (kind == ActionKind.OPEN_DETAIL) PageType.DETAIL else PageType.CATEGORY,
        observedPostcondition = "worked", confidence = confidence, successCount = 2, failureCount = 0, lastVerifiedAt = 1L
    )
}
