package com.appgate.tv

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File

object LearningLogExporter {
    const val LEARNING_LOG_FILE = "site_brain_learning_log.json"

    fun saveToDownloads(context: Context): Result<String> = runCatching {
        val source = File(context.filesDir, LEARNING_LOG_FILE)
        require(source.exists()) { "No learning log file exists yet." }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, LEARNING_LOG_FILE)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values)
            ?: error("Could not create the log in Downloads.")

        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Could not open the Downloads file.")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (error: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
        LEARNING_LOG_FILE
    }
}
