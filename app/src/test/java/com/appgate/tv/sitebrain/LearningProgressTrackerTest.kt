package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningProgressTrackerTest {
    @Test
    fun resumesAtSavedSiteAndAccumulatesVerifiedDiscoveries() {
        val tracker = LearningProgressTracker(siteCount = 3, savedSiteIndex = 1, savedVerified = 7)
        assertEquals(1, tracker.siteIndex)
        tracker.recordVerified()
        tracker.nextSite()
        assertEquals(2, tracker.siteIndex)
        assertEquals(8, tracker.verifiedDiscoveries)
        assertTrue(tracker.snapshot().contains("\"siteIndex\":2"))
    }

    @Test
    fun wrapsAroundSoLearningCanContinueIndefinitely() {
        val tracker = LearningProgressTracker(siteCount = 2, savedSiteIndex = 1, savedVerified = 0)
        tracker.nextSite()
        assertEquals(0, tracker.siteIndex)
        assertEquals(1, tracker.completedPasses)
    }
}
