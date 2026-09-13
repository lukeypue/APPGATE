package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteBrainModelsTest {
    @Test
    fun readinessOrdersFromUnmappedToDeepReady() {
        assertTrue(ReadinessLevel.DEEP_SEARCH_READY.ordinal > ReadinessLevel.SEARCH_READY.ordinal)
        assertTrue(ReadinessLevel.SEARCH_READY.ordinal > ReadinessLevel.LEARNING.ordinal)
    }

    @Test
    fun siteStateDefaultsToUnmapped() {
        val state = SiteBrainState.empty("example.com")
        assertEquals(ReadinessLevel.UNMAPPED, state.readiness)
        assertTrue(state.nodes.isEmpty())
        assertTrue(state.edges.isEmpty())
        assertEquals(0.0, state.coverageScore, 0.0)
    }
}
