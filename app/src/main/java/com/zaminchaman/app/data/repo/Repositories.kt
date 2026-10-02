package com.zaminchaman.app.data.repo

import androidx.room.withTransaction
import com.zaminchaman.app.core.AppError
import com.zaminchaman.app.core.PasswordHasher
import com.zaminchaman.app.core.formatMinutes
import com.zaminchaman.app.core.normalizeDigits
import com.zaminchaman.app.core.normalizeFa
import com.zaminchaman.app.core.normalizePhone
import com.zaminchaman.app.core.dayRangeMillis
import com.zaminchaman.app.core.minuteOfDay
import com.zaminchaman.app.core.validatePhone
import com.zaminchaman.app.core.formatDateTime
import com.zaminchaman.app.core.formatJalali
import com.zaminchaman.app.core.formatMoney
import com.zaminchaman.app.core.toLocalDate
import com.zaminchaman.app.data.db.*
import com.zaminchaman.app.domain.matches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

class ActivityLogger(private val dao: ActivityDao) {
    suspend fun log(text: String) {
        dao.insert(ActivityLog(text = text))
        dao.trim()
    }
}

class CustomerRepository(private val db: AppDatabase, private val log: ActivityLogger) {
    private val dao = db.customerDao()

    fun observeRows(): Flow<List<CustomerRow>> = dao.observeRows()
    suspend fun active(): List<Customer> = dao.active()
    suspend fun byId(id: Long): Customer? = dao.byId(id)

    private fun clean(name: String, phone: String): Pair<String, String> {
        val n = name.normalizeFa()
        if (n.isBlank()) throw AppError.Validation("نام مشتری را وارد کنید.")
        validatePhone(phone)?.let { throw AppError.Validation(it) }
        return n to normalizePhone(phone)
    }

    /** Used by the booking / contract forms: reuse the selected customer or create a new one. */
    suspend fun resolve(selectedId: Long?, name: String, phone: String): Long {
        if (selectedId != null && dao.byId(selectedId) != null) return selectedId
        val (n, p) = clean(name, phone)
        dao.findExact(n, p)?.let { return it.id }
        val id = dao.insert(Customer(name = n, phone = p))
        log.log("مشتری جدید ثبت شد: $n")
        return id
    }

    suspend fun add(name: String, phone: String): Long {
        val (n, p) = clean(name, phone)
        val id = dao.insert(Customer(name = n, phone = p))
        log.log("مشتری جدید ثبت شد: $n")
        return id
    }

    suspend fun update(id: Long, name: String, phone: String) {
        val old = dao.byId(id) ?: throw AppError.Validation("این مشتری پیدا نشد.")
        val (n, p) = clean(name, phone)
        dao.update(old.copy(name = n, phone = p))
    }

    suspend fun softDelete(id: Long) {
        if (dao.activeBookingCount(id) > 0 || dao.activeContractCount(id) > 0) {
            throw AppError.InUse("این مشتری رزرو یا قرارداد فعال دارد. ابتدا آن‌ها را حذف کنید.")
        }
        dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun restore(id: Long) = dao.restore(id)
    fun observeTrash() = dao.observeTrash()

    suspend fun deletePermanently(id: Long) {
        try {
            dao.deletePermanently(id)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            throw AppError.InUse("این مشتری هنوز در رزروها یا قراردادهای حذف‌شده استفاده شده است.")
        }
    }
}

class BookingRepository(private val db: AppDatabase, private val log: ActivityLogger) {
    private val dao = db.bookingDao()
    private val payments = db.paymentDao()
    private val customers = db.customerDao()

    private suspend fun assertNoConflict(start: Long, end: Long, excludeId: Long) {
        val other = dao.firstOverlap(start, end, excludeId) ?: return
        throw AppError.BookingConflict(
            other.customerName,
            formatMinutes(other.booking.startAt.minuteOfDay()),
            formatMinutes(other.booking.endAt.minuteOfDay())
        )
    }

    /** Creates or updates a booking atomically; optionally records the first payment. */
    suspend fun save(booking: Booking, initialPaid: Long = 0, paidAt: Long = System.currentTimeMillis()): Long =
        db.withTransaction {
            if (booking.endAt <= booking.startAt) throw AppError.InvalidTimeRange()
            if (booking.totalAmount < 0) throw AppError.Validation("مبلغ کل نمی‌تواند منفی باشد.")
            if (initialPaid > booking.totalAmount) throw AppError.Validation("مبلغ پرداختی بیشتر از مبلغ کل است.")
            if (booking.status != BookingStatus.CANCELLED) assertNoConflict(booking.startAt, booking.endAt, booking.id)
            val name = customers.byId(booking.customerId)?.name.orEmpty()
            val id = if (booking.id == 0L) {
                dao.insert(booking).also { log.log("رزرو جدید ثبت شد: $name") }
            } else {
                if (booking.totalAmount < payments.sumForBooking(booking.id)) {
                    throw AppError.Validation("مبلغ کل نمی‌تواند کمتر از مجموع پرداخت‌های ثبت‌شده باشد.")
                }
                dao.update(booking)
                booking.id
            }
            if (initialPaid > 0) {
                payments.insert(Payment(bookingId = id, amount = initialPaid, paidAt = paidAt))
                log.log("پرداخت ثبت شد: $name")
            }
            id
        }

