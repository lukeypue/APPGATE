package com.appgate.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningPlateauPolicyTest {
    @Test
    fun firstShallowPlateauDoesNotAbandonSite() {
        assertFalse(
            LearningPlateauPolicy.shouldMoveOn(
                consecutivePlateaus = 1,
                actionsThisSite = 1,
                verifiedThisSite = 0,
                elapsedMs = 20_000L
            )
        )
    }

    @Test
    fun repeatedPlateausAfterUsefulExplorationCanMoveOn() {
        assertTrue(
            LearningPlateauPolicy.shouldMoveOn(
                consecutivePlateaus = 4,
                actionsThisSite = 12,
                verifiedThisSite = 2,
                elapsedMs = 120_000L
            )
        )
    }

    @Test
    fun timeBudgetCanStillEndAStuckSite() {
        assertTrue(
            LearningPlateauPolicy.shouldMoveOn(
                consecutivePlateaus = 2,
                actionsThisSite = 6,
                verifiedThisSite = 0,
                elapsedMs = 10 * 60_000L
            )
        )
    }
}
