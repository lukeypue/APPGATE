package com.appgate.sitebrainlab

data class CoverageReport(
    val discoveredFunctions: Int,
    val classifiedFunctions: Int,
    val percent: Int,
    val readiness: String
)

data class StructuralFunction(val key: String, val status: FunctionStatus)
enum class FunctionStatus { DISCOVERED, VERIFIED, UNRESOLVED, PROTECTED }

object CoverageEngine {
    fun calculate(functions: Collection<StructuralFunction>): CoverageReport {
        if (functions.isEmpty()) return CoverageReport(0, 0, 0, "UNMAPPED")
        val unique = functions.distinctBy { it.key }
        val classified = unique.count { it.status != FunctionStatus.DISCOVERED }
        val percent = ((classified.toDouble() / unique.size) * 100.0).toInt().coerceIn(0, 100)
        val readiness = when {
            percent == 100 && unique.size >= 5 -> "DEEP_SEARCH_READY"
            percent >= 60 -> "SEARCH_READY"
            else -> "LEARNING"
        }
        return CoverageReport(unique.size, classified, percent, readiness)
    }
}

data class LabCheckpoint(
    val domain: String,
    val visited: List<String>,
    val pendingControlIds: List<String>,
    val attemptedControlIds: List<String>,
    val boundaries: List<PackBoundary>
)

object CheckpointCodec {
    fun encode(checkpoint: LabCheckpoint): String = buildString {
        appendLine(checkpoint.domain)
        appendLine(checkpoint.visited.joinToString("\t"))
        appendLine(checkpoint.pendingControlIds.joinToString("\t"))
        appendLine(checkpoint.attemptedControlIds.joinToString("\t"))
        checkpoint.boundaries.forEach { appendLine("${it.route}\t${it.reason}") }
    }

    fun decode(text: String): LabCheckpoint {
        val lines = text.lines()
        fun split(line: String?): List<String> = line.orEmpty().split('\t').filter { it.isNotBlank() }
        return LabCheckpoint(
            domain = lines.getOrElse(0) { "" },
            visited = split(lines.getOrNull(1)),
            pendingControlIds = split(lines.getOrNull(2)),
            attemptedControlIds = split(lines.getOrNull(3)),
            boundaries = lines.drop(4).filter { it.contains('\t') }.map {
                val p = it.split('\t', limit = 2); PackBoundary(p[0], p[1])
            }
        )
    }
}

object PackBuilder {
    fun build(domain: String, nodes: List<PackNode>, transitions: List<PackTransition>, boundaries: List<PackBoundary>, functions: Collection<StructuralFunction>, nowMs: Long = System.currentTimeMillis()): SiteKnowledgePack {
        val report = CoverageEngine.calculate(functions)
        return PackSanitizer.sanitize(SiteKnowledgePack(1, domain, emptyList(), nowMs, report.percent, report.readiness, nodes, transitions, boundaries))
    }
}
