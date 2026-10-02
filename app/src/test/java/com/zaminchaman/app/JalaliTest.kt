package com.zaminchaman.app

import com.zaminchaman.app.core.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class JalaliTest {
    @Test fun knownDates() {
        assertEquals(JalaliDate(1405, 7, 1), LocalDate.of(2026, 9, 23).toJalali())
        assertEquals(JalaliDate(1405, 1, 1), LocalDate.of(2026, 3, 21).toJalali())
        assertEquals(JalaliDate(1404, 1, 1), LocalDate.of(2025, 3, 21).toJalali())
        assertEquals(LocalDate.of(2026, 9, 23), JalaliDate(1405, 7, 1).toLocalDate())
    }

    @Test fun leapYearHasThirtyDayEsfand() {
        assertEquals(30, Jalali.monthLength(1403, 12))
        assertEquals(JalaliDate(1403, 12, 30), LocalDate.of(2025, 3, 20).toJalali())
    }

    @Test fun roundTripEveryDay() {
        var d = LocalDate.of(1995, 1, 1)
        val end = LocalDate.of(2055, 1, 1)
        while (d.isBefore(end)) {
            assertEquals(d, d.toJalali().toLocalDate())
            d = d.plusDays(1)
        }
    }

    @Test fun weekdayIndexSaturdayFirst() {
        assertEquals(4, LocalDate.of(2026, 9, 23).persianWeekday()) // Wednesday
        assertEquals(0, LocalDate.of(2026, 9, 26).persianWeekday()) // Saturday
        assertEquals(6, LocalDate.of(2026, 9, 25).persianWeekday()) // Friday
    }
}
