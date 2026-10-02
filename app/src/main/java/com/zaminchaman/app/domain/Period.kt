package com.zaminchaman.app.domain

import com.zaminchaman.app.core.Jalali
import com.zaminchaman.app.core.JalaliDate
import com.zaminchaman.app.core.persianWeekday
import com.zaminchaman.app.core.toJalali
import java.time.LocalDate

enum class Period(val label: String) {
    TODAY("امروز"), WEEK("این هفته"), MONTH("این ماه"), QUARTER("۳ ماه اخیر"), YEAR("امسال");

    fun range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
        TODAY -> today to today
        WEEK -> {
            val start = today.minusDays(today.persianWeekday().toLong())
            start to start.plusDays(6)
        }
        MONTH -> {
            val j = today.toJalali()
            JalaliDate(j.year, j.month, 1).toLocalDate() to
                JalaliDate(j.year, j.month, Jalali.monthLength(j.year, j.month)).toLocalDate()
        }
        QUARTER -> Jalali.plusMonths(today, -3).plusDays(1) to today
        YEAR -> {
            val j = today.toJalali()
            JalaliDate(j.year, 1, 1).toLocalDate() to
                JalaliDate(j.year, 12, Jalali.monthLength(j.year, 12)).toLocalDate()
        }
    }
}
