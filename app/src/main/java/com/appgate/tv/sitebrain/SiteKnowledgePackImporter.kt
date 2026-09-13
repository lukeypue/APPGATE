package com.appgate.tv.sitebrain

import java.util.Base64

class SiteKnowledgePackImporter {
    fun mergeEncoded(encoded: String, local: SiteBrainState): SiteBrainState {
        val pack = parse(encoded)
        require(pack.schemaVersion == 1) { "Unsupported Site Brain pack schema ${pack.schemaVersion}" }
        require(pack.domain == local.host || local.host.endsWith(pack.domain) || pack.domain.endsWith(local.host)) {
            "Pack domain ${pack.domain} does not match ${local.host}"
        }

        val nodesById = local.nodes.associateBy { it.fingerprint }.toMutableMap()
        pack.nodes.forEach { remote ->
            val existing = nodesById[remote.id]
            if (existing == null) {
                nodesById[remote.id] = SiteNode(
                    fingerprint = remote.id,
                    routeSignature = remote.route,
                    pageType = mapPageType(remote.pageType),
                    title = remote.summary.take(160),
                    firstSeenAt = pack.generatedAt,
                    lastSeenAt = pack.generatedAt,
                    visitCount = 1
                )
            }
        }

        val edges = local.edges.toMutableList()
        pack.edges.filter { it.verified }.forEach { remote ->
            val candidate = SiteEdge(
                id = "pack:${remote.from}:${remote.to}:${remote.action}",
                fromFingerprint = remote.from,
                toFingerprint = remote.to,
                actionKind = mapAction(remote.action),
                semanticIntent = remote.action,
                label = remote.label,
                safetyClass = SafetyClass.SAFE,
                locatorHints = emptyList(),
                expectedPageType = null,
                observedPostcondition = "shared-pack-verified",
                confidence = remote.confidence.coerceIn(0.0, 1.0),
                successCount = 1,
                failureCount = 0,
                lastVerifiedAt = pack.generatedAt
            )
            val index = edges.indexOfFirst { it.fromFingerprint == candidate.fromFingerprint && it.semanticIntent == candidate.semanticIntent && it.label.equals(candidate.label, ignoreCase = true) }
            if (index < 0) edges += candidate
            else if (candidate.confidence > edges[index].confidence && (edges[index].lastVerifiedAt ?: 0L) <= pack.generatedAt) edges[index] = candidate
        }

        val remoteReadiness = runCatching { ReadinessLevel.valueOf(pack.readiness) }.getOrDefault(ReadinessLevel.LEARNING)
        val readiness = if (remoteReadiness.ordinal > local.readiness.ordinal) remoteReadiness else local.readiness
        return local.copy(
            readiness = readiness,
            nodes = nodesById.values.toList(),
            edges = edges,
            unresolvedBranches = maxOf(local.unresolvedBranches, pack.boundaries),
            coverageScore = maxOf(local.coverageScore, pack.coverage / 100.0),
            revision = local.revision + 1
        )
    }

    private data class Node(val id: String, val pageType: String, val route: String, val summary: String)
    private data class Edge(val from: String, val to: String, val action: String, val label: String, val confidence: Double, val verified: Boolean)
    private data class Parsed(val schemaVersion: Int, val domain: String, val generatedAt: Long, val coverage: Int, val readiness: String, val nodes: List<Node>, val edges: List<Edge>, val boundaries: Int)

    private fun parse(text: String): Parsed {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.isNotEmpty()) { "Empty Site Brain pack" }
        val h = lines.first().split('|')
        require(h.size == 6 && h[0] == "SITEBRAIN") { "Invalid Site Brain pack" }
        val nodes = mutableListOf<Node>()
        val edges = mutableListOf<Edge>()
        var boundaries = 0
        lines.drop(1).forEach { line ->
            val p = line.split('|')
            when (p[0]) {
                "NODE" -> nodes += Node(dec(p[1]), dec(p[2]), dec(p[3]), dec(p[4]))
                "EDGE" -> edges += Edge(dec(p[1]), dec(p[2]), dec(p[3]), dec(p[4]), p[5].toDouble(), p[6].toBoolean())
                "BOUNDARY" -> boundaries++
            }
        }
        return Parsed(h[1].toInt(), dec(h[2]), h[3].toLong(), h[4].toInt(), dec(h[5]), nodes, edges, boundaries)
    }

    private fun dec(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    private fun mapPageType(value: String): PageType = when (value.uppercase()) {
        "RESULTS", "RESULT_LIST" -> PageType.RESULT_LIST
        "HOME" -> PageType.HOME
        "SEARCH" -> PageType.SEARCH
        "CATEGORY" -> PageType.CATEGORY
        "DETAIL" -> PageType.DETAIL
        "FILTER", "FILTER_PANEL" -> PageType.FILTER_PANEL
        "PROFILE" -> PageType.PROFILE
        "LOGIN" -> PageType.LOGIN
        "CHALLENGE" -> PageType.CHALLENGE
        else -> PageType.UNKNOWN
    }
    private fun mapAction(value: String): ActionKind = runCatching { ActionKind.valueOf(value.uppercase()) }.getOrDefault(ActionKind.NAVIGATE)
}
