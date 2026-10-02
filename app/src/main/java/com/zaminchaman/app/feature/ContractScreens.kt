package com.zaminchaman.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.data.db.*
import com.zaminchaman.app.domain.describePattern
import com.zaminchaman.app.ui.Routes
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.DebtRed
import com.zaminchaman.app.ui.theme.MutedGray
import com.zaminchaman.app.ui.theme.SettledGreen
import kotlinx.coroutines.launch
import java.time.LocalDate

class ContractsListViewModel(c: AppContainer) : ViewModel() {
    val rows = c.contracts.observeAll().asState(viewModelScope, emptyList())
}

@Composable
fun ContractsTab(nav: NavHostController) {
    val vm = appViewModel { ContractsListViewModel(it) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    if (rows.isEmpty()) EmptyState("هنوز رزرو دوره‌ای ثبت نشده است.")
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows, key = { it.contract.id }) { r ->
            val k = r.contract
            Card(
                onClick = { nav.navigate(Routes.contract(k.id)) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.customerName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusChip(if (k.active) "فعال" else "غیرفعال", if (k.active) SettledGreen else MutedGray)
                    }
                    Text(k.describePattern() + "  |  " + formatMinutes(k.startMinute) + " تا " + formatMinutes(k.endMinute))
                    Text(
                        "از ${LocalDate.ofEpochDay(k.startDay).formatJalali()}" +
                            (k.endDay?.let { " تا ${LocalDate.ofEpochDay(it).formatJalali()}" } ?: " (بدون پایان)"),
                        style = MaterialTheme.typography.bodySmall, color = MutedGray
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- edit

class ContractEditViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    val picker = CustomerPickerState()
    var pattern by mutableStateOf(PatternType.WEEKDAYS)
    var mask by mutableIntStateOf(0)
    var startMin by mutableIntStateOf(19 * 60)
    var endMin by mutableIntStateOf(20 * 60)
    var startDate by mutableStateOf(LocalDate.now())
    var hasEnd by mutableStateOf(false)
    var endDate by mutableStateOf(LocalDate.now().plusDays(30))
    var price by mutableStateOf("")
    var active by mutableStateOf(true)
    var note by mutableStateOf("")
    var loaded by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error: String? by mutableStateOf(null)
    private var existing: Contract? = null

    init {
        viewModelScope.launch {
            try {
                picker.all = c.customers.active()
                if (id > 0) {
                    val k = c.db.contractDao().byId(id)
                    if (k == null) { error = "این قرارداد پیدا نشد." } else {
                        existing = k
                        val cust = c.customers.byId(k.customerId)
                        picker.name = cust?.name.orEmpty(); picker.phone = cust?.phone.orEmpty(); picker.selectedId = k.customerId
                        pattern = k.patternType; mask = k.weekdayMask; startMin = k.startMinute; endMin = k.endMinute
                        startDate = LocalDate.ofEpochDay(k.startDay)
                        hasEnd = k.endDay != null
                        k.endDay?.let { endDate = LocalDate.ofEpochDay(it) }
                        price = moneyText(k.defaultPrice); active = k.active; note = k.note
                    }
                }
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                loaded = true
            }
        }
    }

    fun toggleDay(i: Int) { mask = mask xor (1 shl i) }

    fun save(onDone: () -> Unit) {
        if (saving) return
        viewModelScope.launch {
            saving = true
            try {
                val customerId = c.customers.resolve(picker.selectedId, picker.name, picker.phone)
                c.contracts.save(
                    Contract(
                        id = existing?.id ?: 0, customerId = customerId, patternType = pattern,
                        weekdayMask = if (pattern == PatternType.WEEKDAYS) mask else 0,
                        startMinute = startMin, endMinute = endMin,
                        startDay = startDate.toEpochDay(), endDay = if (hasEnd) endDate.toEpochDay() else null,
                        defaultPrice = parseMoney(price), active = active, note = note.trim(),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                )
                onDone()
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                saving = false
            }
        }
    }
}

@Composable
fun ContractEditScreen(id: Long, onBack: () -> Unit) {
    val vm = appViewModel { ContractEditViewModel(it, id) }
    val ctx = LocalContext.current
    ScreenScaffold(title = if (id > 0) "ویرایش رزرو دوره‌ای" else "رزرو دوره‌ای جدید", onBack = onBack) { pad ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "این قرارداد فقط یک «الگو» است؛ هیچ جلسه‌ای خودکار ثبت نمی‌شود. هر روز که مشتری آمد، جلسه را دستی ثبت کنید.",
                style = MaterialTheme.typography.bodySmall, color = MutedGray
            )
            CustomerPicker(vm.picker)
            Text("الگوی تکرار", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PatternType.EVEN to "روزهای زوج", PatternType.ODD to "روزهای فرد", PatternType.WEEKDAYS to "روزهای هفته").forEach { (p, l) ->
                    FilterChip(selected = vm.pattern == p, onClick = { vm.pattern = p }, label = { Text(l) })
                }
            }
            if (vm.pattern == PatternType.WEEKDAYS) {
                Row(Modifier.fillMaxWidth().horizontalScrollCompat(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Jalali.weekdayNames.forEachIndexed { i, n ->
                        FilterChip(selected = ((vm.mask shr i) and 1) == 1, onClick = { vm.toggleDay(i) }, label = { Text(n) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeField("ساعت شروع", vm.startMin, { vm.startMin = it }, Modifier.weight(1f))
                TimeField("ساعت پایان", vm.endMin, { vm.endMin = it }, Modifier.weight(1f))
            }
            JalaliDateField("تاریخ شروع قرارداد", vm.startDate, { vm.startDate = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تاریخ پایان دارد", modifier = Modifier.weight(1f))
                Switch(checked = vm.hasEnd, onCheckedChange = { vm.hasEnd = it })
            }
            if (vm.hasEnd) JalaliDateField("تاریخ پایان قرارداد", vm.endDate, { vm.endDate = it })
            MoneyField("مبلغ هر جلسه", vm.price, { vm.price = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("قرارداد فعال است", modifier = Modifier.weight(1f))
                Switch(checked = vm.active, onCheckedChange = { vm.active = it })
            }
            OutlinedTextField(vm.note, { vm.note = it }, label = { Text("توضیحات") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(
                onClick = { vm.save { toast(ctx, "ذخیره شد."); onBack() } },
                enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("ذخیره") }
        }
    }
    vm.error?.let { MessageDialog(it) { vm.error = null } }
}

@Composable
private fun Modifier.horizontalScrollCompat(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))

// --------------------------------------------------------------- detail

class ContractDetailViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    var contract: ContractRow? by mutableStateOf(null)
    var error: String? by mutableStateOf(null)
    val sessions = c.db.bookingDao().observeForContract(id).asState(viewModelScope, emptyList())

    init { reload() }

    fun reload() { viewModelScope.launch { contract = c.db.contractDao().rowById(id) } }

    fun toggleActive() {
        val k = contract?.contract ?: return
        viewModelScope.launch {
            try { c.contracts.save(k.copy(active = !k.active)); reload() } catch (e: Exception) { error = e.toUserMessage() }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try { c.contracts.softDelete(id); onDone() } catch (e: Exception) { error = e.toUserMessage() }
        }
    }
}

@Composable
fun ContractDetailScreen(id: Long, nav: NavHostController) {
    val vm = appViewModel { ContractDetailViewModel(it, id) }
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDay by remember { mutableStateOf(false) }

    val live = sessions.filter { it.booking.status != BookingStatus.CANCELLED }
    val done = sessions.filter { it.booking.status == BookingStatus.DONE }
    val total = live.sumOf { it.booking.totalAmount }
    val paid = live.sumOf { it.paid }
    val debt = live.sumOf { it.debt }
    val last = done.maxByOrNull { it.booking.startAt }
    val k = vm.contract?.contract

    ScreenScaffold(
        title = vm.contract?.customerName ?: "قرارداد دوره‌ای", onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { nav.navigate(Routes.contractEdit(id)) }) { Icon(Icons.Default.Edit, "ویرایش") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "حذف") }
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (k != null) item {
                SectionCard(title = "مشخصات قرارداد") {
                    Text("الگو: " + k.describePattern())
                    Text("ساعت: " + formatMinutes(k.startMinute) + " تا " + formatMinutes(k.endMinute))
                    Text("تاریخ شروع: " + LocalDate.ofEpochDay(k.startDay).formatJalali())
                    Text("تاریخ پایان: " + (k.endDay?.let { LocalDate.ofEpochDay(it).formatJalali() } ?: "ندارد"))
                    Text("مبلغ هر جلسه: " + k.defaultPrice.formatMoney() + " تومان")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (k.active) "قرارداد فعال است" else "قرارداد غیرفعال است", modifier = Modifier.weight(1f))
                        Switch(checked = k.active, onCheckedChange = { vm.toggleActive() })
                    }
                    if (k.note.isNotBlank()) Text(k.note, color = MutedGray)
                }
            }
            item {
                SectionCard(title = "آمار جلسات") {
                    Text("جلسات انجام‌شده: ${done.size.fa()}")
                    Text("جلسات ثبت‌شده: ${live.size.fa()}")
                    Text("آخرین جلسه: " + (last?.booking?.startAt?.toLocalDate()?.formatJalali() ?: "—"))
                    Text("مجموع مبلغ: ${total.formatMoney()} تومان")
                    Text("مجموع پرداختی: ${paid.formatMoney()} تومان", color = SettledGreen)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("بدهی: ${debt.formatMoney()} تومان", color = if (debt > 0) DebtRed else SettledGreen,
                            fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        DebtBadge(debt)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { nav.navigate(Routes.booking(contractId = id, day = LocalDate.now().toEpochDay())) },
                        modifier = Modifier.weight(1f)
                    ) { Text("ثبت جلسه امروز") }
                    OutlinedButton(onClick = { pickDay = true }, modifier = Modifier.weight(1f)) { Text("ثبت جلسه در تاریخ دیگر") }
                }
            }
            item { Text("تاریخچه‌ی جلسات", fontWeight = FontWeight.Bold) }
            if (sessions.isEmpty()) item { EmptyState("هنوز جلسه‌ای برای این قرارداد ثبت نشده است.") }
            items(sessions, key = { it.booking.id }) { r ->
                BookingCard(r, showDate = true) { nav.navigate(Routes.booking(r.booking.id)) }
            }
        }
    }

    if (pickDay) {
        JalaliDateDialog(LocalDate.now(), { pickDay = false }) { d ->
            pickDay = false
            nav.navigate(Routes.booking(contractId = id, day = d.toEpochDay()))
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            "حذف قرارداد", "قرارداد به سطل بازیافت می‌رود. جلسات ثبت‌شده‌ی قبلی و حساب‌های مالی آن‌ها حفظ می‌شود.",
            "حذف", onConfirm = {
                confirmDelete = false
                vm.delete { toast(ctx, "قرارداد به سطل بازیافت منتقل شد."); nav.popBackStack() }
            }, onDismiss = { confirmDelete = false }, danger = true
        )
    }
    vm.error?.let { MessageDialog(it) { vm.error = null } }
}
