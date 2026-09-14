package com.appgate.tv

object LearningRuntimePolicy {
    const val maxActionsPerVisit: Int = 5000
    const val maxMinutesPerVisit: Long = 120L
    const val noProgressAutoSkipMs: Long = 30_000L

    fun stalledForMs(nowMs: Long, lastProgressAtMs: Long): Long =
        (nowMs - lastProgressAtMs).coerceAtLeast(0L)

    fun shouldAutoSkip(stalledForMs: Long, waitingForHuman: Boolean): Boolean {
        return stalledForMs >= noProgressAutoSkipMs
    }
}
