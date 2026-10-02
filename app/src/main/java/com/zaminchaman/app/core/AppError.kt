package com.zaminchaman.app.core

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteFullException
import java.io.IOException

/** Every error shown to the user is an AppError with a clear Persian message. */
sealed class AppError(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause) {
    class Validation(message: String) : AppError(message)
    class InvalidTimeRange : AppError("ساعت پایان باید بعد از ساعت شروع باشد.")
    class BookingConflict(who: String, from: String, to: String) :
        AppError("این بازه با رزرو دیگری تداخل دارد: $who، از ساعت $from تا $to.")
    class InsufficientStorage : AppError("حافظه‌ی گوشی کافی نیست. مقداری فضا آزاد کنید و دوباره تلاش کنید.")
    class CorruptBackup : AppError("فایل پشتیبان خراب یا ناقص است و قابل استفاده نیست.")
    class IncompatibleBackup :
        AppError("این فایل پشتیبان با نسخه‌ی جدیدتری از برنامه ساخته شده است. ابتدا برنامه را به‌روزرسانی کنید.")
    class RestoreFailed(cause: Throwable? = null) :
        AppError("بازیابی اطلاعات کامل نشد. اطلاعات قبلی شما دست‌نخورده باقی ماند.", cause)
    class MigrationFailed(cause: Throwable? = null) :
        AppError(
            "به‌روزرسانی ساختار اطلاعات با خطا مواجه شد. اطلاعات شما حذف نشده است. " +
                "برنامه را ببندید و دوباره باز کنید؛ در صورت تکرار، از نسخه‌ی قبلی برنامه استفاده کنید.",
            cause
        )
    class AttachmentMissing : AppError("فایل این فاکتور پیدا نشد؛ ممکن است حذف شده باشد.")
    class InUse(message: String) : AppError(message)
}

fun isNoSpace(e: Throwable): Boolean {
    val m = e.message ?: return false
    return m.contains("ENOSPC") || m.contains("No space left", ignoreCase = true)
}

fun Throwable.toUserMessage(): String = when (this) {
    is AppError -> userMessage
    is SQLiteFullException -> AppError.InsufficientStorage().userMessage
    is SQLiteConstraintException -> "این مورد به اطلاعات دیگری وابسته است و حذف یا تغییر آن ممکن نیست."
    is IOException -> if (isNoSpace(this)) AppError.InsufficientStorage().userMessage
    else "خواندن یا نوشتن فایل با خطا مواجه شد. دوباره تلاش کنید."
    else -> "خطای غیرمنتظره‌ای رخ داد. لطفاً دوباره تلاش کنید."
}
