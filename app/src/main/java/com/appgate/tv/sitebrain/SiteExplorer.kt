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

        // SiteBrainState visitCount is lifetime knowledge, not this run's revisit count.
        // Do not abandon a well-known state merely because it has been observed in earlier app versions.
        val learnedOutcomes = state.edges
            .filter { it.fromFingerprint == snapshot.fingerprint && it.successCount + it.failureCount > 0 }
            .associateBy({ normalize(it.label) to it.actionKind }, { it })

        // Also transfer generic skills across page fingerprints on the same host. Dynamic sites
        // frequently change fingerprints even though controls such as Search, Price, Next and
        // Details still mean the same thing. Prefer a proven semantic route when the local page
        // has not learned one yet.
        // Cross-page transfer is deliberately positive-only. A failure is often caused by
        // a local page binding (modal, stale control, different layout) and must not poison the
        // same semantic skill everywhere else on a reactive site. Only verifier-confirmed SAFE
        // successes are allowed to become transferable host skills.
        val transferableOutcomes = state.edges
            .filter {
                it.safetyClass == SafetyClass.SAFE &&
                    it.successCount > 0 &&
                    it.successCount >= it.failureCount &&
                    it.confidence >= 0.55
            }
            .groupBy { normalize(it.label) to it.actionKind }
            .mapValues { (_, edges) ->
                edges.maxWithOrNull(
                    compareBy<SiteEdge> { it.successCount - it.failureCount }
                        .thenBy { it.confidence }
                        .thenBy { it.lastVerifiedAt ?: 0L }
                )
            }

        val candidates = snapshot.elements.asSequence()
            .filter { SafeActionClassifier.classify(it) == SafetyClass.SAFE }
            .map { element ->
                val kind = SafeActionClassifier.inferActionKind(element)
                val key = normalize(element.label) to kind
                val learned = learnedOutcomes[key] ?: transferableOutcomes[key]
                Triple(element, kind, score(element, kind, learned, snapshot.pageType))
            }
            .filter { it.second != ActionKind.UNKNOWN && it.third > 0 }
            .sortedByDescending { it.third }
            .toList()

        val best = candidates.firstOrNull() ?: return ExplorerDecision.Stop("No safe unexplored actions")
        val intent = semanticIntent(best.second, best.first)
        return ExplorerDecision.Act(best.first, best.second, intent)
    }

    private fun score(element: SemanticElement, kind: ActionKind, learned: SiteEdge?, pageType: PageType): Int {
        // New controls deserve exploration, but a route that has already been verified should
        // become a reusable skill rather than being treated as "already tried" and avoided.
        var score = when {
            learned == null -> 100
            learned.successCount > 0 && learned.successCount >= learned.failureCount -> 135 + (learned.successCount.coerceAtMost(10) * 4)
            else -> -40 - (learned.failureCount.coerceAtMost(10) * 5)
        }
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
        // Prefer actions that advance the generic search workflow instead of repeatedly
        // clicking high-priority controls that do not make sense for the current page type.
        score += when (pageType) {
            PageType.HOME, PageType.UNKNOWN -> if (kind in setOf(ActionKind.SEARCH, ActionKind.OPEN_CATEGORY)) 30 else 0
            PageType.CATEGORY -> if (kind in setOf(ActionKind.SEARCH, ActionKind.APPLY_FILTER, ActionKind.OPEN_DETAIL)) 30 else 0
            PageType.RESULT_LIST -> if (kind in setOf(ActionKind.APPLY_FILTER, ActionKind.SORT, ActionKind.OPEN_DETAIL, ActionKind.PAGINATE)) 35 else if (kind == ActionKind.SEARCH) -15 else 0
            PageType.DETAIL -> if (kind in setOf(ActionKind.EXPAND, ActionKind.BACK)) 35 else if (kind == ActionKind.SEARCH) -25 else 0
            PageType.LOGIN, PageType.CHALLENGE -> -200
            PageType.SEARCH -> if (kind in setOf(ActionKind.SEARCH, ActionKind.APPLY_FILTER, ActionKind.OPEN_DETAIL)) 30 else 0
            PageType.FILTER_PANEL -> if (kind == ActionKind.APPLY_FILTER) 40 else 0
            PageType.PROFILE -> if (kind == ActionKind.BACK) 25 else -10
        }
        val text = normalize(element.label + " " + element.nearbyText.orEmpty())
        if (listOf("privacy", "terms", "help", "about", "careers", "advertise", "cookie").any(text::contains)) score -= 140
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
