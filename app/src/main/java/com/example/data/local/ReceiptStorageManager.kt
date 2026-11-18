package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Persists picked or captured receipt images inside app-private internal storage (`filesDir/receipts/`),
 * guaranteeing durable offline access even after temporary PhotoPicker content URI grants expire.
 */
object ReceiptStorageManager {

    private const val RECEIPTS_DIR = "receipts"

    fun receiptsDirectory(context: Context): File {
        return File(context.filesDir, RECEIPTS_DIR).apply { mkdirs() }
    }

    suspend fun persistReceiptUri(context: Context, uriString: String?): String? {
        val raw = uriString?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Already persisted internally
        if (raw.startsWith("file://") && raw.contains("/$RECEIPTS_DIR/")) return raw
        val dir = receiptsDirectory(context)
        if (raw.startsWith(dir.absolutePath)) return Uri.fromFile(File(raw)).toString()

        return withContext(Dispatchers.IO) {
            runCatching {
                val parsedUri = Uri.parse(raw)
                val destFile = File(dir, "rcpt_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
                context.contentResolver.openInputStream(parsedUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@runCatching raw
                if (destFile.exists() && destFile.length() > 0L) {
                    Uri.fromFile(destFile).toString()
                } else {
                    destFile.delete()
                    raw
                }
            }.getOrDefault(raw)
        }
    }

    suspend fun persistReceiptBitmap(context: Context, bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = receiptsDirectory(context)
                val destFile = File(dir, "rcpt_scan_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg")
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    Uri.fromFile(destFile).toString()
                } else {
                    destFile.delete()
                    null
                }
            }.getOrNull()
        }
    }

    fun deleteReceiptIfOwned(context: Context, uriString: String?) {
        val raw = uriString ?: return
        runCatching {
            val dir = receiptsDirectory(context).canonicalPath
            val path = Uri.parse(raw).path ?: return
            val file = File(path)
            if (file.canonicalPath.startsWith(dir) && file.exists()) {
                file.delete()
            }
        }
    }
}
