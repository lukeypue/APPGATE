package com.appgate.tv.sitebrain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousLearningSessionTest {
    @Test
    fun allowsMultipleSafeActionsBeforeFiniteBudgetStopsLearning() {
        val session = ContinuousLearningSession.forSource("ebay")

        assertTrue(session.shouldContinue(coverage = 0.15, elapsedMs = 5_000L))
        repeat(5) { session.recordAction() }
        assertTrue(session.shouldContinue(coverage = 0.25, elapsedMs = 30_000L))

        repeat(session.policy.maxActions - session.actionsTaken) { session.recordAction() }
        assertFalse(session.shouldContinue(coverage = 0.70, elapsedMs = 60_000L))
    }

    @Test
    fun fullCoverageStopsEvenWhenBudgetRemains() {
        val session = ContinuousLearningSession.forSource("ksl_cars")
        session.recordAction()
        assertFalse(session.shouldContinue(coverage = 1.0, elapsedMs = 10_000L))
    }
}
