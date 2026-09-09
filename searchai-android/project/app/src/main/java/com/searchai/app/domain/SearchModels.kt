package com.searchai.app.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant

data class SearchSource(
    val id: String,
    val name: String,
    val searchUrlTemplate: String,
    val aliases: List<String> = emptyList()
) {
    fun searchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString()).replace("+", "%20")
        return searchUrlTemplate.replace("{q}", encoded)
    }
}

data class SearchResult(
    val title: String,
    val price: String?,
    val source: String,
    val url: String
)

enum class UpdatePolicy {
    EVERY_LAUNCH,
    DAILY,
    WEEKLY,
    ASK_FIRST;

    fun shouldCheck(lastCheck: Instant?, now: Instant): Boolean = when (this) {
        EVERY_LAUNCH -> true
        ASK_FIRST -> false
        DAILY -> lastCheck == null || Duration.between(lastCheck, now).toHours() >= 24
        WEEKLY -> lastCheck == null || Duration.between(lastCheck, now).toDays() >= 7
    }
}
