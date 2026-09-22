package com.appgate.tv.sitebrain

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class GitHubLogRequest(
    val requestId: String,
    val requestedAt: String,
    val reason: String
)

object GitHubLogRequestClient {
    private const val REQUEST_URL =
        "https://raw.githubusercontent.com/lukeypue/APPGATE/ai-browser-v6-deep-search/telemetry/log-request.json"
    private const val PREFS = "github_log_requests"
    private const val LAST_HANDLED = "last_handled_request"

    fun fetch(): Result<GitHubLogRequest?> = runCatching {
        val connection = (URL(REQUEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) error("GitHub log request HTTP " + connection.responseCode)
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val id = json.optString("requestId").trim()
            if (id.isBlank() || id == "initial") null else GitHubLogRequest(
                requestId = id.take(120),
                requestedAt = json.optString("requestedAt").take(80),
                reason = json.optString("reason").take(500)
            )
        } finally {
            connection.disconnect()
        }
    }

    fun isNew(context: Context, request: GitHubLogRequest): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LAST_HANDLED, null)
        return last != request.requestId
    }

    fun markHandled(context: Context, request: GitHubLogRequest) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(LAST_HANDLED, request.requestId).apply()
    }

    fun buildBundle(
        context: Context,
        request: GitHubLogRequest,
        learningLog: File,
        gapLog: File?
    ): File {
        val safeId = request.requestId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        val out = File(context.filesDir, "AI-Browser-Logs-$safeId.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            add(zip, learningLog, "site_brain_learning_log.json")
            if (gapLog != null && gapLog.exists()) add(zip, gapLog, "site_brain_capability_gaps.jsonl")
            val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val versionName = packageInfo?.versionName.orEmpty()
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) packageInfo?.longVersionCode ?: 0L else @Suppress("DEPRECATION") (packageInfo?.versionCode?.toLong() ?: 0L)
            val meta = JSONObject().apply {
                put("schemaVersion", 1)
                put("appVersionName", versionName)
                put("appVersionCode", versionCode)
                put("requestId", request.requestId)
                put("requestedAt", request.requestedAt)
                put("reason", request.reason)
                put("createdAt", System.currentTimeMillis())
                put("containsLearningLog", learningLog.exists())
                put("containsGapLog", gapLog?.exists() == true)
            }.toString(2).toByteArray(Charsets.UTF_8)
            zip.putNextEntry(ZipEntry("request-metadata.json"))
            zip.write(meta)
            zip.closeEntry()
        }
        return out
    }

    private fun add(zip: ZipOutputStream, file: File, name: String) {
        if (!file.exists()) return
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }
}