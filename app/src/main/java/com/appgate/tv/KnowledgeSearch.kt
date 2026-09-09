package com.appgate.tv

import java.net.URI

object KnowledgeSearch {
    fun search(
        query: String,
        entries: List<KnowledgeEntry>,
        skills: List<SiteSkill>,
        limit: Int = 20
    ): List<KnowledgeResult> {
        val tokens = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty() || limit <= 0) return emptyList()

        val results = mutableListOf<KnowledgeResult>()

        entries.forEach { entry ->
            val title = entry.title.lowercase()
            val source = entry.source.lowercase()
            val content = entry.content.lowercase()
            var score = 0
            tokens.forEach { token ->
                if (title.contains(token)) score += 6
                if (source.contains(token)) score += 3
                if (content.contains(token)) score += 1
            }
            if (score > 0) {
                results += KnowledgeResult(
                    title = entry.title,
                    source = entry.source,
                    snippet = entry.content.replace(Regex("\\s+"), " ").take(220),
                    kind = entry.kind,
                    score = score
                )
            }
        }

        skills.forEach { skill ->
            val host = skill.host.lowercase()
            val instructions = skill.instructions.lowercase()
            var score = 0
            tokens.forEach { token ->
                if (host.contains(token)) score += 5
                if (instructions.contains(token)) score += 2
            }
            if (score > 0) {
                results += KnowledgeResult(
                    title = "Site skill: ${skill.host}",
                    source = "site://${skill.host}",
                    snippet = skill.instructions.replace(Regex("\\s+"), " ").take(220),
                    kind = "site-skill",
                    score = score
                )
            }
        }

        return results.sortedWith(
            compareByDescending<KnowledgeResult> { it.score }.thenBy { it.title.lowercase() }
        ).take(limit)
    }

    fun normalizeHost(raw: String): String {
        val trimmed = raw.trim().lowercase()
        if (trimmed.isBlank()) return ""
        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        return try {
            (URI(candidate).host ?: trimmed)
                .removePrefix("www.")
                .substringBefore('/')
                .substringBefore(':')
        } catch (_: Exception) {
            trimmed.removePrefix("www.").substringBefore('/').substringBefore(':')
        }
    }
}
