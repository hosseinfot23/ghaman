package com.zaminchaman.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import java.io.File

const val DB_NAME = "zamin_chaman.db"
const val DB_VERSION = 1

@Database(
    entities = [
        Customer::class, Contract::class, Booking::class, Payment::class,
        Expense::class, Attachment::class, ActivityLog::class, AppSetting::class
    ],
    version = DB_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun contractDao(): ContractDao
    abstract fun bookingDao(): BookingDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun financeDao(): FinanceDao
    abstract fun activityDao(): ActivityDao
    abstract fun settingDao(): SettingDao

    companion object {
        fun build(context: Context): AppDatabase {
            preMigrationCopy(context)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .addMigrations(*Migrations.ALL)
                // NEVER add fallbackToDestructiveMigration(): user data must survive every update.
                .build()
        }

        /** Before an upgrade runs, keep a copy of the old database inside the app's private storage. */
        private fun preMigrationCopy(context: Context) {
            val db = context.getDatabasePath(DB_NAME)
            if (!db.exists()) return
            try {
                val old = SQLiteDatabase.openDatabase(db.path, null, SQLiteDatabase.OPEN_READONLY)
                    .use { it.version }
                if (old in 1 until DB_VERSION) {
                    val dir = File(context.filesDir, "internal_backups").apply { mkdirs() }
                    val stamp = System.currentTimeMillis()
                    for (suffix in listOf("", "-wal", "-shm")) {
                        val src = File(db.path + suffix)
                        if (src.exists()) src.copyTo(File(dir, "pre_migration_v${old}_$stamp.db$suffix"), true)
                    }
                    dir.listFiles { f -> f.name.startsWith("pre_migration_") && f.name.endsWith(".db") }
                        ?.sortedByDescending { it.lastModified() }
                        ?.drop(3)
                        ?.forEach { f ->
                            f.delete()
                            File(f.path + "-wal").delete()
                            File(f.path + "-shm").delete()
                        }
                }
            } catch (_: Exception) {
                // A failed safety copy must never block the app from starting.
            }
        }
    }
}

/**
 * How to ship a schema change (e.g. version 2):
 *  1. Change the entities and bump DB_VERSION to 2.
 *  2. Add MIGRATION_1_2 below with plain ALTER TABLE / CREATE TABLE statements.
 *  3. Add it to ALL, build once so Room exports schemas/2.json, commit both schema files.
 *  4. Extend MigrationTest (androidTest) for 1 -> 2.
 */
object Migrations {
    // val MIGRATION_1_2 = object : Migration(1, 2) {
    //     override fun migrate(db: SupportSQLiteDatabase) {
    //         db.execSQL("ALTER TABLE customers ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    //     }
    // }
    val ALL: Array<Migration> = arrayOf()
}
