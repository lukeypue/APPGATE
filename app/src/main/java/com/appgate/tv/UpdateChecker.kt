package com.appgate.tv

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
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
        val root = JSONObject(json)
        return UpdateInfo(
            versionCode = root.getInt("versionCode"),
            versionName = root.getString("versionName"),
            notes = root.optString("notes"),
            downloadUrl = root.getString("downloadUrl")
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
}
