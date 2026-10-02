package com.zaminchaman.app.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ---------------- read models ----------------

data class BookingRow(
    @Embedded val booking: Booking,
    val customerName: String,
    val customerPhone: String,
    val paid: Long
) {
    val debt: Long get() = (booking.totalAmount - paid).coerceAtLeast(0)
}

data class CustomerRow(@Embedded val customer: Customer, val debt: Long)
data class ContractRow(@Embedded val contract: Contract, val customerName: String)
data class PaymentRow(@Embedded val payment: Payment, val customerName: String)
data class AttachmentHit(@Embedded val attachment: Attachment, val expenseTitle: String)
data class TransactionRow(
    val type: String,
    val refId: Long,
    val bookingId: Long,
    val amount: Long,
    val title: String,
    val note: String,
    val occurredAt: Long
)

private const val BOOKING_ROW = """SELECT b.*, c.name AS customerName, c.phone AS customerPhone,
    COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.bookingId = b.id), 0) AS paid
    FROM bookings b JOIN customers c ON c.id = b.customerId"""

private const val CONTRACT_ROW =
    "SELECT k.*, c.name AS customerName FROM contracts k JOIN customers c ON c.id = k.customerId"

// ---------------- DAOs ----------------

@Dao
interface CustomerDao {
    @Insert suspend fun insert(c: Customer): Long
    @Update suspend fun update(c: Customer)

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun byId(id: Long): Customer?

    @Query("SELECT * FROM customers WHERE deletedAt IS NULL ORDER BY name")
    suspend fun active(): List<Customer>

    @Query("SELECT * FROM customers WHERE deletedAt IS NULL AND name = :name AND phone = :phone LIMIT 1")
    suspend fun findExact(name: String, phone: String): Customer?

    @Query(
        """SELECT c.*, COALESCE((SELECT SUM(MAX(b.totalAmount - COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.bookingId = b.id), 0), 0))
           FROM bookings b WHERE b.customerId = c.id AND b.deletedAt IS NULL AND b.status != 'CANCELLED'), 0) AS debt
           FROM customers c WHERE c.deletedAt IS NULL ORDER BY c.name"""
    )
    fun observeRows(): Flow<List<CustomerRow>>

    @Query("SELECT COUNT(*) FROM bookings WHERE customerId = :id AND deletedAt IS NULL")
    suspend fun activeBookingCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM contracts WHERE customerId = :id AND deletedAt IS NULL")
    suspend fun activeContractCount(id: Long): Int

