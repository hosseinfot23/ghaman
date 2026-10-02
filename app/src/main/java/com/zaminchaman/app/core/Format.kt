package com.zaminchaman.app.core

import java.util.Locale

fun String.toPersianDigits(): String {
    val sb = StringBuilder(length)
    for (ch in this) sb.append(if (ch in '0'..'9') ('۰' + (ch - '0')) else ch)
    return sb.toString()
}

/** Converts Persian and Arabic-Indic digits to ASCII digits. */
fun String.normalizeDigits(): String {
    val sb = StringBuilder(length)
    for (ch in this) {
        sb.append(
            when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                else -> ch
            }
        )
    }
    return sb.toString()
}

/** Unifies Arabic letter variants so searches and duplicates behave correctly. */
fun String.normalizeFa(): String = replace('ي', 'ی').replace('ك', 'ک').trim()

fun Int.fa(): String = toString().toPersianDigits()

fun Long.formatMoney(): String =
    String.format(Locale.US, "%,d", this).replace(',', '٬').toPersianDigits()

fun moneyText(value: Long): String = if (value == 0L) "" else value.formatMoney()

/** Used by money text fields: keeps digits only and re-applies thousands separators. */
fun formatMoneyInput(raw: String): String {
    val digits = raw.normalizeDigits().filter { it in '0'..'9' }.trimStart('0').take(15)
    if (digits.isEmpty()) return ""
    return digits.toLong().formatMoney()
}

fun parseMoney(text: String): Long =
    text.normalizeDigits().filter { it in '0'..'9' }.toLongOrNull() ?: 0L

fun normalizePhone(raw: String): String = raw.normalizeDigits().filter { it in '0'..'9' }

/** Returns an error message, or null when the phone is empty or looks valid. */
fun validatePhone(phone: String): String? {
    val p = normalizePhone(phone)
    if (p.isEmpty()) return null
    return if (p.length in 10..11) null else "شماره موبایل معتبر نیست."
}
