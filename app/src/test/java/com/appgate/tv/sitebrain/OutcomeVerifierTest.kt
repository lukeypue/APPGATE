package com.appgate.tv.sitebrain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutcomeVerifierTest {
    @Test
    fun routeAndPageTypeChangeCountsAsVerifiedOutcome() {
        val before = snapshot("home", PageType.HOME, "home")
        val after = snapshot("results", PageType.RESULT_LIST, "results")
        val edge = edge(ActionKind.SEARCH, PageType.RESULT_LIST)
        val result = OutcomeVerifier.verify(before, edge, after)
        assertTrue(result.success)
        assertTrue(result.evidence.any { it.contains("expected page type") })
    }

    @Test
    fun noOpClickDoesNotGainConfidence() {
        val before = snapshot("same", PageType.CATEGORY, "same")
        val edge = edge(ActionKind.OPEN_CATEGORY, PageType.CATEGORY)
        val result = OutcomeVerifier.verify(before, edge, before)
        assertFalse(result.success)
        assertTrue(result.confidenceDelta < 0)
    }

    private fun snapshot(route: String, type: PageType, fp: String) = PageSnapshot(
        url = "https://example.com/$route", host = "example.com", routeSignature = "example.com/$route", title = route,
        visibleTextSummary = route, headings = listOf(route), elements = emptyList(), pageType = type,
        loginDetected = false, challengeDetected = false, fingerprint = fp
    )

    private fun edge(kind: ActionKind, expected: PageType?) = SiteEdge(
        id = "e", fromFingerprint = "a", toFingerprint = null, actionKind = kind, semanticIntent = kind.name,
        label = kind.name, safetyClass = SafetyClass.SAFE, locatorHints = emptyList(), expectedPageType = expected,
        observedPostcondition = null, confidence = .5, successCount = 0, failureCount = 0, lastVerifiedAt = null
    )
}
