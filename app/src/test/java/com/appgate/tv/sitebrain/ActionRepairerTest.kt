package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionRepairerTest {
    @Test
    fun changedFilterLabelRanksAboveUnrelatedControls() {
        val edge = SiteEdge(
            id = "price", fromFingerprint = "a", toFingerprint = null,
            actionKind = ActionKind.APPLY_FILTER, semanticIntent = "MAX_PRICE_FILTER",
            label = "Max Price", safetyClass = SafetyClass.SAFE,
            locatorHints = listOf("#old"), expectedPageType = PageType.RESULT_LIST,
            observedPostcondition = null, confidence = .7, successCount = 2,
            failureCount = 1, lastVerifiedAt = 1L
        )
        val snapshot = PageSnapshot(
            url = "https://example.com/search", host = "example.com", routeSignature = "example.com/search",
            title = "Search", visibleTextSummary = "", headings = emptyList(),
            elements = listOf(
                element("Price up to", "button"),
                element("Color", "button"),
                element("Buy now", "button")
            ), pageType = PageType.RESULT_LIST, loginDetected = false, challengeDetected = false, fingerprint = "b"
        )

        val ranked = ActionRepairer.rankReplacementCandidates(edge, snapshot)
        assertEquals("Price up to", ranked.first().label)
    }

    private fun element(label: String, role: String) = SemanticElement(
        id = label, tag = "button", role = role, label = label, href = null,
        inputType = null, selected = false, disabled = false, nearbyText = label
    )
}
