package com.appgate.tv.sitebrain

data class VerificationResult(
    val success: Boolean,
    val confidenceDelta: Double,
    val evidence: List<String>
)

object OutcomeVerifier {
    fun verify(before: PageSnapshot, action: SiteEdge, after: PageSnapshot): VerificationResult {
        val evidence = mutableListOf<String>()
        val routeChanged = before.routeSignature != after.routeSignature
        val pageTypeChanged = before.pageType != after.pageType
        val selectedChanged = selectedLabels(before) != selectedLabels(after)
        val semanticStateChanged = before.fingerprint != after.fingerprint

        if (routeChanged) evidence += "route changed"
        if (pageTypeChanged) evidence += "page type ${before.pageType} -> ${after.pageType}"
        if (before.headings.map { it.lowercase() } != after.headings.map { it.lowercase() }) evidence += "headings changed"
        if (selectedChanged) evidence += "selected controls changed"
        if (semanticStateChanged) evidence += "semantic page state changed"

        // Merely already being on the expected page type does not prove a click worked.
        // The expected page type counts only when the action also produced a meaningful state transition.
        if (action.expectedPageType != null && after.pageType == action.expectedPageType &&
            (routeChanged || pageTypeChanged || selectedChanged || semanticStateChanged)) {
            evidence += "expected page type reached"
        }

        val meaningful = routeChanged || pageTypeChanged || selectedChanged ||
            (semanticStateChanged && evidence.any { it == "expected page type reached" })
        return VerificationResult(
            success = meaningful,
            confidenceDelta = if (meaningful) 0.15 else -0.10,
            evidence = evidence
        )
    }

    private fun selectedLabels(snapshot: PageSnapshot): Set<String> = snapshot.elements
        .filter { it.selected }
        .map { it.label.lowercase().trim() }
        .filter { it.isNotBlank() }
        .toSet()
}
