package com.zaminchaman.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.zaminchaman.app.data.db.AppDatabase
import com.zaminchaman.app.data.repo.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Manual dependency container: keeps UI, logic and storage decoupled without a DI framework. */
class AppContainer(val context: Context) {
    val db: AppDatabase by lazy { AppDatabase.build(context) }
    val attachments = AttachmentStore(context)
    val session = SessionManager()

    private val logger by lazy { ActivityLogger(db.activityDao()) }
    val auth by lazy { AuthRepository(db.settingDao()) }
    val customers by lazy { CustomerRepository(db, logger) }
    val bookings by lazy { BookingRepository(db, logger) }
    val contracts by lazy { ContractRepository(db, logger) }
    val finance by lazy { FinanceRepository(db, attachments, logger) }
    val reports by lazy { ReportRepository(db) }
    val search by lazy { SearchRepository(db) }
    val backup by lazy { BackupManager(context, this) }

    /** Forces the database open (and any migration to run) so failures surface at startup. */
    fun openDatabase() {
        db.openHelper.writableDatabase
    }

    fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Handler(Looper.getMainLooper()).postDelayed({ Runtime.getRuntime().exit(0) }, 300)
    }
}

/** Tracks lock state and inactivity for the auto-lock feature. */
class SessionManager {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    @Volatile var timeoutSeconds: Int = 300
    @Volatile private var lastActive: Long = SystemClock.elapsedRealtime()

    fun unlock() { lastActive = SystemClock.elapsedRealtime(); _unlocked.value = true }
    fun lock() { _unlocked.value = false }
    fun touch() { lastActive = SystemClock.elapsedRealtime() }

    /** 0 means "never lock automatically". Background time counts as inactivity. */
    fun enforce() {
        val t = timeoutSeconds
        if (_unlocked.value && t > 0 && SystemClock.elapsedRealtime() - lastActive > t * 1000L) lock()
    }
}
