package com.appgate.tv.sitebrain

object ActionRepairer {
    fun rankReplacementCandidates(expectedEdge: SiteEdge, currentSnapshot: PageSnapshot): List<SemanticElement> {
        return currentSnapshot.elements
            .filter { SafeActionClassifier.classify(it) == SafetyClass.SAFE }
            .map { it to score(expectedEdge, it) }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun score(edge: SiteEdge, element: SemanticElement): Double {
        val edgeTokens = tokens(edge.label + " " + edge.semanticIntent.replace('_', ' '))
        val candidateTokens = tokens(element.label + " " + element.nearbyText.orEmpty())
        val overlap = if (edgeTokens.isEmpty()) 0.0 else edgeTokens.intersect(candidateTokens).size.toDouble() / edgeTokens.size
        val inferred = SafeActionClassifier.inferActionKind(element)
        val kindBonus = if (inferred == edge.actionKind) 0.55 else if (compatible(edge.actionKind, inferred)) 0.25 else 0.0
        val roleBonus = if (element.role in setOf("button", "link", "tab")) 0.05 else 0.0
        return overlap + kindBonus + roleBonus
    }

    private fun compatible(expected: ActionKind, actual: ActionKind): Boolean = when (expected) {
        ActionKind.APPLY_FILTER -> actual == ActionKind.APPLY_FILTER || actual == ActionKind.EXPAND
        ActionKind.OPEN_CATEGORY -> actual == ActionKind.NAVIGATE || actual == ActionKind.OPEN_CATEGORY
        ActionKind.OPEN_DETAIL -> actual == ActionKind.NAVIGATE || actual == ActionKind.OPEN_DETAIL
        ActionKind.SEARCH -> actual == ActionKind.SEARCH || actual == ActionKind.SUBMIT_FORM
        else -> false
    }

    private fun tokens(value: String): Set<String> = value.lowercase()
        .replace("maximum", "max")
        .replace("up to", "max")
        .replace("highest", "max")
        .replace("minimum", "min")
        .replace(Regex("[^a-z0-9]+"), " ")
        .split(' ')
        .filter { it.length > 1 }
        .toSet()
}
