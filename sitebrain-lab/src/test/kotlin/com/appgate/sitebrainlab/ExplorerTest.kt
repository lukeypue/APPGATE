package com.appgate.sitebrainlab

import org.junit.Assert.*
import org.junit.Test

class ExplorerTest {
    @Test fun exploresBreadthFirstAndDeduplicates() {
        var state = ExplorerState.start("home")
        state = SiteExplorer.observe(state, LabObservation("home", "HOME", listOf(LabControl("cars", "Cars", LabActionSafety.SAFE), LabControl("search", "Search", LabActionSafety.SAFE))))
        assertEquals("cars", SiteExplorer.next(state)!!.control.id)
        state = SiteExplorer.markAttempted(state, "cars")
        assertEquals("search", SiteExplorer.next(state)!!.control.id)
    }

    @Test fun protectedBoundaryDoesNotKillOtherFrontierWork() {
        var state = ExplorerState.start("home")
        state = SiteExplorer.observe(state, LabObservation("login", "LOGIN", emptyList(), protectedBoundary = true))
        assertTrue(state.boundaries.any { it.reason == "HUMAN_OR_AUTH_REQUIRED" })
        assertFalse(state.finished)
    }

    @Test fun defaultBudgetIsFiniteButLargeEnoughToLearn() {
        val b = ExplorerBudget.default()
        assertTrue(b.maxActions >= 60)
        assertTrue(b.maxPages >= 120)
        assertTrue(b.maxElapsedMs >= 900_000L)
    }
}
