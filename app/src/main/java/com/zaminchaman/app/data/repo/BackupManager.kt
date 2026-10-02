package com.zaminchaman.app.data.repo

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.AppError
import com.zaminchaman.app.core.isNoSpace
import com.zaminchaman.app.data.db.DB_NAME
import com.zaminchaman.app.data.db.DB_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup file = ZIP containing manifest.json, db/zamin_chaman.db and attachments/*.
 * The manifest carries format/db version and SHA-256 checksums, so a damaged or
 * incompatible file is detected before anything on the phone is touched.
 */
class BackupManager(private val context: Context, private val container: AppContainer) {
    companion object {
        const val MANIFEST = "manifest.json"
        const val DB_ENTRY = "db/zamin_chaman.db"
        const val ATT_PREFIX = "attachments/"
        const val FORMAT = 1
    }

    private val dbFile: File get() = context.getDatabasePath(DB_NAME)
    private val attDir: File get() = container.attachments.dir
    private val internalDir: File get() = File(context.filesDir, "internal_backups").also { it.mkdirs() }

    // ---------------------------------------------------------------- create

    suspend fun createBackup(target: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(target, "w")
            ?: throw AppError.Validation("امکان نوشتن در مسیر انتخاب‌شده وجود ندارد.")
        try {
            out.use { writeBackup(it) }
        } catch (e: IOException) {
            throw if (isNoSpace(e)) AppError.InsufficientStorage() else e
        }
    }

