package com.appgate.tv

object LearningPlateauPolicy {
    private const val MIN_USEFUL_ACTIONS = 5
    private const val REQUIRED_PLATEAUS = 8
    private const val HARD_PLATEAU_CAP = 12
    private const val STUCK_TIMEOUT_MS = 3 * 60_000L

    fun shouldMoveOn(
        consecutivePlateaus: Int,
        actionsThisSite: Int,
        verifiedThisSite: Int,
        elapsedMs: Long
    ): Boolean {
        if (consecutivePlateaus >= HARD_PLATEAU_CAP) return true
        if (elapsedMs >= STUCK_TIMEOUT_MS && consecutivePlateaus >= 2) return true
        if (consecutivePlateaus < REQUIRED_PLATEAUS) return false
        return actionsThisSite >= MIN_USEFUL_ACTIONS || verifiedThisSite > 0
    }
}
