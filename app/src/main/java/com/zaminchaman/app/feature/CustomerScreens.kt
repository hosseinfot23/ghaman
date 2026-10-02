package com.zaminchaman.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.data.db.BookingStatus
import com.zaminchaman.app.data.db.Customer
import com.zaminchaman.app.ui.Routes
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.DebtRed
import com.zaminchaman.app.ui.theme.MutedGray
import com.zaminchaman.app.ui.theme.SettledGreen
import kotlinx.coroutines.launch

class CustomersListViewModel(private val c: AppContainer) : ViewModel() {
    val rows = c.customers.observeRows().asState(viewModelScope, emptyList())
    var query by mutableStateOf("")
    var error: String? by mutableStateOf(null)

    fun add(name: String, phone: String) {
        viewModelScope.launch {
            try { c.customers.add(name, phone) } catch (e: Exception) { error = e.toUserMessage() }
        }
    }
}

@Composable
fun CustomersTab(nav: NavHostController, vm: CustomersListViewModel) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val q = vm.query.normalizeFa()
    val qNum = q.normalizeDigits()
    val shown = if (q.isEmpty()) rows else rows.filter {
        it.customer.name.contains(q) || (qNum.isNotEmpty() && it.customer.phone.contains(qNum))
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query, onValueChange = { vm.query = it }, singleLine = true,
            label = { Text("جستجوی نام یا شماره") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        if (shown.isEmpty()) EmptyState("مشتری‌ای پیدا نشد.")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(shown, key = { it.customer.id }) { r ->
                Card(
                    onClick = { nav.navigate(Routes.customer(r.customer.id)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.customer.name, fontWeight = FontWeight.Bold)
                            if (r.customer.phone.isNotEmpty()) {
                                Text(r.customer.phone.toPersianDigits(), style = MaterialTheme.typography.bodySmall, color = MutedGray)
                            }
                            if (r.debt > 0) Text("بدهی: ${r.debt.formatMoney()} تومان", color = DebtRed, style = MaterialTheme.typography.bodySmall)
                        }
                        DebtBadge(r.debt)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerEditDialog(initial: Customer?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "مشتری جدید" else "ویرایش مشتری") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    phone.toPersianDigits(), { phone = normalizePhone(it).take(11) },
                    label = { Text("شماره موبایل") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = DebtRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    name.isBlank() -> "نام مشتری را وارد کنید."
                    else -> validatePhone(phone)
                }
                if (error == null) onSave(name, phone)
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

class CustomerDetailViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    var customer: Customer? by mutableStateOf(null)
    var error: String? by mutableStateOf(null)
    val rows = c.db.bookingDao().observeForCustomer(id).asState(viewModelScope, emptyList())

    init { reload() }

    fun reload() {
        viewModelScope.launch { customer = c.customers.byId(id) }
    }

    fun update(name: String, phone: String) {
        viewModelScope.launch {
            try { c.customers.update(id, name, phone); reload() } catch (e: Exception) { error = e.toUserMessage() }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try { c.customers.softDelete(id); onDone() } catch (e: Exception) { error = e.toUserMessage() }
        }
    }
}

@Composable
fun CustomerDetailScreen(id: Long, nav: NavHostController) {
    val vm = appViewModel { CustomerDetailViewModel(it, id) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val live = rows.filter { it.booking.status != BookingStatus.CANCELLED }
    val totalAmount = live.sumOf { it.booking.totalAmount }
    val totalPaid = live.sumOf { it.paid }
    val debt = live.sumOf { it.debt }
    val sessions = rows.count { it.booking.status == BookingStatus.DONE }

    ScreenScaffold(
        title = vm.customer?.name ?: "مشتری", onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "ویرایش") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "حذف") }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "اطلاعات و حساب") {
                    vm.customer?.phone?.takeIf { it.isNotEmpty() }?.let { Text("شماره: " + it.toPersianDigits()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("وضعیت حساب:", modifier = Modifier.weight(1f)); DebtBadge(debt)
                    }
                    Text("تعداد رزروها: ${live.size.fa()}    جلسات انجام‌شده: ${sessions.fa()}")
                    Text("مجموع مبلغ: ${totalAmount.formatMoney()} تومان")
                    Text("مجموع پرداختی: ${totalPaid.formatMoney()} تومان", color = SettledGreen)
                    Text("بدهی: ${debt.formatMoney()} تومان", color = if (debt > 0) DebtRed else SettledGreen, fontWeight = FontWeight.Bold)
                }
            }
            item { Text("سوابق رزرو و جلسات", fontWeight = FontWeight.Bold) }
            if (rows.isEmpty()) item { EmptyState("هنوز رزروی برای این مشتری ثبت نشده است.") }
            items(rows, key = { it.booking.id }) { r ->
                BookingCard(r, showDate = true) { nav.navigate(Routes.booking(r.booking.id)) }
            }
        }
    }

    if (editing) {
        CustomerEditDialog(vm.customer, { editing = false }, { n, p -> vm.update(n, p); editing = false })
    }
    if (confirmDelete) {
        ConfirmDialog(
            "حذف مشتری", "این مشتری به سطل بازیافت منتقل می‌شود و بعداً قابل بازگرداندن است. ادامه می‌دهید؟",
            "حذف", onConfirm = {
                confirmDelete = false
                vm.delete { toast(ctx, "مشتری به سطل بازیافت منتقل شد."); nav.popBackStack() }
            }, onDismiss = { confirmDelete = false }, danger = true
        )
    }
    vm.error?.let { MessageDialog(it) { vm.error = null } }
}
