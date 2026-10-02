package com.zaminchaman.app

import com.zaminchaman.app.data.db.Contract
import com.zaminchaman.app.data.db.PatternType
import com.zaminchaman.app.domain.matches
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun contract(type: String, mask: Int = 0, end: LocalDate? = null) = Contract(
        id = 1, customerId = 1, patternType = type, weekdayMask = mask,
        startMinute = 19 * 60, endMinute = 20 * 60,
        startDay = LocalDate.of(2026, 9, 1).toEpochDay(), endDay = end?.toEpochDay()
    )

    @Test fun oddAndEvenFollowJalaliDayOfMonth() {
        // 2026-09-23 = 1405/07/01 (odd), 2026-09-24 = 1405/07/02 (even)
        assertTrue(contract(PatternType.ODD).matches(LocalDate.of(2026, 9, 23)))
        assertFalse(contract(PatternType.ODD).matches(LocalDate.of(2026, 9, 24)))
        assertTrue(contract(PatternType.EVEN).matches(LocalDate.of(2026, 9, 24)))
    }

    @Test fun weekdayMask() {
        val wednesday = contract(PatternType.WEEKDAYS, mask = 1 shl 4)
        assertTrue(wednesday.matches(LocalDate.of(2026, 9, 23)))
        assertFalse(wednesday.matches(LocalDate.of(2026, 9, 24)))
    }

    @Test fun respectsStartAndEnd() {
        val c = contract(PatternType.WEEKDAYS, mask = 0b1111111, end = LocalDate.of(2026, 9, 30))
        assertFalse(c.matches(LocalDate.of(2026, 8, 31)))
        assertTrue(c.matches(LocalDate.of(2026, 9, 30)))
        assertFalse(c.matches(LocalDate.of(2026, 10, 1)))
    }
}
