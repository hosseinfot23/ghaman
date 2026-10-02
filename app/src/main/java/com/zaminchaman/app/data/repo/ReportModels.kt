package com.zaminchaman.app.data.repo

import com.zaminchaman.app.core.dayRangeMillis
import com.zaminchaman.app.core.formatDateTime
import com.zaminchaman.app.core.formatJalali
import com.zaminchaman.app.core.formatMinutes
import com.zaminchaman.app.core.formatMoney
import com.zaminchaman.app.core.fa
import com.zaminchaman.app.core.minuteOfDay
import com.zaminchaman.app.core.persianWeekday
import com.zaminchaman.app.core.toJalali
import com.zaminchaman.app.core.toLocalDate
import com.zaminchaman.app.core.Jalali
import com.zaminchaman.app.data.db.AppDatabase
import com.zaminchaman.app.data.db.BookingRow
import com.zaminchaman.app.data.db.BookingStatus
import com.zaminchaman.app.data.db.Expense
import com.zaminchaman.app.data.db.PaymentRow
import java.time.LocalDate

enum class Parity(val label: String) { ALL("همه روزها"), EVEN("روزهای زوج"), ODD("روزهای فرد") }

enum class ReportKind(val label: String) {
    INCOME("درآمد و پرداخت‌ها"),
    EXPENSE("هزینه‌ها"),
    RENTAL("اجاره تایم"),
    DEBTS("بدهی‌ها"),
    SETTLED("تسویه‌شده‌ها"),
    SESSIONS("جلسات انجام‌شده")
}

/** All filters combine: date range AND parity AND weekdays (0 = شنبه ... 6 = جمعه; empty = every day). */
data class ReportFilter(
    val fromDay: Long,
    val toDay: Long,
    val parity: Parity = Parity.ALL,
    val weekdays: Set<Int> = emptySet()
) {
    fun accepts(date: LocalDate): Boolean {
        val day = date.toEpochDay()
        if (day < fromDay || day > toDay) return false
        if (weekdays.isNotEmpty() && date.persianWeekday() !in weekdays) return false
        val d = date.toJalali().day
        return when (parity) {
            Parity.ALL -> true
            Parity.EVEN -> d % 2 == 0
            Parity.ODD -> d % 2 == 1
        }
    }

    fun acceptsMillis(m: Long) = accepts(m.toLocalDate())

    fun rangeMillis() = dayRangeMillis(LocalDate.ofEpochDay(fromDay), LocalDate.ofEpochDay(toDay))

    fun describe(): String {
        val sb = StringBuilder()
        sb.append("از ${LocalDate.ofEpochDay(fromDay).formatJalali()} تا ${LocalDate.ofEpochDay(toDay).formatJalali()}")
        if (parity != Parity.ALL) sb.append(" | ${parity.label}")
        if (weekdays.isNotEmpty()) {
            sb.append(" | ").append(weekdays.sorted().joinToString("، ") { Jalali.weekdayNames[it] })
        }
        return sb.toString()
    }
}

class ReportTable(
    val title: String,
    val headers: List<String>,
    /** Cells are Long (money, shown as number in Excel) or String. */
    val rows: List<List<Any>>,
    val weights: List<Float>,
    val footer: String?
)

