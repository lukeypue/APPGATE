package com.appgate.tv.sitebrain

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

interface SiteBrainStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class SharedPreferencesSiteBrainStore(private val prefs: SharedPreferences) : SiteBrainStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
}

class SiteBrainRepository(private val store: SiteBrainStore) {
    fun load(host: String): SiteBrainState {
        val raw = store.get(key(host)) ?: return SiteBrainState.empty(host)
        return runCatching { decode(raw) }.getOrElse { SiteBrainState.empty(host) }
    }

    fun save(state: SiteBrainState) {
        store.put(key(state.host), encode(state).toString())
    }

    fun recordNode(snapshot: PageSnapshot): SiteBrainState {
        val current = load(snapshot.host)
        val now = System.currentTimeMillis()
        val existing = current.nodes.firstOrNull { it.fingerprint == snapshot.fingerprint }
        val nodes = if (existing == null) {
            current.nodes + SiteNode(snapshot.fingerprint, snapshot.routeSignature, snapshot.pageType, snapshot.title, now, now, 1)
        } else {
            current.nodes.map {
                if (it.fingerprint == snapshot.fingerprint) it.copy(lastSeenAt = now, visitCount = it.visitCount + 1, title = snapshot.title, pageType = snapshot.pageType)
                else it
            }
        }
        val updated = recalculate(current.copy(nodes = nodes, revision = current.revision + 1))
        save(updated)
        return updated
    }

    fun recordTransition(host: String, fromFingerprint: String, edge: SiteEdge, toFingerprint: String?): SiteBrainState {
        val current = load(host)
        val normalized = edge.copy(fromFingerprint = fromFingerprint, toFingerprint = toFingerprint)
        val edges = current.edges.filterNot { it.id == normalized.id } + normalized
        val updated = recalculate(current.copy(edges = edges, revision = current.revision + 1))
        save(updated)
        return updated
    }

    /**
     * Adds many discovered controls with one load/encode/save cycle.
     * The old discovery loop persisted the complete Site Brain once per control
     * (up to 120 times on a page), which could block Android's UI thread long
     * enough for the system to report "Browser isn't responding".
     */
    fun recordTransitions(host: String, edgesToAdd: List<SiteEdge>): SiteBrainState {
        if (edgesToAdd.isEmpty()) return load(host)
        val current = load(host)
        val byId = LinkedHashMap<String, SiteEdge>(current.edges.size + edgesToAdd.size)
        current.edges.forEach { byId[it.id] = it }
        var changed = false
        edgesToAdd.forEach { edge ->
            if (!byId.containsKey(edge.id)) {
                byId[edge.id] = edge
                changed = true
            }
        }
        if (!changed) return current
        val updated = recalculate(current.copy(edges = byId.values.toList(), revision = current.revision + 1))
        save(updated)
        return updated
    }

    fun markSuccess(host: String, edgeId: String, postcondition: String, toFingerprint: String?): SiteBrainState {
        val current = load(host)
        val now = System.currentTimeMillis()
        val edges = current.edges.map { edge ->
            if (edge.id != edgeId) edge else edge.copy(
                toFingerprint = toFingerprint ?: edge.toFingerprint,
                observedPostcondition = postcondition,
                successCount = edge.successCount + 1,
                confidence = (edge.confidence + 0.15).coerceAtMost(1.0),
                lastVerifiedAt = now
            )
        }
        val updated = recalculate(current.copy(edges = edges, revision = current.revision + 1))
        save(updated)
        return updated
    }

    fun markFailure(host: String, edgeId: String): SiteBrainState {
        val current = load(host)
        val edges = current.edges.map { edge ->
            if (edge.id != edgeId) edge else edge.copy(
                failureCount = edge.failureCount + 1,
                confidence = (edge.confidence - 0.2).coerceAtLeast(0.0)
            )
        }
        val updated = recalculate(current.copy(edges = edges, revision = current.revision + 1))
        save(updated)
        return updated
    }

    private fun recalculate(state: SiteBrainState): SiteBrainState {
        val verified = state.edges.filter { it.successCount > 0 && it.confidence >= 0.55 && it.safetyClass == SafetyClass.SAFE }
        val hasSearchFlow = verified.any { it.actionKind in setOf(ActionKind.SEARCH, ActionKind.OPEN_CATEGORY, ActionKind.APPLY_FILTER) } &&
            state.nodes.any { it.pageType == PageType.RESULT_LIST }
        val hasDetail = verified.any { it.actionKind == ActionKind.OPEN_DETAIL } && state.nodes.any { it.pageType == PageType.DETAIL }
        val hasAlternates = verified.groupBy { it.actionKind }.values.any { it.size >= 2 } || verified.count { it.actionKind == ActionKind.OPEN_CATEGORY } >= 2
        val readiness = when {
            hasSearchFlow && hasDetail && hasAlternates -> ReadinessLevel.DEEP_SEARCH_READY
            hasSearchFlow -> ReadinessLevel.SEARCH_READY
            state.nodes.isNotEmpty() || state.edges.isNotEmpty() -> ReadinessLevel.LEARNING
            else -> ReadinessLevel.UNMAPPED
        }
        val coverage = ((state.nodes.size.coerceAtMost(20) / 20.0) * 0.35 +
            (verified.size.coerceAtMost(20) / 20.0) * 0.45 +
            (if (hasDetail) 0.1 else 0.0) +
            (if (hasAlternates) 0.1 else 0.0)).coerceIn(0.0, 1.0)
        return state.copy(readiness = readiness, coverageScore = coverage)
    }

