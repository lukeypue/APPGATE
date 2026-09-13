package com.appgate.tv.sitebrain

data class VerificationResult(
    val success: Boolean,
    val confidenceDelta: Double,
    val evidence: List<String>
)

object OutcomeVerifier {
    fun verify(before: PageSnapshot, action: SiteEdge, after: PageSnapshot): VerificationResult {
        val evidence = mutableListOf<String>()
        if (before.routeSignature != after.routeSignature) evidence += "route changed"
        if (before.pageType != after.pageType) evidence += "page type ${before.pageType} -> ${after.pageType}"
        if (before.headings.map(String::lowercase) != after.headings.map(String::lowercase)) evidence += "headings changed"
        if (selectedLabels(before) != selectedLabels(after)) evidence += "selected controls changed"
        if (before.fingerprint != after.fingerprint) evidence += "semantic page state changed"
        if (action.expectedPageType != null && after.pageType == action.expectedPageType) evidence += "expected page type reached"

        val meaningful = evidence.any {
            it == "route changed" || it.startsWith("page type") || it == "selected controls changed" || it == "expected page type reached"
        }
        return VerificationResult(
            success = meaningful,
            confidenceDelta = if (meaningful) 0.15 else -0.10,
            evidence = evidence
        )
    }

    private fun selectedLabels(snapshot: PageSnapshot): Set<String> = snapshot.elements
        .filter { it.selected }
        .map { it.label.lowercase().trim() }
        .filter(String::isNotBlank)
        .toSet()
}
