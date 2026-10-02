package com.zaminchaman.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.data.db.*
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.DebtRed
import com.zaminchaman.app.ui.theme.MutedGray
import com.zaminchaman.app.ui.theme.SettledGreen
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class BookingEditViewModel(
    private val c: AppContainer,
    val id: Long,
    private val contractId: Long,
    private val dayArg: Long
) : ViewModel() {
    val picker = CustomerPickerState()
    var date by mutableStateOf(LocalDate.now())
    var startMin by mutableIntStateOf(0)
    var endMin by mutableIntStateOf(0)
    var total by mutableStateOf("")
    var paidNow by mutableStateOf("")
    var note by mutableStateOf("")
    var status by mutableStateOf(BookingStatus.SCHEDULED)
    var contract: Contract? by mutableStateOf(null)
    var existing: BookingRow? by mutableStateOf(null)
    var loaded by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error: String? by mutableStateOf(null)

    val payments: StateFlow<List<Payment>> =
        (if (id > 0) c.db.paymentDao().forBooking(id) else flowOf(emptyList<Payment>()))
            .asState(viewModelScope, emptyList())

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        try {
            picker.all = c.customers.active()
            val now = LocalTime.now()
            val day = if (dayArg > 0) LocalDate.ofEpochDay(dayArg) else LocalDate.now()
            when {
                id > 0 -> {
                    val row = c.db.bookingDao().rowById(id)
                    if (row == null) { error = "این رزرو پیدا نشد."; return }
                    existing = row
                    val b = row.booking
                    date = b.startAt.toLocalDate(); startMin = b.startAt.minuteOfDay(); endMin = b.endAt.minuteOfDay()
                    total = moneyText(b.totalAmount); note = b.note; status = b.status
                    picker.name = row.customerName; picker.phone = row.customerPhone; picker.selectedId = b.customerId
                    contract = b.contractId?.let { c.db.contractDao().byId(it) }
                }
                contractId > 0 -> {
                    val k = c.db.contractDao().rowById(contractId)
                    if (k == null) { error = "این قرارداد پیدا نشد."; return }
                    contract = k.contract
                    val cust = c.customers.byId(k.contract.customerId)
                    picker.name = k.customerName; picker.phone = cust?.phone.orEmpty(); picker.selectedId = k.contract.customerId
                    date = day; startMin = k.contract.startMinute; endMin = k.contract.endMinute
                    total = moneyText(k.contract.defaultPrice); status = BookingStatus.DONE
                }
                else -> {
                    date = day
                    startMin = now.hour * 60 + now.minute
                    endMin = minOf(startMin + 60, 23 * 60 + 59)
                }
            }
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally {
            loaded = true
        }
    }

    fun save(onDone: () -> Unit) {
        if (saving) return
        viewModelScope.launch {
            saving = true
            try {
                val customerId = c.customers.resolve(picker.selectedId, picker.name, picker.phone)
                val base = existing?.booking
                val booking = Booking(
                    id = base?.id ?: 0,
                    customerId = customerId,
                    contractId = base?.contractId ?: contract?.id,
                    startAt = millisOf(date, startMin),
                    endAt = millisOf(date, endMin),
                    totalAmount = parseMoney(total),
                    status = status,
                    note = note.trim(),
                    createdAt = base?.createdAt ?: System.currentTimeMillis()
                )
                c.bookings.save(booking, if (base == null) parseMoney(paidNow) else 0L)
                onDone()
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                saving = false
            }
        }
    }

    fun addPayment(amount: Long, paidAt: Long, note: String) {
        viewModelScope.launch {
            try { c.bookings.addPayment(id, amount, paidAt, note) } catch (e: Exception) { error = e.toUserMessage() }
        }
    }

    fun deletePayment(p: Payment) {
        viewModelScope.launch {
            try { c.bookings.deletePayment(p.id) } catch (e: Exception) { error = e.toUserMessage() }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try { c.bookings.softDelete(id); onDone() } catch (e: Exception) { error = e.toUserMessage() }
        }
    }
}

