package com.appgate.tv.sitebrain

import android.webkit.WebView
import org.json.JSONTokener

enum class SiteBrainControllerState {
    SEARCHING,
    EXPLORING,
    WAITING_FOR_HUMAN,
    VERIFYING,
    COMPLETE
}

data class SiteBrainObservation(
    val snapshot: PageSnapshot,
    val brain: SiteBrainState,
    val controllerState: SiteBrainControllerState,
    val safeActionsFound: Int,
    val consequentialActionsBlocked: Int
)

class WebViewSiteBrainController(private val repository: SiteBrainRepository) {
    var state: SiteBrainControllerState = SiteBrainControllerState.SEARCHING
        private set

    fun observe(webView: WebView, callback: (Result<SiteBrainObservation>) -> Unit) {
        webView.evaluateJavascript(SemanticPageSnapshot.javascript()) { raw ->
            runCatching {
                val decoded = decodeJsString(raw)
                val snapshot = SemanticPageSnapshot.parse(decoded)
                var brain = repository.recordNode(snapshot)
                val safe = snapshot.elements.filter { SafeActionClassifier.classify(it) == SafetyClass.SAFE }
                val consequential = snapshot.elements.count { SafeActionClassifier.classify(it) == SafetyClass.CONSEQUENTIAL }

                // Persist low-confidence semantic hypotheses. They become trusted only after a
                // later action is executed and OutcomeVerifier proves the postcondition.
                safe.take(120).forEach { element ->
                    val kind = SafeActionClassifier.inferActionKind(element)
                    if (kind == ActionKind.UNKNOWN || kind == ActionKind.LOGIN) return@forEach
                    val edgeId = edgeId(snapshot.fingerprint, kind, element.label, element.href)
                    if (brain.edges.none { it.id == edgeId }) {
                        val edge = SiteEdge(
                            id = edgeId,
                            fromFingerprint = snapshot.fingerprint,
                            toFingerprint = null,
                            actionKind = kind,
                            semanticIntent = semanticIntent(kind, element.label),
                            label = element.label,
                            safetyClass = SafetyClass.SAFE,
                            locatorHints = element.locatorHints,
                            expectedPageType = expectedPageType(kind),
                            observedPostcondition = null,
                            confidence = 0.20,
                            successCount = 0,
                            failureCount = 0,
                            lastVerifiedAt = null
                        )
                        brain = repository.recordTransition(snapshot.host, snapshot.fingerprint, edge, null)
                    }
                }

                state = if (snapshot.challengeDetected || snapshot.pageType == PageType.CHALLENGE || snapshot.pageType == PageType.LOGIN && snapshot.loginDetected) {
                    SiteBrainControllerState.WAITING_FOR_HUMAN
                } else {
                    SiteBrainControllerState.SEARCHING
                }
                SiteBrainObservation(snapshot, brain, state, safe.size, consequential)
            }.also(callback)
        }
    }

    fun markHumanResume() {
        state = SiteBrainControllerState.SEARCHING
    }

    fun statusLine(observation: SiteBrainObservation): String {
        val readiness = when (observation.brain.readiness) {
            ReadinessLevel.UNMAPPED -> "Unmapped"
            ReadinessLevel.LEARNING -> "Learning"
            ReadinessLevel.SEARCH_READY -> "Search Ready"
            ReadinessLevel.DEEP_SEARCH_READY -> "Deep Search Ready"
        }
        val percent = (observation.brain.coverageScore * 100).toInt().coerceIn(0, 100)
        return "Site Brain: $readiness · $percent% mapped · ${observation.safeActionsFound} safe controls learned"
    }

    private fun expectedPageType(kind: ActionKind): PageType? = when (kind) {
        ActionKind.SEARCH, ActionKind.APPLY_FILTER, ActionKind.SORT, ActionKind.PAGINATE -> PageType.RESULT_LIST
        ActionKind.OPEN_CATEGORY -> PageType.CATEGORY
        ActionKind.OPEN_DETAIL -> PageType.DETAIL
        else -> null
    }

    private fun semanticIntent(kind: ActionKind, label: String): String = when (kind) {
        ActionKind.SEARCH -> "SEARCH"
        ActionKind.OPEN_CATEGORY -> "OPEN_CATEGORY:${label.take(100)}"
        ActionKind.APPLY_FILTER -> "APPLY_FILTER:${label.take(100)}"
        ActionKind.SORT -> "SORT:${label.take(100)}"
        ActionKind.PAGINATE -> "PAGINATE:${label.take(100)}"
        ActionKind.OPEN_DETAIL -> "OPEN_DETAIL:${label.take(100)}"
        else -> "${kind.name}:${label.take(100)}"
    }

    private fun edgeId(fp: String, kind: ActionKind, label: String, href: String?): String {
        val raw = "$fp|${kind.name}|${label.lowercase().trim()}|${href.orEmpty().substringBefore('?')}"
        return raw.hashCode().toUInt().toString(16)
    }

    private fun decodeJsString(raw: String?): String {
        if (raw == null || raw == "null") return "{}"
        return try {
            val value = JSONTokener(raw).nextValue()
            if (value is String) value else value.toString()
        } catch (_: Exception) {
            raw.trim('"').replace("\\n", "\n").replace("\\\"", "\"")
        }
    }
}
