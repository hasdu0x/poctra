package com.poctra.wallet.data

import android.content.Context
import android.os.Environment
import java.io.File

class BackupExporter(private val context: Context) {
    fun save(content: String): String {
        val fileName = "poctra-backup-${System.currentTimeMillis()}.json"
        return saveToAppDownloads(fileName, content)
    }

    private fun saveToAppDownloads(fileName: String, content: String): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("External downloads directory is unavailable.")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file.absolutePath
    }
}
