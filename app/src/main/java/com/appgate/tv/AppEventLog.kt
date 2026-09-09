package com.appgate.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class BrowserEvent(
    val type: String,
    val timestamp: Long,
    val host: String = "",
    val detail: String = "",
    val outcome: String = ""
)

class AppEventLog(context: Context) {
    private val prefs = context.getSharedPreferences("ai_browser_event_log", Context.MODE_PRIVATE)

    fun add(type: String, host: String = "", detail: String = "", outcome: String = "") {
        val events = readMutable()
        events.add(BrowserEvent(type, System.currentTimeMillis(), host, detail.take(1000), outcome))
        val trimmed = if (events.size > MAX_EVENTS) events.takeLast(MAX_EVENTS) else events
        prefs.edit().putString(KEY_EVENTS, encodeEvents(trimmed)).apply()
    }

    fun events(): List<BrowserEvent> = readMutable()

    fun clear() {
        prefs.edit().remove(KEY_EVENTS).apply()
    }

    private fun readMutable(): MutableList<BrowserEvent> {
        val raw = prefs.getString(KEY_EVENTS, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            MutableList(array.length()) { i ->
                val o = array.getJSONObject(i)
                BrowserEvent(
                    type = o.optString("type"),
                    timestamp = o.optLong("timestamp"),
                    host = o.optString("host"),
                    detail = o.optString("detail"),
                    outcome = o.optString("outcome")
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    companion object {
        private const val KEY_EVENTS = "events"
        private const val MAX_EVENTS = 1500

        fun sanitizeUrl(value: String): String {
            return try {
                val uri = URI(value)
                val scheme = uri.scheme?.lowercase().orEmpty()
                if (scheme != "http" && scheme != "https") {
                    ""
                } else {
                    URI(uri.scheme, uri.userInfo, uri.host, uri.port, uri.path, null, null).toString()
                }
            } catch (_: Exception) {
                ""
            }
        }

        fun buildReport(
            events: List<BrowserEvent>,
            notes: String,
            versionName: String,
            versionCode: Int
        ): String {
            val eventJson = events.joinToString(",\n") { event ->
                """    {"type":"${escapeJson(event.type)}","timestamp":${event.timestamp},"host":"${escapeJson(event.host)}","detail":"${escapeJson(event.detail)}","outcome":"${escapeJson(event.outcome)}"}"""
            }
            return """
{
  "schemaVersion": 1,
  "appVersionName": "${escapeJson(versionName)}",
  "appVersionCode": $versionCode,
  "exportedAt": ${System.currentTimeMillis()},
  "testerNotes": "${escapeJson(notes.take(10_000))}",
  "privacy": "No cookies, passwords, form contents, page bodies, or URL query/fragment data are included.",
  "events": [
$eventJson
  ]
}
            """.trimIndent()
        }

        private fun escapeJson(value: String): String = buildString(value.length + 16) {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
                }
            }
        }

        private fun encodeEvents(events: List<BrowserEvent>): String {
            val array = JSONArray()
            events.forEach { event ->
                array.put(JSONObject().apply {
                    put("type", event.type)
                    put("timestamp", event.timestamp)
                    put("host", event.host)
                    put("detail", event.detail)
                    put("outcome", event.outcome)
                })
            }
            return array.toString()
        }
    }
}