class ReportData(
    val filter: ReportFilter,
    val bookings: List<BookingRow>,
    val payments: List<PaymentRow>,
    val expenses: List<Expense>
) {
    private val live = bookings.filter { it.booking.status != BookingStatus.CANCELLED }
    val income = payments.sumOf { it.payment.amount }
    val expenseTotal = expenses.sumOf { it.amount }
    val net = income - expenseTotal
    val bookingCount = live.size
    val rentalTotal = live.sumOf { it.booking.totalAmount }
    val debtRows = live.filter { it.debt > 0 }
    val debtTotal = debtRows.sumOf { it.debt }
    val settledRows = live.filter { it.debt == 0L }
    val doneRows = bookings.filter { it.booking.status == BookingStatus.DONE }

    fun summaryLines(): List<Pair<String, String>> = listOf(
        "درآمد (پرداخت‌ها)" to "${income.formatMoney()} تومان",
        "هزینه‌ها" to "${expenseTotal.formatMoney()} تومان",
        "سود خالص" to "${net.formatMoney()} تومان",
        "تعداد رزروها" to bookingCount.fa(),
        "جلسات انجام‌شده" to doneRows.size.fa(),
        "مجموع مبلغ اجاره" to "${rentalTotal.formatMoney()} تومان",
        "مجموع بدهی" to "${debtTotal.formatMoney()} تومان (${debtRows.size.fa()} مورد)",
        "تسویه‌شده‌ها" to "${settledRows.size.fa()} مورد"
    )

    private fun bookingTable(title: String, rows: List<BookingRow>): ReportTable = ReportTable(
        title,
        listOf("تاریخ", "ساعت", "مشتری", "مبلغ کل", "پرداخت", "بدهی", "وضعیت"),
        rows.map {
            listOf(
                it.booking.startAt.toLocalDate().formatJalali(),
                formatMinutes(it.booking.startAt.minuteOfDay()) + " - " + formatMinutes(it.booking.endAt.minuteOfDay()),
                it.customerName, it.booking.totalAmount, it.paid, it.debt, BookingStatus.label(it.booking.status)
            )
        },
        listOf(1.2f, 1.5f, 1.6f, 1.2f, 1.2f, 1.1f, 1f),
        "تعداد: ${rows.size.fa()}   |   جمع مبلغ: ${rows.sumOf { it.booking.totalAmount }.formatMoney()}   |   جمع بدهی: ${rows.sumOf { it.debt }.formatMoney()}"
    )

    fun tables(kinds: Set<ReportKind>): List<ReportTable> {
        val out = mutableListOf<ReportTable>()
        for (k in ReportKind.values()) {
            if (k !in kinds) continue
            out += when (k) {
                ReportKind.INCOME -> ReportTable(
                    k.label,
                    listOf("تاریخ و ساعت", "مشتری", "مبلغ", "توضیحات"),
                    payments.map { listOf(it.payment.paidAt.formatDateTime(), it.customerName, it.payment.amount, it.payment.note) },
                    listOf(1.6f, 1.6f, 1.2f, 2f),
                    "تعداد: ${payments.size.fa()}   |   جمع درآمد: ${income.formatMoney()} تومان"
                )
                ReportKind.EXPENSE -> ReportTable(
                    k.label,
                    listOf("تاریخ و ساعت", "بابت", "مبلغ", "توضیحات"),
                    expenses.map { listOf(it.spentAt.formatDateTime(), it.title, it.amount, it.note) },
                    listOf(1.6f, 1.6f, 1.2f, 2f),
                    "تعداد: ${expenses.size.fa()}   |   جمع هزینه: ${expenseTotal.formatMoney()} تومان"
                )
                ReportKind.RENTAL -> bookingTable(k.label, live)
                ReportKind.DEBTS -> bookingTable(k.label, debtRows)
                ReportKind.SETTLED -> bookingTable(k.label, settledRows)
                ReportKind.SESSIONS -> bookingTable(k.label, doneRows)
            }
        }
        return out
    }
}

class ReportRepository(private val db: AppDatabase) {
    suspend fun build(filter: ReportFilter): ReportData {
        val (from, to) = filter.rangeMillis()
        return ReportData(
            filter,
            db.bookingDao().rangeRows(from, to).filter { filter.acceptsMillis(it.booking.startAt) },
            db.paymentDao().rangeRows(from, to).filter { filter.acceptsMillis(it.payment.paidAt) },
            db.expenseDao().rangeList(from, to).filter { filter.acceptsMillis(it.spentAt) }
        )
    }
}
