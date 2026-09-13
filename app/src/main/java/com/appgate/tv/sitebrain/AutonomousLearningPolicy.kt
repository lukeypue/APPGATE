package com.appgate.tv.sitebrain

data class AutonomousLearningPolicy(
    val targetCoverage: Double,
    val maxActions: Int,
    val maxElapsedMs: Long,
    val maxBacktracks: Int
) {
    fun shouldContinue(coverage: Double, actionsTaken: Int, elapsedMs: Long): Boolean =
        coverage < targetCoverage && actionsTaken < maxActions && elapsedMs < maxElapsedMs

    companion object {
        fun forSource(sourceKey: String): AutonomousLearningPolicy = when (sourceKey) {
            "ksl_cars", "facebook_marketplace", "ebay" ->
                AutonomousLearningPolicy(targetCoverage = 1.0, maxActions = 60, maxElapsedMs = 300_000L, maxBacktracks = 12)
            else ->
                AutonomousLearningPolicy(targetCoverage = 1.0, maxActions = 30, maxElapsedMs = 180_000L, maxBacktracks = 8)
        }
    }
}
