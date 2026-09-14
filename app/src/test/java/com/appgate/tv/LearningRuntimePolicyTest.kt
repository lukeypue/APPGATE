package com.appgate.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningRuntimePolicyTest {
    @Test
    fun overnightModeDoesNotStopAtOld150ActionLimit() {
        assertTrue(LearningRuntimePolicy.maxActionsPerVisit >= 2000)
    }

    @Test
    fun watchdogSkipsAfterThirtySecondsWithoutProgress() {
        assertFalse(LearningRuntimePolicy.shouldAutoSkip(29_999L, waitingForHuman = true))
        assertTrue(LearningRuntimePolicy.shouldAutoSkip(30_000L, waitingForHuman = true))
        assertTrue(LearningRuntimePolicy.shouldAutoSkip(30_000L, waitingForHuman = false))
    }

    @Test
    fun verifiedProgressResetsWatchdogClock() {
        val now = 100_000L
        val lastProgress = 95_000L
        assertEquals(5_000L, LearningRuntimePolicy.stalledForMs(now, lastProgress))
        assertFalse(LearningRuntimePolicy.shouldAutoSkip(LearningRuntimePolicy.stalledForMs(now, lastProgress), false))
    }
}
