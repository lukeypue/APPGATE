package com.appgate.sitebrainlab

data class ExplorerBudget(val maxActions: Int, val maxPages: Int, val maxElapsedMs: Long) {
    companion object { fun default() = ExplorerBudget(60, 120, 900_000L) }
}
data class LabControl(val id: String, val label: String, val safety: LabActionSafety)
data class LabObservation(val fingerprint: String, val pageType: String, val controls: List<LabControl>, val protectedBoundary: Boolean = false)
data class FrontierAction(val sourceFingerprint: String, val control: LabControl)
data class ExplorerState(
    val root: String,
    val visited: Set<String> = emptySet(),
    val frontier: List<FrontierAction> = emptyList(),
    val attemptedControlIds: Set<String> = emptySet(),
    val boundaries: List<PackBoundary> = emptyList(),
    val actionsTaken: Int = 0,
    val pagesSeen: Int = 0,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
    val budget: ExplorerBudget = ExplorerBudget.default(),
    val finished: Boolean = false
) { companion object { fun start(root: String) = ExplorerState(root = root) } }

object SiteExplorer {
    fun observe(state: ExplorerState, observation: LabObservation): ExplorerState {
        val boundary = if (observation.protectedBoundary) state.boundaries + PackBoundary(observation.fingerprint, "HUMAN_OR_AUTH_REQUIRED") else state.boundaries
        if (observation.fingerprint in state.visited) return state.copy(boundaries = boundary.distinct())
        val safe = observation.controls.filter { it.safety == LabActionSafety.SAFE }
            .map { FrontierAction(observation.fingerprint, it) }
        return state.copy(
            visited = state.visited + observation.fingerprint,
            frontier = state.frontier + safe,
            boundaries = boundary.distinct(),
            pagesSeen = state.pagesSeen + 1
        )
    }

    fun next(state: ExplorerState, nowMs: Long = System.currentTimeMillis()): FrontierAction? {
        if (state.actionsTaken >= state.budget.maxActions || state.pagesSeen >= state.budget.maxPages || nowMs - state.startedAtEpochMs >= state.budget.maxElapsedMs) return null
        return state.frontier.firstOrNull { it.control.id !in state.attemptedControlIds }
    }

    fun markAttempted(state: ExplorerState, controlId: String): ExplorerState = state.copy(
        attemptedControlIds = state.attemptedControlIds + controlId,
        actionsTaken = state.actionsTaken + 1,
        finished = state.actionsTaken + 1 >= state.budget.maxActions
    )
}