    suspend fun addPayment(bookingId: Long, amount: Long, paidAt: Long, note: String) = db.withTransaction {
        val b = dao.rowById(bookingId) ?: throw AppError.Validation("این رزرو پیدا نشد.")
        if (amount <= 0) throw AppError.Validation("مبلغ پرداخت را وارد کنید.")
        if (b.paid + amount > b.booking.totalAmount) {
            throw AppError.Validation("مجموع پرداخت‌ها از مبلغ کل بیشتر می‌شود.")
        }
        payments.insert(Payment(bookingId = bookingId, amount = amount, paidAt = paidAt, note = note.trim()))
        log.log("پرداخت ثبت شد: ${b.customerName}")
    }

    suspend fun deletePayment(id: Long) = payments.delete(id)

    suspend fun softDelete(id: Long) {
        val row = dao.rowById(id)
        dao.softDelete(id, System.currentTimeMillis())
        if (row != null) log.log("رزرو حذف شد: ${row.customerName}")
    }

    suspend fun restore(id: Long) = db.withTransaction {
        val row = dao.rowById(id) ?: return@withTransaction
        if (row.booking.status != BookingStatus.CANCELLED) {
            assertNoConflict(row.booking.startAt, row.booking.endAt, id)
        }
        customers.restore(row.booking.customerId)
        dao.restore(id)
    }

    suspend fun deletePermanently(id: Long) = dao.deletePermanently(id)
    fun observeTrash() = dao.observeTrash()
}

class ContractRepository(private val db: AppDatabase, private val log: ActivityLogger) {
    private val dao = db.contractDao()

    fun observeAll() = dao.observeAll()
    fun observeTrash() = dao.observeTrash()

    suspend fun save(c: Contract): Long {
        if (c.patternType == PatternType.WEEKDAYS && c.weekdayMask == 0) {
            throw AppError.Validation("حداقل یک روز هفته را انتخاب کنید.")
        }
        if (c.endMinute <= c.startMinute) throw AppError.InvalidTimeRange()
        if (c.endDay != null && c.endDay < c.startDay) {
            throw AppError.Validation("تاریخ پایان نمی‌تواند قبل از تاریخ شروع باشد.")
        }
        if (c.defaultPrice < 0) throw AppError.Validation("مبلغ نمی‌تواند منفی باشد.")
        return if (c.id == 0L) {
            dao.insert(c).also { log.log("رزرو دوره‌ای جدید ثبت شد") }
        } else {
            dao.update(c); c.id
        }
    }

    suspend fun softDelete(id: Long) = dao.softDelete(id, System.currentTimeMillis())

    suspend fun restore(id: Long) = db.withTransaction {
        val c = dao.byId(id) ?: return@withTransaction
        db.customerDao().restore(c.customerId)
        dao.restore(id)
    }

    suspend fun deletePermanently(id: Long) = dao.deletePermanently(id)

    /** Contracts whose pattern falls on [date] and that have no logged session that day yet. */
    suspend fun expectedFor(date: LocalDate): List<ContractRow> {
        val (from, to) = dayRangeMillis(date, date)
        return dao.activeRows().filter {
            it.contract.matches(date) &&
                db.bookingDao().countForContractBetween(it.contract.id, from, to) == 0
        }
    }
}

data class AttachmentDraft(
    val id: Long,
    val storedName: String,
    val fileName: String,
    val mime: String,
    val note: String
)

class FinanceRepository(
    private val db: AppDatabase,
    private val store: AttachmentStore,
    private val log: ActivityLogger
) {
    private val expenses = db.expenseDao()
    private val attachments = db.attachmentDao()

    suspend fun attachmentsOf(expenseId: Long): List<Attachment> = attachments.forExpense(expenseId)

    suspend fun saveExpense(expense: Expense, drafts: List<AttachmentDraft>, removedIds: List<Long>): Long =
        db.withTransaction {
            if (expense.amount <= 0) throw AppError.Validation("مبلغ هزینه را وارد کنید.")
            if (expense.title.isBlank()) throw AppError.Validation("دلیل هزینه را وارد کنید.")
            val id = if (expense.id == 0L) {
                expenses.insert(expense).also { log.log("هزینه ثبت شد: ${expense.title}") }
            } else {
                expenses.update(expense); expense.id
            }
            removedIds.forEach { attachments.delete(it) }
            drafts.forEach { d ->
                if (d.id == 0L) {
                    attachments.insert(
                        Attachment(expenseId = id, storedName = d.storedName, fileName = d.fileName, mimeType = d.mime, note = d.note)
                    )
                } else {
                    attachments.updateInfo(d.id, d.storedName, d.fileName, d.mime, d.note)
                }
            }
            id
        }

    suspend fun softDelete(id: Long) = expenses.softDelete(id, System.currentTimeMillis())
    suspend fun restore(id: Long) = expenses.restore(id)
    fun observeTrash() = expenses.observeTrash()

    /** Removes the record and its invoice files for good (files are removed only after the DB commit). */
    suspend fun deletePermanently(id: Long) {
        val files = attachments.forExpense(id)
        expenses.deletePermanently(id)
        files.forEach { store.delete(it.storedName) }
    }
}

class AuthRepository(private val dao: SettingDao) {
    companion object {
        const val K_USER = "username"
        const val K_HASH = "pass_hash"
        const val K_SALT = "pass_salt"
        const val K_ITER = "pass_iter"
        const val K_LOCK = "auto_lock_sec"
        const val DEFAULT_LOCK = 300
    }

