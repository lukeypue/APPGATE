package com.appgate.tv

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: String,
    val downloadUrl: String
)

object UpdateChecker {
    fun parseManifest(json: String): UpdateInfo {
        return UpdateInfo(
            versionCode = extractInt(json, "versionCode"),
            versionName = extractString(json, "versionName"),
            notes = extractString(json, "notes", required = false),
            downloadUrl = extractString(json, "downloadUrl")
        )
    }

    fun check(url: String, callback: (Result<UpdateInfo>) -> Unit) {
        Thread {
            val result = runCatching {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                }
                try {
                    if (connection.responseCode !in 200..299) {
                        error("Update server returned ${connection.responseCode}")
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parseManifest(body)
                } finally {
                    connection.disconnect()
                }
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }

    private fun extractInt(json: String, key: String): Int {
        val regex = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(\\d+)")
        return regex.find(json)?.groupValues?.get(1)?.toInt()
            ?: error("Missing integer field: $key")
    }

    private fun extractString(json: String, key: String, required: Boolean = true): String {
        val regex = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        val raw = regex.find(json)?.groupValues?.get(1)
        if (raw == null) {
            if (required) error("Missing string field: $key")
            return ""
        }
        return unescapeJsonString(raw)
    }

    private fun unescapeJsonString(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch != '\\' || i + 1 >= raw.length) {
                out.append(ch)
                i++
                continue
            }
            val next = raw[i + 1]
            when (next) {
                '"' -> out.append('"')
                '\\' -> out.append('\\')
                '/' -> out.append('/')
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                else -> out.append(next)
            }
            i += 2
        }
        return out.toString()
    }
}
