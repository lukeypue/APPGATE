package com.appgate.tv.sitebrain

import org.json.JSONArray
import org.json.JSONObject

data class LearningEvent(
    val timestamp: Long,
    val source: String,
    val host: String,
    val pageType: String,
    val route: String,
    val action: String,
    val outcome: String,
    val coverageBefore: Double,
    val coverageAfter: Double,
    val note: String = ""
)

object LearningReportWriter {
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phone = Regex("(?<!\\d)(?:\\+?1[-.\\s]?)?(?:\\(?\\d{3}\\)?[-.\\s]?)\\d{3}[-.\\s]?\\d{4}(?!\\d)")
    private val secret = Regex("(?i)\\b(cookie|session|token|password|authorization|bearer)\\b\\s*[:=]?\\s*[^\\s,;]*")

    fun encode(events: List<LearningEvent>): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("reportType", "site_brain_search_run")
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().apply {
                put("timestamp", e.timestamp)
                put("source", clean(e.source))
                put("host", clean(e.host))
                put("pageType", clean(e.pageType))
                put("route", clean(e.route))
                put("action", clean(e.action))
                put("outcome", clean(e.outcome))
                put("coverageBefore", e.coverageBefore.coerceIn(0.0, 1.0))
                put("coverageAfter", e.coverageAfter.coerceIn(0.0, 1.0))
                put("note", clean(e.note))
            })
        }
        root.put("events", arr)
        return root.toString(2)
    }

    private fun clean(value: String): String = value
        .replace(email, "[redacted]")
        .replace(phone, "[redacted]")
        .replace(secret, "[redacted]")
        .take(1000)
}