    @Query("UPDATE customers SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)

    @Query("UPDATE customers SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM customers WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Customer>>

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("SELECT * FROM customers WHERE deletedAt IS NULL AND (name LIKE '%' || :q || '%' OR phone LIKE '%' || :qNum || '%') ORDER BY name LIMIT 30")
    suspend fun search(q: String, qNum: String): List<Customer>
}

@Dao
interface ContractDao {
    @Insert suspend fun insert(c: Contract): Long
    @Update suspend fun update(c: Contract)

    @Query("SELECT * FROM contracts WHERE id = :id")
    suspend fun byId(id: Long): Contract?

    @Query("$CONTRACT_ROW WHERE k.id = :id")
    suspend fun rowById(id: Long): ContractRow?

    @Query("$CONTRACT_ROW WHERE k.deletedAt IS NULL ORDER BY k.active DESC, c.name")
    fun observeAll(): Flow<List<ContractRow>>

    @Query("$CONTRACT_ROW WHERE k.deletedAt IS NULL AND k.active = 1")
    suspend fun activeRows(): List<ContractRow>

    @Query("UPDATE contracts SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)

    @Query("UPDATE contracts SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("$CONTRACT_ROW WHERE k.deletedAt IS NOT NULL ORDER BY k.deletedAt DESC")
    fun observeTrash(): Flow<List<ContractRow>>

    @Query("DELETE FROM contracts WHERE id = :id")
    suspend fun deletePermanently(id: Long)
}

@Dao
interface BookingDao {
    @Insert suspend fun insert(b: Booking): Long
    @Update suspend fun update(b: Booking)

    @Query("SELECT * FROM bookings WHERE id = :id")
    suspend fun byId(id: Long): Booking?

    @Query("$BOOKING_ROW WHERE b.id = :id")
    suspend fun rowById(id: Long): BookingRow?

    @Query("$BOOKING_ROW WHERE b.deletedAt IS NULL AND b.startAt BETWEEN :from AND :to ORDER BY b.startAt")
    fun observeRange(from: Long, to: Long): Flow<List<BookingRow>>

    @Query("$BOOKING_ROW WHERE b.deletedAt IS NULL AND b.startAt BETWEEN :from AND :to ORDER BY b.startAt")
    suspend fun rangeRows(from: Long, to: Long): List<BookingRow>

    /** Overlap test: [start, end) intersects an existing active booking. Back-to-back is allowed. */
    @Query("$BOOKING_ROW WHERE b.deletedAt IS NULL AND b.status != 'CANCELLED' AND b.id != :excludeId AND b.startAt < :end AND b.endAt > :start LIMIT 1")
    suspend fun firstOverlap(start: Long, end: Long, excludeId: Long): BookingRow?

    @Query("$BOOKING_ROW WHERE b.deletedAt IS NULL AND b.customerId = :customerId ORDER BY b.startAt DESC")
    fun observeForCustomer(customerId: Long): Flow<List<BookingRow>>

    @Query("$BOOKING_ROW WHERE b.deletedAt IS NULL AND b.contractId = :contractId ORDER BY b.startAt DESC")
    fun observeForContract(contractId: Long): Flow<List<BookingRow>>

    @Query("SELECT COUNT(*) FROM bookings WHERE deletedAt IS NULL AND contractId = :contractId AND startAt BETWEEN :from AND :to")
    suspend fun countForContractBetween(contractId: Long, from: Long, to: Long): Int

    @Query(
        """SELECT COALESCE(SUM(t.d), 0) FROM (
             SELECT b.totalAmount - COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.bookingId = b.id), 0) AS d
             FROM bookings b WHERE b.deletedAt IS NULL AND b.status != 'CANCELLED') t WHERE t.d > 0"""
    )
    fun observeTotalDebt(): Flow<Long>

    @Query("UPDATE bookings SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)

    @Query("UPDATE bookings SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("$BOOKING_ROW WHERE b.deletedAt IS NOT NULL ORDER BY b.deletedAt DESC")
    fun observeTrash(): Flow<List<BookingRow>>

    @Query("DELETE FROM bookings WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("$BOOKING_ROW WHERE b.deletedAt IS NULL AND (c.name LIKE '%' || :q || '%' OR c.phone LIKE '%' || :qNum || '%' OR b.note LIKE '%' || :q || '%' OR CAST(b.totalAmount AS TEXT) LIKE '%' || :qNum || '%') ORDER BY b.startAt DESC LIMIT 30")
    suspend fun search(q: String, qNum: String): List<BookingRow>
}

@Dao
interface PaymentDao {
    @Insert suspend fun insert(p: Payment): Long

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM payments WHERE bookingId = :bookingId ORDER BY paidAt DESC")
    fun forBooking(bookingId: Long): Flow<List<Payment>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE bookingId = :bookingId")
    suspend fun sumForBooking(bookingId: Long): Long

    @Query(
        """SELECT COALESCE(SUM(p.amount), 0) FROM payments p JOIN bookings b ON b.id = p.bookingId
           WHERE b.deletedAt IS NULL AND p.paidAt BETWEEN :from AND :to"""
    )
    fun observeIncome(from: Long, to: Long): Flow<Long>

    @Query(
        """SELECT p.*, c.name AS customerName FROM payments p
           JOIN bookings b ON b.id = p.bookingId JOIN customers c ON c.id = b.customerId
           WHERE b.deletedAt IS NULL AND p.paidAt BETWEEN :from AND :to ORDER BY p.paidAt DESC"""
    )
    suspend fun rangeRows(from: Long, to: Long): List<PaymentRow>

    @Query(
        """SELECT p.*, c.name AS customerName FROM payments p
           JOIN bookings b ON b.id = p.bookingId JOIN customers c ON c.id = b.customerId
           WHERE b.deletedAt IS NULL AND (c.name LIKE '%' || :q || '%' OR p.note LIKE '%' || :q || '%' OR CAST(p.amount AS TEXT) LIKE '%' || :qNum || '%')
           ORDER BY p.paidAt DESC LIMIT 30"""
    )
    suspend fun search(q: String, qNum: String): List<PaymentRow>
}

@Dao
interface ExpenseDao {
    @Insert suspend fun insert(e: Expense): Long
    @Update suspend fun update(e: Expense)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun byId(id: Long): Expense?

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND spentAt BETWEEN :from AND :to ORDER BY spentAt DESC")
    suspend fun rangeList(from: Long, to: Long): List<Expense>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE deletedAt IS NULL AND spentAt BETWEEN :from AND :to")
    fun observeSum(from: Long, to: Long): Flow<Long>

    @Query("UPDATE expenses SET deletedAt = :at WHERE id = :id")
    suspend fun softDelete(id: Long, at: Long)

    @Query("UPDATE expenses SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM expenses WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Expense>>

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deletePermanently(id: Long)

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND (title LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%' OR CAST(amount AS TEXT) LIKE '%' || :qNum || '%') ORDER BY spentAt DESC LIMIT 30")
    suspend fun search(q: String, qNum: String): List<Expense>
}

@Dao
interface AttachmentDao {
    @Insert suspend fun insert(a: Attachment): Long

    @Query("UPDATE attachments SET storedName = :storedName, fileName = :fileName, mimeType = :mime, note = :note WHERE id = :id")
    suspend fun updateInfo(id: Long, storedName: String, fileName: String, mime: String, note: String)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM attachments WHERE expenseId = :expenseId ORDER BY id")
    suspend fun forExpense(expenseId: Long): List<Attachment>

    @Query(
        """SELECT a.*, e.title AS expenseTitle FROM attachments a JOIN expenses e ON e.id = a.expenseId
           WHERE e.deletedAt IS NULL AND (a.fileName LIKE '%' || :q || '%' OR a.note LIKE '%' || :q || '%') LIMIT 30"""
    )
    suspend fun search(q: String): List<AttachmentHit>
}

@Dao
interface FinanceDao {
    /** Income (payments) and expenses in one chronological list; no duplicated rows, no double counting. */
    @Query(
        """SELECT 'INCOME' AS type, p.id AS refId, b.id AS bookingId, p.amount AS amount, c.name AS title, p.note AS note, p.paidAt AS occurredAt
           FROM payments p JOIN bookings b ON b.id = p.bookingId JOIN customers c ON c.id = b.customerId
           WHERE b.deletedAt IS NULL AND p.paidAt BETWEEN :from AND :to
           UNION ALL
           SELECT 'EXPENSE', e.id, 0, e.amount, e.title, e.note, e.spentAt
           FROM expenses e WHERE e.deletedAt IS NULL AND e.spentAt BETWEEN :from AND :to
           ORDER BY occurredAt DESC"""
    )
    fun transactions(from: Long, to: Long): Flow<List<TransactionRow>>
}

@Dao
interface ActivityDao {
    @Insert suspend fun insert(a: ActivityLog)

    @Query("SELECT * FROM activity_log ORDER BY createdAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ActivityLog>>

    @Query("DELETE FROM activity_log WHERE id NOT IN (SELECT id FROM activity_log ORDER BY createdAt DESC LIMIT 300)")
    suspend fun trim()
}

@Dao
interface SettingDao {
    @Query("SELECT settingValue FROM app_settings WHERE settingKey = :key")
    suspend fun get(key: String): String?

    @Query("SELECT settingValue FROM app_settings WHERE settingKey = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(s: AppSetting)
}
