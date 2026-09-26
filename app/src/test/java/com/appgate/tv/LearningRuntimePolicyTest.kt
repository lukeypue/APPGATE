package com.appgate.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningRuntimePolicyTest {
    @Test fun overnightModeUsesBoundedActionBudgetForStability() { assertEquals(1200, LearningRuntimePolicy.maxActionsPerVisit) }

    @Test
    fun watchdogGivesDynamicSitesNinetySecondsWithoutProgress() {
        assertFalse(LearningRuntimePolicy.shouldAutoSkip(89_999L, waitingForHuman = true))
        assertTrue(LearningRuntimePolicy.shouldAutoSkip(90_000L, waitingForHuman = true))
        assertTrue(LearningRuntimePolicy.shouldAutoSkip(90_000L, waitingForHuman = false))
    }

    @Test fun verifiedProgressResetsWatchdogClock() {
        val now = 100_000L
        val lastProgress = 95_000L
        assertEquals(5_000L, LearningRuntimePolicy.stalledForMs(now, lastProgress))
        assertFalse(LearningRuntimePolicy.shouldAutoSkip(LearningRuntimePolicy.stalledForMs(now, lastProgress), false))
    }

    @Test fun overnightLogsAreBoundedToPreventMemoryGrowth() { assertEquals(8_000, LearningRuntimePolicy.maxLogEvents) }

    @Test fun repeatedRouteActionIsCappedSoExplorerBranchesOut() {
        assertTrue(LearningRuntimePolicy.shouldTrySameRouteAction(0))
        assertTrue(LearningRuntimePolicy.shouldTrySameRouteAction(2))
        assertFalse(LearningRuntimePolicy.shouldTrySameRouteAction(3))
    }

    @Test fun largeLogsArePersistedInBatchesInsteadOfRewrittenEveryEvent() {
        assertFalse(LearningRuntimePolicy.shouldPersistLog(1, 1_000L))
        assertFalse(LearningRuntimePolicy.shouldPersistLog(100, 1_000L))
        assertTrue(LearningRuntimePolicy.shouldPersistLog(250, 1_000L))
        assertTrue(LearningRuntimePolicy.shouldPersistLog(1, 60_000L))
    }
}
