package com.appgate.tv.sitebrain

import com.appgate.tv.ParsedSearch

data class SearchPath(
    val host: String,
    val edgeId: String,
    val semanticIntent: String,
    val label: String,
    val confidence: Double,
    val fromFingerprint: String,
    val expectedPageType: PageType?,
    val requiresDeepVerification: Boolean = false
)

data class SearchWave(
    val index: Int,
    val name: String,
    val paths: List<SearchPath>,
    val estimatedCoverage: Double,
    val unresolvedBranches: Int
)

object CentralSearchBrain {
    fun plan(parsed: ParsedSearch, siteBrains: List<SiteBrainState>): List<SearchWave> {
        val queryTerms = (parsed.conceptTerms + parsed.discoveredVocabulary + parsed.optionalTerms.flatMap(::tokens))
            .map { it.lowercase() }
            .toSet()
        val wave1 = mutableListOf<SearchPath>()
        val wave2 = mutableListOf<SearchPath>()
        val wave3 = mutableListOf<SearchPath>()

        siteBrains.forEach { brain ->
            val safeEdges = brain.edges
                .filter { it.safetyClass == SafetyClass.SAFE && it.successCount > 0 && it.confidence >= 0.45 }
                .sortedByDescending { it.confidence }

            safeEdges.forEachIndexed { index, edge ->
                val score = relevance(edge, queryTerms)
                val path = SearchPath(
                    host = brain.host,
                    edgeId = edge.id,
                    semanticIntent = edge.semanticIntent,
                    label = edge.label,
                    confidence = edge.confidence,
                    fromFingerprint = edge.fromFingerprint,
                    expectedPageType = edge.expectedPageType,
                    requiresDeepVerification = false
                )
                val primary = edge.confidence >= 0.72 && edge.actionKind in setOf(ActionKind.SEARCH, ActionKind.OPEN_CATEGORY, ActionKind.APPLY_FILTER)
                when {
                    primary && (score > 0.0 || index == 0) -> wave1 += path
                    score > 0.0 || edge.actionKind in setOf(ActionKind.OPEN_CATEGORY, ActionKind.APPLY_FILTER, ActionKind.PAGINATE) -> wave2 += path
                }
            }

            if (parsed.requiredTerms.isNotEmpty()) {
                safeEdges.filter { it.actionKind == ActionKind.OPEN_DETAIL }.take(12).forEach { edge ->
                    wave3 += SearchPath(
                        host = brain.host,
                        edgeId = edge.id,
                        semanticIntent = edge.semanticIntent,
                        label = edge.label,
                        confidence = edge.confidence,
                        fromFingerprint = edge.fromFingerprint,
                        expectedPageType = edge.expectedPageType,
                        requiresDeepVerification = true
                    )
                }
            }
        }

        fun dedupe(paths: List<SearchPath>): List<SearchPath> = paths.distinctBy { "${it.host}|${it.edgeId}" }
        val totalCoverage = if (siteBrains.isEmpty()) 0.0 else siteBrains.map { it.coverageScore }.average()
        val unresolved = siteBrains.sumOf { it.unresolvedBranches }
        val waves = mutableListOf<SearchWave>()
        val first = dedupe(wave1)
        val second = dedupe(wave2).filterNot { p -> first.any { it.host == p.host && it.edgeId == p.edgeId } }
        val third = dedupe(wave3)
        if (first.isNotEmpty()) waves += SearchWave(1, "Fast known paths", first, totalCoverage.coerceAtMost(.65), unresolved)
        if (second.isNotEmpty()) waves += SearchWave(2, "Alternate paths", second, totalCoverage.coerceAtMost(.9), unresolved)
        if (third.isNotEmpty()) waves += SearchWave(3, "Deep verification", third, totalCoverage, unresolved)
        return waves
    }

    private fun relevance(edge: SiteEdge, terms: Set<String>): Double {
        if (terms.isEmpty()) return 0.0
        val edgeTerms = tokens(edge.semanticIntent + " " + edge.label).toSet()
        return edgeTerms.intersect(terms).size.toDouble() / terms.size
    }

    private fun tokens(value: String): List<String> = value.lowercase()
        .replace(Regex("[^a-z0-9.]+"), " ")
        .split(' ')
        .filter { it.length > 1 }
}
