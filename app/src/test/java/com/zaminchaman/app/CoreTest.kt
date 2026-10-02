package com.zaminchaman.app

import com.zaminchaman.app.core.*
import org.junit.Assert.*
import org.junit.Test

class CoreTest {
    @Test fun moneyFormatAndParse() {
        assertEquals("۱٬۲۵۰٬۰۰۰", 1_250_000L.formatMoney())
        assertEquals(1_250_000L, parseMoney("۱٬۲۵۰٬۰۰۰"))
        assertEquals(1_250_000L, parseMoney("1,250,000"))
        assertEquals("۱٬۰۰۰", formatMoneyInput("abc1000"))
        assertEquals("", formatMoneyInput("000"))
    }

    @Test fun digitsAndPhone() {
        assertEquals("09121234567", normalizePhone("۰۹۱۲۱۲۳۴۵۶۷"))
        assertNull(validatePhone(""))
        assertNull(validatePhone("09121234567"))
        assertNotNull(validatePhone("123"))
    }

    @Test fun passwordHashing() {
        val h = PasswordHasher.hash("s3cret", iterations = 1000)
        assertTrue(PasswordHasher.verify("s3cret", h.hash, h.salt, h.iterations))
        assertFalse(PasswordHasher.verify("wrong", h.hash, h.salt, h.iterations))
        val h2 = PasswordHasher.hash("s3cret", iterations = 1000)
        assertNotEquals(h.hash, h2.hash) // random salt
    }

    @Test fun reportFilterCombinesRules() {
        val from = java.time.LocalDate.of(2026, 9, 23).toEpochDay()
        val f = com.zaminchaman.app.data.repo.ReportFilter(
            from, from + 6, com.zaminchaman.app.data.repo.Parity.ODD, setOf(4) // Wednesday + odd day
        )
        assertTrue(f.accepts(java.time.LocalDate.of(2026, 9, 23)))   // Wed, 1405/07/01
        assertFalse(f.accepts(java.time.LocalDate.of(2026, 9, 24)))  // Thu
        assertFalse(f.accepts(java.time.LocalDate.of(2026, 9, 30)))  // out of range
    }
}
