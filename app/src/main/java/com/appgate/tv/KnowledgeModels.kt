package com.appgate.tv

data class KnowledgeEntry(
    val id: String,
    val title: String,
    val source: String,
    val content: String,
    val updatedAt: Long,
    val kind: String
)

data class SiteSkill(
    val host: String,
    val instructions: String,
    val updatedAt: Long
)

data class KnowledgeResult(
    val title: String,
    val source: String,
    val snippet: String,
    val kind: String,
    val score: Int
)
