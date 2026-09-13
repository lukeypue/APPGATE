package com.appgate.tv.sitebrain.trainer

import com.appgate.tv.sitebrain.ActionKind
import com.appgate.tv.sitebrain.PageType
import com.appgate.tv.sitebrain.SafetyClass
import com.appgate.tv.sitebrain.SiteBrainState
import com.appgate.tv.sitebrain.SiteEdge

/** Coverage is earned only by verified, safe structural behavior. Seeing a control is not enough. */
data class TrainerCoverageReport(
    val percent: Int,
    val criticalMissing: List<String>,
    val protectedBoundaries: List<String>,
    val targetReached: Boolean
)

object TrainerCoverage {
    private val optionalCapabilities = linkedMapOf(
        ActionKind.OPEN_CATEGORY to "CATEGORY",
        ActionKind.APPLY_FILTER to "FILTER",
        ActionKind.SORT to "SORT",
        ActionKind.PAGINATE to "PAGINATION"
    )

    fun evaluate(brain: SiteBrainState): TrainerCoverageReport {
        val verified = brain.edges.filter(::isVerifiedSafe)
        val discoveredSafe = brain.edges.filter { it.safetyClass == SafetyClass.SAFE }

        val hasSearch = verified.any { it.actionKind == ActionKind.SEARCH }
        val hasResults = brain.nodes.any { it.pageType == PageType.RESULT_LIST } &&
            verified.any { edge -> edge.toFingerprint?.let { fp -> brain.nodes.any { it.fingerprint == fp && it.pageType == PageType.RESULT_LIST } } == true }
        val hasDetail = brain.nodes.any { it.pageType == PageType.DETAIL } && verified.any { it.actionKind == ActionKind.OPEN_DETAIL }
        val hasReturn = verified.any { edge ->
            edge.actionKind == ActionKind.BACK && edge.toFingerprint?.let { fp ->
                brain.nodes.any { it.fingerprint == fp && it.pageType == PageType.RESULT_LIST }
            } == true
        }

        val missing = mutableListOf<String>()
        if (!hasSearch) missing += "SEARCH"
        if (!hasResults) missing += "RESULTS"
        if (!hasDetail) missing += "DETAIL"
        if (!hasReturn) missing += "BACK_TO_RESULTS"

        optionalCapabilities.forEach { (kind, name) ->
            val discovered = discoveredSafe.any { it.actionKind == kind }
            val proven = verified.any { it.actionKind == kind }
            if (discovered && !proven) missing += name
        }

        val protected = buildList {
            brain.nodes.filter { it.pageType == PageType.LOGIN || it.pageType == PageType.CHALLENGE }
                .forEach { add("${it.pageType.name}:${it.routeSignature}") }
            brain.edges.filter { it.safetyClass != SafetyClass.SAFE }
                .forEach { add("${it.safetyClass.name}:${it.semanticIntent}") }
        }.distinct()

        // Critical funnel carries most of the score. Optional capabilities become part of the
        // denominator only after the site reveals them, so newly discovered unverified structure
        // can lower coverage rather than creating a false sense of completion.
        val required = mutableListOf(
            "SEARCH" to hasSearch,
            "RESULTS" to hasResults,
            "DETAIL" to hasDetail,
            "BACK_TO_RESULTS" to hasReturn
        )
        optionalCapabilities.forEach { (kind, name) ->
            if (discoveredSafe.any { it.actionKind == kind }) {
                required += name to verified.any { it.actionKind == kind }
            }
        }

        val verifiedCount = required.count { it.second }
        var percent = if (required.isEmpty()) 0 else ((verifiedCount * 100.0) / required.size).toInt()

        // Unresolved safe branches are real discovered work. Penalize them so 95% cannot be
        // achieved by verifying a tiny happy path while leaving a large frontier untouched.
        val unresolvedSafe = discoveredSafe.count { !isVerifiedSafe(it) }
        if (unresolvedSafe > 0) percent = (percent - unresolvedSafe.coerceAtMost(20) * 5).coerceAtLeast(0)
        percent = percent.coerceIn(0, 100)

        val targetReached = percent >= 95 && missing.isEmpty()
        return TrainerCoverageReport(percent, missing.distinct(), protected, targetReached)
    }

    private fun isVerifiedSafe(edge: SiteEdge): Boolean =
        edge.safetyClass == SafetyClass.SAFE &&
            edge.successCount > 0 &&
            edge.confidence >= 0.55 &&
            edge.lastVerifiedAt != null &&
            edge.toFingerprint != null
}
