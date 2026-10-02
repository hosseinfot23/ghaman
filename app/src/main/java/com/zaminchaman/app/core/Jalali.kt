package com.zaminchaman.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    fun toLocalDate(): LocalDate = LocalDate.ofEpochDay(Jalali.toEpochDay(year, month, day))
    fun formatLatin(): String = String.format(Locale.US, "%04d/%02d/%02d", year, month, day)
    fun format(): String = formatLatin().toPersianDigits()
}

/** Jalali (Persian) calendar conversion based on the well-known jalaali algorithm. */
object Jalali {
    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    /** Saturday-first week, index 0 = شنبه. */
    val weekdayNames = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")

    private class Cal(val leap: Int, val gy: Int, val march: Int)

    private fun cal(jy: Int): Cal {
        val bl = breaks.size
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        require(jy >= jp && jy < breaks[bl - 1]) { "Invalid Jalali year $jy" }
        var jump = 0
        for (i in 1 until bl) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if ((jump % 33) == 4 && (jump - n) == 4) leapJ += 1
        val leapG = (gy / 4) - (((gy / 100) + 1) * 3 / 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return Cal(leap, gy, march)
    }

    fun toEpochDay(jy: Int, jm: Int, jd: Int): Long {
        val r = cal(jy)
        return LocalDate.of(r.gy, 3, 1).toEpochDay() + (r.march - 1) +
            (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
    }

    fun fromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
        val gy = LocalDate.ofEpochDay(epochDay).year
        var jy = gy - 621
        val r = cal(jy)
        val first = LocalDate.of(gy, 3, 1).toEpochDay() + (r.march - 1)
        var k = (epochDay - first).toInt()
        if (k >= 0) {
            if (k <= 185) return Triple(jy, 1 + k / 31, k % 31 + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return Triple(jy, 7 + k / 30, k % 30 + 1)
    }

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (cal(jy).leap == 0) 30 else 29
    }

    fun plusMonths(date: LocalDate, months: Int): LocalDate {
        val j = date.toJalali()
        val total = j.year * 12 + (j.month - 1) + months
        val y = Math.floorDiv(total, 12)
        val m = Math.floorMod(total, 12) + 1
        return JalaliDate(y, m, minOf(j.day, monthLength(y, m))).toLocalDate()
    }
}

fun LocalDate.toJalali(): JalaliDate {
    val (y, m, d) = Jalali.fromEpochDay(toEpochDay())
    return JalaliDate(y, m, d)
}

/** 0 = شنبه ... 6 = جمعه */
fun LocalDate.persianWeekday(): Int = (dayOfWeek.value + 1) % 7

fun LocalDate.weekdayName(): String = Jalali.weekdayNames[persianWeekday()]
fun LocalDate.formatJalali(): String = toJalali().format()
fun LocalDate.formatJalaliLong(): String {
    val j = toJalali()
    return "${weekdayName()} ${j.day.fa()} ${Jalali.monthNames[j.month - 1]} ${j.year.fa()}"
}

// ---------- time helpers: all instants are stored as epoch milliseconds ----------

fun zone(): ZoneId = ZoneId.systemDefault()

fun millisOf(date: LocalDate, minuteOfDay: Int): Long =
    date.atStartOfDay(zone()).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(zone()).toLocalDate()

fun Long.minuteOfDay(): Int {
    val t = Instant.ofEpochMilli(this).atZone(zone())
    return t.hour * 60 + t.minute
}

fun formatMinutes(minuteOfDay: Int): String =
    String.format(Locale.US, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60).toPersianDigits()

fun Long.formatTime(): String = formatMinutes(minuteOfDay())
fun Long.formatDateTime(): String = "${toLocalDate().formatJalali()}  ${formatTime()}"

fun dayRangeMillis(from: LocalDate, to: LocalDate): Pair<Long, Long> =
    from.atStartOfDay(zone()).toInstant().toEpochMilli() to
        (to.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli() - 1)
