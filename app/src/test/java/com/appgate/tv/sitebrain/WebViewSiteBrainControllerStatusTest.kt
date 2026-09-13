package com.appgate.tv.sitebrain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewSiteBrainControllerStatusTest {
    private class MemoryStore : SiteBrainStore {
        private val data = mutableMapOf<String, String>()
        override fun get(key: String): String? = data[key]
        override fun put(key: String, value: String) { data[key] = value }
    }

    @Test
    fun statusDoesNotCallObservedControlsLearned() {
        val controller = WebViewSiteBrainController(SiteBrainRepository(MemoryStore()))
        val snapshot = PageSnapshot(
            url = "https://example.com/search",
            host = "example.com",
            routeSignature = "example.com/search",
            title = "Results",
            visibleTextSummary = "Results",
            headings = listOf("Results"),
            elements = emptyList(),
            pageType = PageType.RESULT_LIST,
            loginDetected = false,
            challengeDetected = false,
            fingerprint = "fp"
        )
        val brain = SiteBrainState(
            host = "example.com",
            readiness = ReadinessLevel.LEARNING,
            nodes = emptyList(),
            edges = emptyList(),
            unresolvedBranches = 0,
            coverageScore = 0.03,
            revision = 1
        )
        val observation = SiteBrainObservation(
            snapshot = snapshot,
            brain = brain,
            controllerState = SiteBrainControllerState.SEARCHING,
            safeActionsFound = 277,
            consequentialActionsBlocked = 0
        )

        val line = controller.statusLine(observation)
        assertTrue(line.contains("277 safe controls seen"))
        assertFalse(line.contains("safe controls learned"))
    }
}