    private fun writeBackup(out: OutputStream) {
        val tmp = File(context.cacheDir, "backup_db_${System.nanoTime()}.db")
        try {
            // Flush the write-ahead log into the main file so the copy is complete.
            container.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            if (context.cacheDir.usableSpace < dbFile.length() + 5_000_000L) throw AppError.InsufficientStorage()
            dbFile.copyTo(tmp, overwrite = true)
            val files = attDir.listFiles()?.filter { it.isFile }.orEmpty()
            val manifest = JSONObject().apply {
                put("format", FORMAT)
                put("dbVersion", readUserVersion(tmp))
                put("createdAt", System.currentTimeMillis())
                put("dbSha256", sha256(tmp))
                put("attachments", JSONArray().also { arr ->
                    files.forEach { arr.put(JSONObject().put("name", it.name).put("sha256", sha256(it))) }
                })
            }
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST)); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry(DB_ENTRY)); tmp.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                files.forEach { f ->
                    zip.putNextEntry(ZipEntry(ATT_PREFIX + f.name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        } finally {
            tmp.delete()
        }
    }

    // --------------------------------------------------------------- restore

    private class Extracted(val manifest: JSONObject, val db: File, val attDir: File)

    suspend fun restore(source: Uri) = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "restore_tmp").apply { deleteRecursively(); mkdirs() }
        try {
            val extracted = extract(source, work)
            validate(extracted)
            // Safety net: a full backup of the current data before it is replaced.
            val safety = File(internalDir, "pre_restore_${System.currentTimeMillis()}.zip")
            try {
                safety.outputStream().use { writeBackup(it) }
            } catch (e: IOException) {
                safety.delete()
                throw if (isNoSpace(e)) AppError.InsufficientStorage() else e
            }
            prune("pre_restore_", 3)
            swap(extracted)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun extract(source: Uri, work: File): Extracted {
        val manifestFile = File(work, "manifest.json")
        val dbOut = File(work, "db.sqlite")
        val attOut = File(work, "att").apply { mkdirs() }
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw AppError.CorruptBackup()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    val target = when {
                        name == MANIFEST -> manifestFile
                        name == DB_ENTRY -> dbOut
                        name.startsWith(ATT_PREFIX) -> {
                            val n = name.removePrefix(ATT_PREFIX)
                            if (n.isEmpty() || n.contains('/') || n.contains('\\') || n.contains("..")) null
                            else File(attOut, n)
                        }
                        else -> null
                    }
                    if (target != null) target.outputStream().use { zip.copyTo(it) }
                    zip.closeEntry()
                }
            }
            if (!manifestFile.exists() || !dbOut.exists()) throw AppError.CorruptBackup()
            return Extracted(JSONObject(manifestFile.readText()), dbOut, attOut)
        } catch (e: AppError) {
            throw e
        } catch (e: ZipException) {
            throw AppError.CorruptBackup()
        } catch (e: JSONException) {
            throw AppError.CorruptBackup()
        } catch (e: IOException) {
            throw if (isNoSpace(e)) AppError.InsufficientStorage() else AppError.CorruptBackup()
        }
    }

    private fun validate(x: Extracted) {
        val m = x.manifest
        val format = m.optInt("format", -1)
        if (format < 1) throw AppError.CorruptBackup()
        if (format > FORMAT) throw AppError.IncompatibleBackup()
        val ver = m.optInt("dbVersion", -1)
        if (ver < 1) throw AppError.CorruptBackup()
        if (ver > DB_VERSION) throw AppError.IncompatibleBackup()
        if (!looksLikeSqlite(x.db) || readUserVersion(x.db) != ver) throw AppError.CorruptBackup()
        if (sha256(x.db) != m.optString("dbSha256")) throw AppError.CorruptBackup()
        val arr = m.optJSONArray("attachments") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val f = File(x.attDir, o.getString("name"))
            if (!f.exists() || sha256(f) != o.getString("sha256")) throw AppError.CorruptBackup()
        }
        if (!integrityOk(x.db)) throw AppError.CorruptBackup()
    }

    private fun integrityOk(db: File): Boolean {
        val copy = File(db.parentFile, "integrity_check.db")
        try {
            db.copyTo(copy, overwrite = true)
            return SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { d ->
                d.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
            }
        } catch (e: Exception) {
            return false
        } finally {
            copy.delete(); File(copy.path + "-wal").delete(); File(copy.path + "-shm").delete()
        }
    }

    /** Replaces live data; any failure rolls back to the previous files. */
    private fun swap(x: Extracted) {
        container.db.close()
        val live = dbFile
        val suffixes = listOf("", "-wal", "-shm")
        val oldAtt = File(attDir.parentFile, "attachments.old")
        try {
            oldAtt.deleteRecursively()
            suffixes.forEach { s -> File(live.path + s).let { if (it.exists()) it.renameTo(File(it.path + ".old")) } }
            if (attDir.exists()) attDir.renameTo(oldAtt)
            x.db.copyTo(live, overwrite = true)
            val newAtt = attDir.apply { mkdirs() }
            x.attDir.listFiles()?.forEach { it.copyTo(File(newAtt, it.name), overwrite = true) }
            suffixes.forEach { s -> File(live.path + s + ".old").delete() }
            oldAtt.deleteRecursively()
        } catch (e: Throwable) {
            suffixes.forEach { s -> File(live.path + s).delete() }
            suffixes.forEach { s -> File(live.path + s + ".old").let { if (it.exists()) it.renameTo(File(live.path + s)) } }
            File(context.filesDir, "attachments").deleteRecursively()
            if (oldAtt.exists()) oldAtt.renameTo(File(context.filesDir, "attachments"))
            throw if (e is IOException && isNoSpace(e)) AppError.InsufficientStorage() else AppError.RestoreFailed(e)
        }
    }

    // --------------------------------------------------------------- helpers

    private fun prune(prefix: String, keep: Int) {
        internalDir.listFiles { f -> f.name.startsWith(prefix) }
            ?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { it.delete() }
    }

    private fun looksLikeSqlite(f: File): Boolean = runCatching {
        RandomAccessFile(f, "r").use { r ->
            val b = ByteArray(15); r.readFully(b); String(b, Charsets.US_ASCII) == "SQLite format 3"
        }
    }.getOrDefault(false)

    private fun readUserVersion(f: File): Int = RandomAccessFile(f, "r").use { it.seek(60); it.readInt() }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
