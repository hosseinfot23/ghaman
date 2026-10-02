package com.zaminchaman.app.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.zaminchaman.app.core.AppError
import com.zaminchaman.app.core.isNoSpace
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Invoice / receipt files live in the app's private storage (filesDir/attachments).
 * That folder survives app updates and is included in every Backup.
 */
class AttachmentStore(private val context: Context) {
    val dir: File get() = File(context.filesDir, "attachments").also { it.mkdirs() }

    data class Imported(val storedName: String, val fileName: String, val mime: String)

    fun import(uri: Uri): Imported {
        val resolver = context.contentResolver
        var name = "فاکتور"
        var size = -1L
        resolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (ni >= 0) c.getString(ni)?.let { name = it }
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        if (size > 0 && dir.usableSpace < size + 10L * 1024 * 1024) throw AppError.InsufficientStorage()

        val ext = name.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(8)
        val stored = UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else ""
        val target = File(dir, stored)
        try {
            val input = resolver.openInputStream(uri) ?: throw AppError.Validation("خواندن فایل انتخاب‌شده ممکن نشد.")
            input.use { src -> target.outputStream().use { out -> src.copyTo(out) } }
        } catch (e: IOException) {
            target.delete()
            throw if (isNoSpace(e)) AppError.InsufficientStorage() else e
        }
        return Imported(stored, name, mime)
    }

    fun file(storedName: String): File = File(dir, storedName)
    fun exists(storedName: String): Boolean = file(storedName).exists()
    fun delete(storedName: String) {
        runCatching { file(storedName).delete() }
    }

    fun uriFor(storedName: String): Uri {
        val f = file(storedName)
        if (!f.exists()) throw AppError.AttachmentMissing()
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }
}