@Composable
fun BookingEditScreen(id: Long, contractId: Long, day: Long, onBack: () -> Unit, onOpenContract: (Long) -> Unit) {
    val vm = appViewModel { BookingEditViewModel(it, id, contractId, day) }
    val payments by vm.payments.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var addingPayment by remember { mutableStateOf(false) }
    var payToDelete by remember { mutableStateOf<Payment?>(null) }

    val totalValue = parseMoney(vm.total)
    val paidValue = if (id > 0) payments.sumOf { it.amount } else parseMoney(vm.paidNow)
    val debt = (totalValue - paidValue).coerceAtLeast(0)
    val isContractSession = vm.contract != null

    ScreenScaffold(title = if (id > 0) "ویرایش رزرو" else "رزرو جدید", onBack = onBack) { pad ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            vm.contract?.let { k ->
                SectionCard {
                    Text("این یک جلسه‌ی رزرو دوره‌ای است.")
                    TextButton(onClick = { onOpenContract(k.id) }) { Text("مشاهده‌ی قرارداد دوره‌ای") }
                }
            }
            CustomerPicker(vm.picker, enabled = !isContractSession)
            JalaliDateField("تاریخ", vm.date, { vm.date = it })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField("ساعت شروع", vm.startMin, { vm.startMin = it }, Modifier.weight(1f))
                TimeField("ساعت پایان", vm.endMin, { vm.endMin = it }, Modifier.weight(1f))
            }
            MoneyField("مبلغ کل", vm.total, { vm.total = it })
            if (id <= 0) MoneyField("مبلغ پرداخت‌شده تا الان", vm.paidNow, { vm.paidNow = it })

            SectionCard(title = "وضعیت مالی") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("بدهی: ${debt.formatMoney()} تومان", color = if (debt > 0) DebtRed else SettledGreen,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    DebtBadge(debt)
                }
                if (id > 0) {
                    Text("جمع پرداخت‌ها: ${paidValue.formatMoney()} تومان", color = MutedGray)
                    payments.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.amount.formatMoney() + " تومان")
                                Text(
                                    p.paidAt.formatDateTime() + if (p.note.isNotBlank()) "  |  " + p.note else "",
                                    style = MaterialTheme.typography.bodySmall, color = MutedGray
                                )
                            }
                            IconButton(onClick = { payToDelete = p }) { Icon(Icons.Default.Delete, "حذف پرداخت", tint = DebtRed) }
                        }
                    }
                    OutlinedButton(onClick = { addingPayment = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("ثبت پرداخت جدید")
                    }
                }
            }

            Text("وضعیت", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(BookingStatus.SCHEDULED, BookingStatus.DONE, BookingStatus.CANCELLED).forEach { s ->
                    FilterChip(selected = vm.status == s, onClick = { vm.status = s }, label = { Text(BookingStatus.label(s)) })
                }
            }
            OutlinedTextField(vm.note, { vm.note = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            Button(
                onClick = { vm.save { toast(ctx, "ذخیره شد."); onBack() } },
                enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("ذخیره") }
            if (id > 0) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("حذف رزرو", color = DebtRed)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            "حذف رزرو", "این رزرو به سطل بازیافت منتقل می‌شود و از محاسبات مالی کنار می‌رود. ادامه می‌دهید؟",
            "حذف", onConfirm = {
                confirmDelete = false
                vm.delete { toast(ctx, "رزرو به سطل بازیافت منتقل شد."); onBack() }
            }, onDismiss = { confirmDelete = false }, danger = true
        )
    }
    payToDelete?.let { p ->
        ConfirmDialog(
            "حذف پرداخت", "پرداخت ${p.amount.formatMoney()} تومانی حذف شود؟ بدهی این رزرو دوباره محاسبه می‌شود.",
            "حذف", onConfirm = { vm.deletePayment(p); payToDelete = null },
            onDismiss = { payToDelete = null }, danger = true
        )
    }
    if (addingPayment) {
        AddPaymentDialog(
            remaining = debt, onDismiss = { addingPayment = false },
            onAdd = { a, t, n -> vm.addPayment(a, t, n); addingPayment = false }
        )
    }
    vm.error?.let { MessageDialog(it) { vm.error = null } }
}

@Composable
private fun AddPaymentDialog(remaining: Long, onDismiss: () -> Unit, onAdd: (Long, Long, String) -> Unit) {
    var amount by remember { mutableStateOf(moneyText(remaining)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var minute by remember { mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute }) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت پرداخت") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("مبلغ", amount, { amount = it })
                JalaliDateField("تاریخ پرداخت", date, { date = it })
                TimeField("ساعت پرداخت", minute, { minute = it })
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(parseMoney(amount), millisOf(date, minute), note) }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
