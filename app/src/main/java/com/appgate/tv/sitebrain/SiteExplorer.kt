package com.appgate.tv.sitebrain

data class ExplorerBudget(
    val maxPages: Int = 30,
    val maxActions: Int = 60,
    val maxRevisitsPerFingerprint: Int = 2,
    val maxElapsedMs: Long = 120_000L,
    val pagesSeen: Int = 0,
    val actionsTaken: Int = 0,
    val startedAt: Long = System.currentTimeMillis()
)

sealed class ExplorerDecision {
    data class Act(val element: SemanticElement, val actionKind: ActionKind, val semanticIntent: String) : ExplorerDecision()
    data class Stop(val reason: String) : ExplorerDecision()
    data class WaitForHuman(val reason: String) : ExplorerDecision()
}

object SiteExplorer {
    fun nextAction(state: SiteBrainState, snapshot: PageSnapshot, budget: ExplorerBudget): ExplorerDecision {
        if (snapshot.challengeDetected || snapshot.loginDetected && snapshot.pageType == PageType.LOGIN) {
            return ExplorerDecision.WaitForHuman("Login or human verification required")
        }
        if (budget.pagesSeen >= budget.maxPages) return ExplorerDecision.Stop("Page budget reached")
        if (budget.actionsTaken >= budget.maxActions) return ExplorerDecision.Stop("Action budget reached")
        if (System.currentTimeMillis() - budget.startedAt >= budget.maxElapsedMs) return ExplorerDecision.Stop("Time budget reached")

        val currentVisits = state.nodes.firstOrNull { it.fingerprint == snapshot.fingerprint }?.visitCount ?: 0
        if (currentVisits > budget.maxRevisitsPerFingerprint) return ExplorerDecision.Stop("Revisit budget reached")

        val alreadyTried = state.edges
            .filter { it.fromFingerprint == snapshot.fingerprint && it.successCount + it.failureCount > 0 }
            .map { normalize(it.label) to it.actionKind }
            .toSet()

        val candidates = snapshot.elements.asSequence()
            .filter { SafeActionClassifier.classify(it) == SafetyClass.SAFE }
            .map { element ->
                val kind = SafeActionClassifier.inferActionKind(element)
                val key = normalize(element.label) to kind
                Triple(element, kind, score(element, kind, key !in alreadyTried))
            }
            .filter { it.second != ActionKind.UNKNOWN && it.third > 0 }
            .sortedByDescending { it.third }
            .toList()

        val best = candidates.firstOrNull() ?: return ExplorerDecision.Stop("No safe unexplored actions")
        val intent = semanticIntent(best.second, best.first)
        return ExplorerDecision.Act(best.first, best.second, intent)
    }

    private fun score(element: SemanticElement, kind: ActionKind, unexplored: Boolean): Int {
        var score = if (unexplored) 100 else 0
        score += when (kind) {
            ActionKind.SEARCH -> 90
            ActionKind.OPEN_CATEGORY -> 80
            ActionKind.APPLY_FILTER -> 75
            ActionKind.OPEN_DETAIL -> 65
            ActionKind.PAGINATE -> 55
            ActionKind.SORT -> 45
            ActionKind.OPEN_TAB -> 40
            ActionKind.NAVIGATE -> 35
            ActionKind.EXPAND -> 25
            ActionKind.BACK -> 10
            else -> 0
        }
        val text = normalize(element.label + " " + element.nearbyText.orEmpty())
        if (listOf("privacy", "terms", "help", "about", "careers", "advertise", "cookie").any(text::contains)) score -= 120
        if (element.href?.contains("#") == true) score -= 10
        return score
    }

    private fun semanticIntent(kind: ActionKind, element: SemanticElement): String {
        val label = element.label.trim().replace(Regex("\\s+"), " ").take(100)
        return when (kind) {
            ActionKind.SEARCH -> "SEARCH"
            ActionKind.OPEN_CATEGORY -> "OPEN_CATEGORY:$label"
            ActionKind.APPLY_FILTER -> "APPLY_FILTER:$label"
            ActionKind.SORT -> "SORT:$label"
            ActionKind.PAGINATE -> "PAGINATE:$label"
            ActionKind.OPEN_DETAIL -> "OPEN_DETAIL:$label"
            ActionKind.OPEN_TAB -> "OPEN_TAB:$label"
            ActionKind.EXPAND -> "EXPAND:$label"
            ActionKind.BACK -> "BACK"
            else -> "NAVIGATE:$label"
        }
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("\\s+"), " ").trim()
}
