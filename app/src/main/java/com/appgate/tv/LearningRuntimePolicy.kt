package com.appgate.tv

object LearningRuntimePolicy {
    const val maxActionsPerVisit: Int = 5000
    const val maxMinutesPerVisit: Long = 120L
    const val noProgressAutoSkipMs: Long = 30_000L
    const val maxLogEvents: Int = 50_000
    const val maxSameActionPerRoute: Int = 3
    const val logPersistEveryEvents: Int = 100
    const val logPersistIntervalMs: Long = 15_000L

    fun stalledForMs(nowMs: Long, lastProgressAtMs: Long): Long =
        (nowMs - lastProgressAtMs).coerceAtLeast(0L)

    fun shouldAutoSkip(stalledForMs: Long, waitingForHuman: Boolean): Boolean {
        return stalledForMs >= noProgressAutoSkipMs
    }

    fun shouldTrySameRouteAction(previousAttempts: Int): Boolean =
        previousAttempts < maxSameActionPerRoute

    fun shouldPersistLog(eventsSinceLastPersist: Int, elapsedSinceLastPersistMs: Long): Boolean =
        eventsSinceLastPersist >= logPersistEveryEvents || elapsedSinceLastPersistMs >= logPersistIntervalMs
}
