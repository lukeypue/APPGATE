package com.appgate.tv.sitebrain.trainer

import com.appgate.tv.sitebrain.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainerCoverageTest {
    @Test
    fun cannotReachTargetWithoutCriticalSearchResultsDetailAndReturnFlow() {
        val brain = brain(
            nodes = listOf(node("results", PageType.RESULT_LIST), node("detail", PageType.DETAIL)),
            edges = listOf(
                verified("search", ActionKind.SEARCH, "home", "results"),
                verified("detail", ActionKind.OPEN_DETAIL, "results", "detail")
            )
        )
        val report = TrainerCoverage.evaluate(brain)
        assertFalse(report.targetReached)
        assertTrue("BACK_TO_RESULTS" in report.criticalMissing)
    }

    @Test
    fun discoveredSafeCapabilitiesMustBeVerifiedBeforeTarget() {
        val baseline = completeBrain()
        assertTrue(TrainerCoverage.evaluate(baseline).targetReached)

        val discoveredFilter = unverified("filter", ActionKind.APPLY_FILTER, "results")
        val withUnverifiedFilter = baseline.copy(edges = baseline.edges + discoveredFilter, unresolvedBranches = 1)
        val report = TrainerCoverage.evaluate(withUnverifiedFilter)
        assertFalse(report.targetReached)
        assertTrue(report.percent < 95)
        assertTrue(report.criticalMissing.any { it.contains("FILTER") })
    }

    @Test
    fun protectedBoundariesNeverIncreaseCoverage() {
        val baseline = completeBrain()
        val baseReport = TrainerCoverage.evaluate(baseline)
        val protected = baseline.copy(
            nodes = baseline.nodes + node("login", PageType.LOGIN),
            edges = baseline.edges + SiteEdge(
                id = "login", fromFingerprint = "results", toFingerprint = "login", actionKind = ActionKind.LOGIN,
                semanticIntent = "LOGIN", label = "Sign in", safetyClass = SafetyClass.BLOCKED,
                locatorHints = emptyList(), expectedPageType = PageType.LOGIN, observedPostcondition = null,
                confidence = 1.0, successCount = 5, failureCount = 0, lastVerifiedAt = 1L
            )
        )
        val report = TrainerCoverage.evaluate(protected)
        assertTrue(report.protectedBoundaries.isNotEmpty())
        assertTrue(report.percent <= baseReport.percent)
    }

    private fun completeBrain(): SiteBrainState {
        val nodes = listOf(
            node("home", PageType.HOME), node("results", PageType.RESULT_LIST), node("detail", PageType.DETAIL)
        )
        val edges = listOf(
            verified("search", ActionKind.SEARCH, "home", "results"),
            verified("detail", ActionKind.OPEN_DETAIL, "results", "detail"),
            verified("back", ActionKind.BACK, "detail", "results")
        )
        return brain(nodes, edges)
    }

    private fun brain(nodes: List<SiteNode>, edges: List<SiteEdge>) = SiteBrainState(
        host = "example.com", readiness = ReadinessLevel.LEARNING, nodes = nodes, edges = edges,
        unresolvedBranches = 0, coverageScore = 0.0, revision = 1
    )

    private fun node(fp: String, type: PageType) = SiteNode(fp, "/$fp", type, fp, 1L, 1L, 1)

    private fun verified(id: String, kind: ActionKind, from: String, to: String) = SiteEdge(
        id = id, fromFingerprint = from, toFingerprint = to, actionKind = kind, semanticIntent = kind.name,
        label = kind.name, safetyClass = SafetyClass.SAFE, locatorHints = emptyList(), expectedPageType = null,
        observedPostcondition = "verified", confidence = .9, successCount = 2, failureCount = 0, lastVerifiedAt = 1L
    )

    private fun unverified(id: String, kind: ActionKind, from: String) = SiteEdge(
        id = id, fromFingerprint = from, toFingerprint = null, actionKind = kind, semanticIntent = kind.name,
        label = kind.name, safetyClass = SafetyClass.SAFE, locatorHints = emptyList(), expectedPageType = null,
        observedPostcondition = null, confidence = .2, successCount = 0, failureCount = 0, lastVerifiedAt = null
    )
}
