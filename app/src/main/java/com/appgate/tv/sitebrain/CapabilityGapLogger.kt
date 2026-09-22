package com.appgate.tv.sitebrain

import android.content.Context
import org.json.JSONObject
import java.io.File

class CapabilityGapLogger(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun record(
        type: String,
        source: String,
        host: String,
        route: String,
        detail: String,
        resolvedByHuman: Boolean = false
    ) {
        val event = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("type", clean(type))
            put("source", clean(source))
            put("host", clean(host))
            put("route", clean(route))
            put("detail", clean(detail))
            put("resolvedByHuman", resolvedByHuman)
        }.toString()
        file.appendText(event + "\n")
        trimIfNeeded()
    }

    fun fileOrNull(): File? = file.takeIf { it.exists() && it.length() > 0L }

    private fun trimIfNeeded() {
        if (file.length() <= MAX_BYTES) return
        val kept = file.readLines().takeLast(MAX_LINES)
        file.writeText(kept.joinToString("\n", postfix = if (kept.isEmpty()) "" else "\n"))
    }

    private fun clean(value: String): String = value
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(1200)

    companion object {
        const val FILE_NAME = "site_brain_capability_gaps.jsonl"
        private const val MAX_BYTES = 2_000_000L
        private const val MAX_LINES = 5000
    }
}
