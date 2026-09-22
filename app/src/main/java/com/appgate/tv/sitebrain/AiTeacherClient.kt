package com.appgate.tv.sitebrain

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class AiTeacherSuggestion(
    val actionKind: ActionKind?,
    val targetElementId: String?,
    val diagnosis: String,
    val capabilityGap: String?,
    val needsEngineCode: Boolean
)

object AiTeacherClient {
    private val executor = Executors.newSingleThreadExecutor()

    fun suggest(
        apiKey: String,
        snapshot: PageSnapshot,
        failureReason: String,
        callback: (Result<AiTeacherSuggestion>) -> Unit
    ) {
        executor.execute {
            callback(runCatching { request(apiKey, snapshot, failureReason) })
        }
    }

    private fun request(apiKey: String, snapshot: PageSnapshot, failureReason: String): AiTeacherSuggestion {
        val controls = JSONArray()
        snapshot.elements
            .filter { SafeActionClassifier.classify(it) == SafetyClass.SAFE }
            .take(80)
            .forEach { e ->
                controls.put(JSONObject().apply {
                    put("id", e.id)
                    put("tag", e.tag.take(30))
                    put("role", e.role.orEmpty().take(40))
                    put("label", e.label.take(140))
                    put("inputType", e.inputType.orEmpty().take(40))
                    put("selected", e.selected)
                    put("currentValue", e.currentValue.orEmpty().take(120))
                    put("choices", JSONArray(e.choices.take(30).map { it.take(100) }))
                })
            }

        val input = JSONObject().apply {
            put("host", snapshot.host.take(180))
            put("route", snapshot.routeSignature.take(300))
            put("pageType", snapshot.pageType.name)
            put("failureReason", failureReason.take(500))
            put("controls", controls)
        }

        val schema = JSONObject().apply {
            put("type", "object")
            put("additionalProperties", false)
            put("properties", JSONObject().apply {
                put("action_kind", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray(listOf(
                        "SEARCH", "NAVIGATE", "OPEN_CATEGORY", "APPLY_FILTER", "SORT",
                        "PAGINATE", "EXPAND", "OPEN_DETAIL", "OPEN_TAB", "BACK", "NO_ACTION"
                    )))
                })
                put("target_element_id", JSONObject().apply { put("type", "string") })
                put("diagnosis", JSONObject().apply { put("type", "string") })
                put("capability_gap", JSONObject().apply { put("type", "string") })
                put("needs_engine_code", JSONObject().apply { put("type", "boolean") })
            })
            put("required", JSONArray(listOf("action_kind", "target_element_id", "diagnosis", "capability_gap", "needs_engine_code")))
        }

        val body = JSONObject().apply {
            put("model", "gpt-5.6-luna")
            put("store", false)
            put("reasoning", JSONObject().put("effort", "low"))
            put(
                "instructions",
                "You are a conservative website-control teacher for an Android Site Brain. " +
                    "Choose only from the supplied safe controls and allowed action kinds. " +
                    "Never suggest CAPTCHA, login, 2FA, payment, messaging, purchasing, destructive actions, " +
                    "account changes, or bypassing access controls. If the current engine lacks a capability, " +
                    "return NO_ACTION and explain the reusable capability gap."
            )
            put("input", input.toString())
            put("text", JSONObject().put("format", JSONObject().apply {
                put("type", "json_schema")
                put("name", "site_brain_teacher")
                put("strict", true)
                put("schema", schema)
            }))
        }

        val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("AI teacher request failed with HTTP $code: ${responseText.take(700)}")
            val response = JSONObject(responseText)
            val outputText = response.optString("output_text").ifBlank { extractOutputText(response) }
            if (outputText.isBlank()) error("AI teacher returned no structured output")
            val o = JSONObject(outputText)
            val rawKind = o.optString("action_kind").takeIf { it.isNotBlank() && it != "null" }
            val kind = rawKind
                ?.takeIf { it != "NO_ACTION" }
                ?.let { runCatching { ActionKind.valueOf(it) }.getOrNull() }
            return AiTeacherSuggestion(
                actionKind = kind,
                targetElementId = o.optString("target_element_id").takeIf { it.isNotBlank() && it != "null" },
                diagnosis = o.optString("diagnosis").take(1000),
                capabilityGap = o.optString("capability_gap").takeIf { it.isNotBlank() && it != "null" }?.take(1000),
                needsEngineCode = o.optBoolean("needs_engine_code", false)
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun extractOutputText(response: JSONObject): String {
        val output = response.optJSONArray("output") ?: return ""
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                if (part.optString("type") == "output_text") {
                    val text = part.optString("text")
                    if (text.isNotBlank()) return text
                }
            }
        }
        return ""
    }
}
