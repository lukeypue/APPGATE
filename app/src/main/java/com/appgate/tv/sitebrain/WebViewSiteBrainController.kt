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

data class PreparedExploration(
    val javascript: String,
    val edge: SiteEdge,
    val before: PageSnapshot,
    val semanticIntent: String
)

data class ExplorationVerification(
    val verification: VerificationResult,
    val after: PageSnapshot,
    val brain: SiteBrainState
)

class WebViewSiteBrainController(private val repository: SiteBrainRepository) {
    var state: SiteBrainControllerState = SiteBrainControllerState.SEARCHING
        private set

    private var pending: PreparedExploration? = null

    fun seedPriorityHost(host: String) {
        val seed = PrioritySiteSeeds.seedForHost(host) ?: return
        val current = repository.load(host)
        val knownIds = current.edges.map { it.id }.toSet()
        val missing = seed.edges.filterNot { it.id in knownIds }
        if (missing.isEmpty()) return
        repository.save(
            current.copy(
                readiness = if (current.readiness == ReadinessLevel.UNMAPPED) ReadinessLevel.LEARNING else current.readiness,
                edges = current.edges + missing,
                unresolvedBranches = maxOf(current.unresolvedBranches, seed.unresolvedBranches),
                coverageScore = maxOf(current.coverageScore, seed.coverageScore),
                revision = current.revision + 1
            )
        )
    }

    fun observe(webView: WebView, callback: (Result<SiteBrainObservation>) -> Unit) {
        capture(webView) { captureResult ->
            captureResult.map { snapshot ->
                seedPriorityHost(snapshot.host)
                var brain = repository.recordNode(snapshot)
                val safe = snapshot.elements.filter { SafeActionClassifier.classify(it) == SafetyClass.SAFE }
                val consequential = snapshot.elements.count { SafeActionClassifier.classify(it) == SafetyClass.CONSEQUENTIAL }

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

                state = if (requiresHuman(snapshot)) {
                    SiteBrainControllerState.WAITING_FOR_HUMAN
                } else {
                    SiteBrainControllerState.SEARCHING
                }
                SiteBrainObservation(snapshot, brain, state, safe.size, consequential)
            }.also(callback)
        }
    }

    fun prepareExploration(
        observation: SiteBrainObservation,
        budget: ExplorerBudget,
        query: String?
    ): PreparedExploration? {
        if (pending != null) return null
        val decision = SiteExplorer.nextAction(observation.brain, observation.snapshot, budget)
        if (decision is ExplorerDecision.WaitForHuman) {
            state = SiteBrainControllerState.WAITING_FOR_HUMAN
            return null
        }
        if (decision is ExplorerDecision.Stop) {
            state = SiteBrainControllerState.COMPLETE
            return null
        }
        decision as ExplorerDecision.Act
        val js = SafeActionExecutor.javascriptFor(
            decision.element,
            decision.actionKind,
            query,
            observation.snapshot.host
        ) ?: return null
        val id = edgeId(observation.snapshot.fingerprint, decision.actionKind, decision.element.label, decision.element.href)
        val edge = observation.brain.edges.firstOrNull { it.id == id } ?: SiteEdge(
            id = id,
            fromFingerprint = observation.snapshot.fingerprint,
            toFingerprint = null,
            actionKind = decision.actionKind,
            semanticIntent = decision.semanticIntent,
            label = decision.element.label,
            safetyClass = SafetyClass.SAFE,
            locatorHints = decision.element.locatorHints,
            expectedPageType = expectedPageType(decision.actionKind),
            observedPostcondition = null,
            confidence = 0.20,
            successCount = 0,
            failureCount = 0,
            lastVerifiedAt = null
        )
        repository.recordTransition(observation.snapshot.host, observation.snapshot.fingerprint, edge, null)
        return PreparedExploration(js, edge, observation.snapshot, decision.semanticIntent).also {
            pending = it
            state = SiteBrainControllerState.EXPLORING
        }
    }

    fun executePrepared(webView: WebView, prepared: PreparedExploration, callback: (Boolean) -> Unit) {
        if (pending?.edge?.id != prepared.edge.id) {
            callback(false)
            return
        }
        webView.evaluateJavascript(prepared.javascript) { raw ->
            val response = decodeJsString(raw).uppercase()
            val accepted = response.contains("CLICKED") || response.contains("FILLED") || response.contains("NAVIGATE") || prepared.javascript.startsWith("window.location.href=")
            if (!accepted) {
                repository.markFailure(prepared.before.host, prepared.edge.id)
                pending = null
                state = SiteBrainControllerState.SEARCHING
            } else {
                state = SiteBrainControllerState.VERIFYING
            }
            callback(accepted)
        }
    }

    fun hasPendingExploration(): Boolean = pending != null

    fun verifyPending(webView: WebView, callback: (Result<ExplorationVerification>) -> Unit) {
        val active = pending ?: run {
            callback(Result.failure(IllegalStateException("No pending exploration")))
            return
        }
        state = SiteBrainControllerState.VERIFYING
        capture(webView) { result ->
            result.map { after ->
                repository.recordNode(after)
                if (requiresHuman(after)) {
                    val brain = repository.load(active.before.host)
                    val verification = VerificationResult(
                        success = false,
                        confidenceDelta = 0.0,
                        evidence = listOf("human verification required")
                    )
                    pending = null
                    state = SiteBrainControllerState.WAITING_FOR_HUMAN
                    return@map ExplorationVerification(verification, after, brain)
                }

                val verification = OutcomeVerifier.verify(active.before, active.edge, after)
                val brain = if (verification.success) {
                    repository.markSuccess(
                        active.before.host,
                        active.edge.id,
                        verification.evidence.joinToString("; ").ifBlank { "verified semantic state change" },
                        after.fingerprint
                    )
                } else {
                    repository.markFailure(active.before.host, active.edge.id)
                }
                pending = null
                state = SiteBrainControllerState.SEARCHING
                ExplorationVerification(verification, after, brain)
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

    private fun capture(webView: WebView, callback: (Result<PageSnapshot>) -> Unit) {
        webView.evaluateJavascript(SemanticPageSnapshot.javascript()) { raw ->
            runCatching {
                val decoded = decodeJsString(raw)
                SemanticPageSnapshot.parse(decoded)
            }.also(callback)
        }
    }

    private fun requiresHuman(snapshot: PageSnapshot): Boolean =
        snapshot.challengeDetected || snapshot.pageType == PageType.CHALLENGE ||
            (snapshot.pageType == PageType.LOGIN && snapshot.loginDetected)

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
