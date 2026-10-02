package com.zaminchaman.app.domain

import com.zaminchaman.app.core.Jalali
import com.zaminchaman.app.core.persianWeekday
import com.zaminchaman.app.core.toJalali
import com.zaminchaman.app.data.db.Contract
import com.zaminchaman.app.data.db.PatternType
import java.time.LocalDate

/** Does the contract's pattern fall on this date? (Says nothing about sessions that really happened.) */
fun Contract.matches(date: LocalDate): Boolean {
    val day = date.toEpochDay()
    if (day < startDay) return false
    if (endDay != null && day > endDay) return false
    return when (patternType) {
        PatternType.EVEN -> date.toJalali().day % 2 == 0
        PatternType.ODD -> date.toJalali().day % 2 == 1
        else -> ((weekdayMask shr date.persianWeekday()) and 1) == 1
    }
}

fun Contract.describePattern(): String = when (patternType) {
    PatternType.EVEN -> "روزهای زوج"
    PatternType.ODD -> "روزهای فرد"
    else -> Jalali.weekdayNames.filterIndexed { i, _ -> ((weekdayMask shr i) and 1) == 1 }.joinToString(" + ")
}
