package com.zaminchaman.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object BookingStatus {
    const val SCHEDULED = "SCHEDULED"
    const val DONE = "DONE"
    const val CANCELLED = "CANCELLED"

    fun label(status: String): String = when (status) {
        DONE -> "انجام شد"
        CANCELLED -> "لغو شد"
        else -> "در انتظار"
    }
}

object PatternType {
    const val EVEN = "EVEN"
    const val ODD = "ODD"
    const val WEEKDAYS = "WEEKDAYS"
}

@Entity(tableName = "customers", indices = [Index("name"), Index("phone")])
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

/** A recurring agreement. It never creates sessions by itself. */
@Entity(
    tableName = "contracts",
    foreignKeys = [ForeignKey(Customer::class, ["id"], ["customerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("customerId")]
)
data class Contract(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val patternType: String,
    /** bit i set = weekday i (0 = شنبه ... 6 = جمعه). Used when patternType == WEEKDAYS. */
    val weekdayMask: Int = 0,
    val startMinute: Int,
    val endMinute: Int,
    val startDay: Long,
    val endDay: Long? = null,
    val defaultPrice: Long = 0,
    val active: Boolean = true,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

/**
 * One reservation / one session. A normal booking has contractId == null.
 * A session of a recurring contract has contractId set and is only created when the
 * customer really comes (operator logs it).
 */
@Entity(
    tableName = "bookings",
    foreignKeys = [
        ForeignKey(Customer::class, ["id"], ["customerId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(Contract::class, ["id"], ["contractId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("customerId"), Index("contractId"), Index("startAt"), Index("endAt")]
)
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val contractId: Long? = null,
    val startAt: Long,
    val endAt: Long,
    val totalAmount: Long,
    val status: String = BookingStatus.SCHEDULED,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

/** Debt is never stored: debt = totalAmount - SUM(payments). Income comes only from here. */
@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(Booking::class, ["id"], ["bookingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookingId"), Index("paidAt")]
)
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookingId: Long,
    val amount: Long,
    val paidAt: Long,
    val note: String = ""
)

@Entity(tableName = "expenses", indices = [Index("spentAt")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val title: String,
    val note: String = "",
    val spentAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(Expense::class, ["id"], ["expenseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("expenseId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    /** File name inside filesDir/attachments (random UUID, never a user path). */
    val storedName: String,
    val fileName: String,
    val mimeType: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_log")
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val text: String
)

/** Key/value store: login hash, auto-lock time... lives in the DB so Backup carries it too. */
@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val settingKey: String,
    val settingValue: String
)
