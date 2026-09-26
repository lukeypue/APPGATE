package com.appgate.tv

object LearningRuntimePolicy {
    const val maxActionsPerVisit: Int = 1200
    const val maxMinutesPerVisit: Long = 45L
    // Central floor for automatic site rotation. Individual recovery paths may request a move,
    // but none of them may churn through sites before the learner has had time to inspect one.
    const val minAutomaticSiteDwellMs: Long = 120_000L
    const val noProgressAutoSkipMs: Long = 90_000L
    // A WebView JavaScript callback can occasionally never return. The normal watchdog used to
    // ignore that state while pageSettling/actionInFlight was true, which could leave a run frozen
    // for hours. This wall-clock limit deliberately ignores those transient flags.
    const val hardFreezeRecoveryMs: Long = 180_000L
    const val maxLogEvents: Int = 8_000
    const val maxSameActionPerRoute: Int = 3
    const val logPersistEveryEvents: Int = 250
    const val logPersistIntervalMs: Long = 60_000L

    fun stalledForMs(nowMs: Long, lastProgressAtMs: Long): Long =
        (nowMs - lastProgressAtMs).coerceAtLeast(0L)

    fun shouldAutoSkip(stalledForMs: Long, waitingForHuman: Boolean): Boolean {
        return stalledForMs >= noProgressAutoSkipMs
    }

    fun shouldHardRecover(stalledForMs: Long, waitingForHuman: Boolean, authScreenOpen: Boolean): Boolean =
        !waitingForHuman && !authScreenOpen && stalledForMs >= hardFreezeRecoveryMs

    fun shouldTrySameRouteAction(previousAttempts: Int): Boolean =
        previousAttempts < maxSameActionPerRoute

    fun shouldPersistLog(eventsSinceLastPersist: Int, elapsedSinceLastPersistMs: Long): Boolean =
        eventsSinceLastPersist >= logPersistEveryEvents || elapsedSinceLastPersistMs >= logPersistIntervalMs
}