    private fun key(host: String) = "site_brain_v1_${host.lowercase()}"

    private fun encode(state: SiteBrainState): JSONObject = JSONObject().apply {
        put("host", state.host)
        put("readiness", state.readiness.name)
        put("unresolvedBranches", state.unresolvedBranches)
        put("coverageScore", state.coverageScore)
        put("revision", state.revision)
        put("nodes", JSONArray().apply { state.nodes.forEach { put(encodeNode(it)) } })
        put("edges", JSONArray().apply { state.edges.forEach { put(encodeEdge(it)) } })
    }

    private fun encodeNode(n: SiteNode) = JSONObject().apply {
        put("fingerprint", n.fingerprint); put("routeSignature", n.routeSignature); put("pageType", n.pageType.name)
        put("title", n.title); put("firstSeenAt", n.firstSeenAt); put("lastSeenAt", n.lastSeenAt); put("visitCount", n.visitCount)
    }

    private fun encodeEdge(e: SiteEdge) = JSONObject().apply {
        put("id", e.id); put("fromFingerprint", e.fromFingerprint); put("toFingerprint", e.toFingerprint)
        put("actionKind", e.actionKind.name); put("semanticIntent", e.semanticIntent); put("label", e.label)
        put("safetyClass", e.safetyClass.name); put("locatorHints", JSONArray(e.locatorHints))
        put("expectedPageType", e.expectedPageType?.name); put("observedPostcondition", e.observedPostcondition)
        put("confidence", e.confidence); put("successCount", e.successCount); put("failureCount", e.failureCount)
        put("lastVerifiedAt", e.lastVerifiedAt)
    }

    private fun decode(raw: String): SiteBrainState {
        val o = JSONObject(raw)
        val nodes = mutableListOf<SiteNode>()
        val nodeArray = o.optJSONArray("nodes") ?: JSONArray()
        for (i in 0 until nodeArray.length()) {
            val n = nodeArray.getJSONObject(i)
            nodes += SiteNode(n.getString("fingerprint"), n.getString("routeSignature"), enumValueOf(n.getString("pageType")), n.optString("title"), n.optLong("firstSeenAt"), n.optLong("lastSeenAt"), n.optInt("visitCount", 1))
        }
        val edges = mutableListOf<SiteEdge>()
        val edgeArray = o.optJSONArray("edges") ?: JSONArray()
        for (i in 0 until edgeArray.length()) {
            val e = edgeArray.getJSONObject(i)
            val hintsArray = e.optJSONArray("locatorHints") ?: JSONArray()
            val hints = (0 until hintsArray.length()).map { hintsArray.optString(it) }
            edges += SiteEdge(
                id = e.getString("id"), fromFingerprint = e.getString("fromFingerprint"), toFingerprint = e.optString("toFingerprint").takeIf { it.isNotBlank() && it != "null" },
                actionKind = enumValueOf(e.getString("actionKind")), semanticIntent = e.optString("semanticIntent"), label = e.optString("label"), safetyClass = enumValueOf(e.getString("safetyClass")),
                locatorHints = hints, expectedPageType = e.optString("expectedPageType").takeIf { it.isNotBlank() && it != "null" }?.let { enumValueOf<PageType>(it) },
                observedPostcondition = e.optString("observedPostcondition").takeIf { it.isNotBlank() && it != "null" }, confidence = e.optDouble("confidence", 0.0),
                successCount = e.optInt("successCount", 0), failureCount = e.optInt("failureCount", 0), lastVerifiedAt = if (e.isNull("lastVerifiedAt")) null else e.optLong("lastVerifiedAt")
            )
        }
        return SiteBrainState(
            host = o.getString("host"), readiness = enumValueOf(o.optString("readiness", ReadinessLevel.UNMAPPED.name)), nodes = nodes, edges = edges,
            unresolvedBranches = o.optInt("unresolvedBranches", 0), coverageScore = o.optDouble("coverageScore", 0.0), revision = o.optInt("revision", 1)
        )
    }
}
