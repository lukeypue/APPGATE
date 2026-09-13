package com.appgate.tv.sitebrain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousLearningPolicyTest {
    @Test
    fun priorityMarketplacesGetDeepAutonomousLearningBudget() {
        val ksl = AutonomousLearningPolicy.forSource("ksl_cars")
        val facebook = AutonomousLearningPolicy.forSource("facebook_marketplace")
        val ebay = AutonomousLearningPolicy.forSource("ebay")

        listOf(ksl, facebook, ebay).forEach { policy ->
            assertEquals(1.0, policy.targetCoverage, 0.0)
            assertTrue(policy.maxActions >= 40)
            assertTrue(policy.maxElapsedMs >= 180_000L)
        }
    }

    @Test
    fun learningContinuesUntilCoverageTargetOrBudgetIsReached() {
        val policy = AutonomousLearningPolicy.forSource("ebay")
        assertTrue(policy.shouldContinue(coverage = 0.31, actionsTaken = 5, elapsedMs = 30_000L))
        assertTrue(!policy.shouldContinue(coverage = 1.0, actionsTaken = 5, elapsedMs = 30_000L))
        assertTrue(!policy.shouldContinue(coverage = 0.8, actionsTaken = policy.maxActions, elapsedMs = 30_000L))
    }
}
