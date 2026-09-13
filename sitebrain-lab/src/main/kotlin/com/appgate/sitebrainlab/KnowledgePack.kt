package com.appgate.sitebrainlab

import java.util.Base64

data class PackNode(val id: String, val pageType: String, val route: String, val summary: String)
data class PackTransition(
    val fromId: String,
    val toId: String,
    val actionKind: String,
    val label: String,
    val confidence: Double,
    val verified: Boolean
)
data class PackBoundary(val route: String, val reason: String)

data class SiteKnowledgePack(
    val schemaVersion: Int,
    val domain: String,
    val hostAliases: List<String>,
    val generatedAtEpochMs: Long,
    val coveragePercent: Int,
    val readiness: String,
    val nodes: List<PackNode>,
    val transitions: List<PackTransition>,
    val boundaries: List<PackBoundary>
)

/**
 * Small deterministic line codec. Fields are URL-safe base64 encoded so page labels/routes cannot
 * corrupt the format. The contract is intentionally dependency-light so the same pack can be
 * consumed by the Android app.
 */
object KnowledgePackCodec {
    fun encode(pack: SiteKnowledgePack): String = buildString {
        appendLine("SITEBRAIN|${pack.schemaVersion}|${enc(pack.domain)}|${pack.generatedAtEpochMs}|${pack.coveragePercent}|${enc(pack.readiness)}")
        pack.hostAliases.sorted().forEach { appendLine("HOST|${enc(it)}") }
        pack.nodes.sortedBy { it.id }.forEach {
            appendLine("NODE|${enc(it.id)}|${enc(it.pageType)}|${enc(it.route)}|${enc(it.summary)}")
        }
        pack.transitions.sortedWith(compareBy<PackTransition> { it.fromId }.thenBy { it.toId }.thenBy { it.actionKind }).forEach {
            appendLine("EDGE|${enc(it.fromId)}|${enc(it.toId)}|${enc(it.actionKind)}|${enc(it.label)}|${it.confidence}|${it.verified}")
        }
        pack.boundaries.sortedBy { it.route }.forEach { appendLine("BOUNDARY|${enc(it.route)}|${enc(it.reason)}") }
    }

    fun decode(text: String): SiteKnowledgePack {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.isNotEmpty()) { "Empty Site Brain pack" }
        val header = lines.first().split('|')
        require(header.size == 6 && header[0] == "SITEBRAIN") { "Invalid Site Brain pack" }
        val hosts = mutableListOf<String>()
        val nodes = mutableListOf<PackNode>()
        val edges = mutableListOf<PackTransition>()
        val boundaries = mutableListOf<PackBoundary>()
        lines.drop(1).forEach { line ->
            val p = line.split('|')
            when (p[0]) {
                "HOST" -> hosts += dec(p[1])
                "NODE" -> nodes += PackNode(dec(p[1]), dec(p[2]), dec(p[3]), dec(p[4]))
                "EDGE" -> edges += PackTransition(dec(p[1]), dec(p[2]), dec(p[3]), dec(p[4]), p[5].toDouble(), p[6].toBoolean())
                "BOUNDARY" -> boundaries += PackBoundary(dec(p[1]), dec(p[2]))
            }
        }
        return SiteKnowledgePack(
            schemaVersion = header[1].toInt(),
            domain = dec(header[2]),
            hostAliases = hosts,
            generatedAtEpochMs = header[3].toLong(),
            coveragePercent = header[4].toInt(),
            readiness = dec(header[5]),
            nodes = nodes,
            transitions = edges,
            boundaries = boundaries
        )
    }

    private fun enc(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun dec(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
