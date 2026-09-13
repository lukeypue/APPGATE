package com.appgate.tv.sitebrain

class ContinuousLearningSession private constructor(
    val policy: AutonomousLearningPolicy
) {
    var actionsTaken: Int = 0
        private set

    fun recordAction() {
        if (actionsTaken < policy.maxActions) actionsTaken++
    }

    fun shouldContinue(coverage: Double, elapsedMs: Long): Boolean =
        policy.shouldContinue(
            coverage = coverage,
            actionsTaken = actionsTaken,
            elapsedMs = elapsedMs
        )

    companion object {
        fun forSource(sourceKey: String): ContinuousLearningSession =
            ContinuousLearningSession(AutonomousLearningPolicy.forSource(sourceKey))
    }
}