    private fun String.pw() = normalizeDigits()

    private suspend fun store(user: String, password: String) {
        val h = withContext(Dispatchers.Default) { PasswordHasher.hash(password.pw()) }
        dao.put(AppSetting(K_USER, user.trim()))
        dao.put(AppSetting(K_HASH, h.hash))
        dao.put(AppSetting(K_SALT, h.salt))
        dao.put(AppSetting(K_ITER, h.iterations.toString()))
    }

    /** First run only: creates the initial account. Existing accounts are never touched. */
    suspend fun ensureSeeded() {
        if (dao.get(K_USER) == null || dao.get(K_HASH) == null) store("admin", "123456")
        if (dao.get(K_LOCK) == null) dao.put(AppSetting(K_LOCK, DEFAULT_LOCK.toString()))
    }

    suspend fun username(): String = dao.get(K_USER).orEmpty()

    private suspend fun passwordMatches(password: String): Boolean {
        val hash = dao.get(K_HASH) ?: return false
        val salt = dao.get(K_SALT) ?: return false
        val iter = dao.get(K_ITER)?.toIntOrNull() ?: return false
        return withContext(Dispatchers.Default) { PasswordHasher.verify(password.pw(), hash, salt, iter) }
    }

    suspend fun verify(user: String, password: String): Boolean {
        val passOk = passwordMatches(password)
        val userOk = user.trim().equals(username(), ignoreCase = true)
        return passOk && userOk
    }

    suspend fun changeCredentials(currentPassword: String, newUser: String?, newPassword: String?) {
        if (!passwordMatches(currentPassword)) throw AppError.Validation("رمز عبور فعلی اشتباه است.")
        val user = newUser?.trim()?.takeIf { it.isNotEmpty() } ?: username()
        if (user.length < 3) throw AppError.Validation("نام کاربری باید حداقل ۳ حرف باشد.")
        if (newPassword != null && newPassword.length < 4) {
            throw AppError.Validation("رمز عبور جدید باید حداقل ۴ کاراکتر باشد.")
        }
        if (newPassword != null) {
            store(user, newPassword)
        } else {
            dao.put(AppSetting(K_USER, user))
        }
    }

    fun autoLockSeconds(): Flow<Int> = dao.observe(K_LOCK).map { it?.toIntOrNull() ?: DEFAULT_LOCK }
    suspend fun setAutoLock(seconds: Int) = dao.put(AppSetting(K_LOCK, seconds.toString()))
}

enum class SearchTarget { CUSTOMER, BOOKING, EXPENSE }

data class SearchResult(
    val group: String,
    val title: String,
    val subtitle: String,
    val target: SearchTarget,
    val id: Long
)

class SearchRepository(private val db: AppDatabase) {
    suspend fun search(raw: String): List<SearchResult> {
        val q = raw.normalizeFa()
        if (q.isEmpty()) return emptyList()
        val qNum = q.normalizeDigits().filter { it in '0'..'9' }.ifEmpty { "#" }
        val out = mutableListOf<SearchResult>()

        db.customerDao().search(q, qNum).forEach {
            out += SearchResult("مشتری", it.name, it.phone, SearchTarget.CUSTOMER, it.id)
        }
        db.bookingDao().search(q, qNum).forEach {
            val kind = if (it.booking.status == BookingStatus.DONE) "جلسه" else "رزرو"
            val sub = it.booking.startAt.toLocalDate().formatJalali() + "  " +
                formatMinutes(it.booking.startAt.minuteOfDay()) + " تا " +
                formatMinutes(it.booking.endAt.minuteOfDay())
            out += SearchResult(kind, it.customerName, sub, SearchTarget.BOOKING, it.booking.id)
        }
        db.paymentDao().search(q, qNum).forEach {
            val sub = it.payment.paidAt.formatDateTime() + "  " + it.payment.amount.formatMoney() + " تومان"
            out += SearchResult("تراکنش مالی (درآمد)", it.customerName, sub, SearchTarget.BOOKING, it.payment.bookingId)
        }
        db.expenseDao().search(q, qNum).forEach {
            val sub = it.spentAt.formatDateTime() + "  " + it.amount.formatMoney() + " تومان"
            out += SearchResult("تراکنش مالی (هزینه)", it.title, sub, SearchTarget.EXPENSE, it.id)
        }
        db.attachmentDao().search(q).forEach {
            out += SearchResult("فاکتور", it.attachment.fileName, it.expenseTitle, SearchTarget.EXPENSE, it.attachment.expenseId)
        }
        return out
    }
}
