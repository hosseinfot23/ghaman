package com.zaminchaman.app.feature

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.domain.Period
import com.zaminchaman.app.ui.Routes
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.DebtRed
import com.zaminchaman.app.ui.theme.MutedGray
import com.zaminchaman.app.ui.theme.SettledGreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(c: AppContainer) : ViewModel() {
    val period = MutableStateFlow(Period.MONTH)
    /** null = all, "INCOME", "EXPENSE" */
    val type = MutableStateFlow<String?>(null)

    private val range = period.map { p ->
        val (f, t) = p.range(LocalDate.now())
        dayRangeMillis(f, t)
    }

    val income = range.flatMapLatest { c.db.paymentDao().observeIncome(it.first, it.second) }.asState(viewModelScope, 0L)
    val expense = range.flatMapLatest { c.db.expenseDao().observeSum(it.first, it.second) }.asState(viewModelScope, 0L)
    val debt = c.db.bookingDao().observeTotalDebt().asState(viewModelScope, 0L)
    val transactions = combine(range, type) { r, t -> r to t }
        .flatMapLatest { (r, t) ->
            c.db.financeDao().transactions(r.first, r.second).map { list -> if (t == null) list else list.filter { it.type == t } }
        }.asState(viewModelScope, emptyList())
}

@Composable
fun FinanceScreen(nav: NavHostController) {
    val vm = appViewModel { FinanceViewModel(it) }
    val period by vm.period.collectAsStateWithLifecycle()
    val type by vm.type.collectAsStateWithLifecycle()
    val income by vm.income.collectAsStateWithLifecycle()
    val expense by vm.expense.collectAsStateWithLifecycle()
    val debt by vm.debt.collectAsStateWithLifecycle()
    val list by vm.transactions.collectAsStateWithLifecycle()

    ScreenScaffold(
        title = "مالی و حسابداری", tab = true,
        actions = {
            IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Default.Search, "جستجو") }
            IconButton(onClick = { nav.navigate(Routes.REPORTS) }) { Icon(Icons.Default.Assessment, "گزارش‌گیری") }
        },
        floating = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.expense()) },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("هزینه جدید") }
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Period.TODAY, Period.WEEK, Period.MONTH, Period.QUARTER, Period.YEAR).forEach { p ->
                        FilterChip(selected = period == p, onClick = { vm.period.value = p }, label = { Text(p.label) })
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("درآمد", income.formatMoney(), Modifier.weight(1f), SettledGreen)
                        StatCard("هزینه", expense.formatMoney(), Modifier.weight(1f), DebtRed)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("سود خالص", (income - expense).formatMoney(), Modifier.weight(1f),
                            if (income - expense >= 0) SettledGreen else DebtRed)
                        StatCard("مجموع بدهی‌ها", debt.formatMoney(), Modifier.weight(1f), if (debt > 0) DebtRed else SettledGreen)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == null, onClick = { vm.type.value = null }, label = { Text("همه") })
                    FilterChip(selected = type == "INCOME", onClick = { vm.type.value = "INCOME" }, label = { Text("درآمد") })
                    FilterChip(selected = type == "EXPENSE", onClick = { vm.type.value = "EXPENSE" }, label = { Text("هزینه") })
                }
            }
            if (list.isEmpty()) item { EmptyState("تراکنشی در این بازه ثبت نشده است.") }
            items(list, key = { it.type + it.refId }) { t ->
                val isIncome = t.type == "INCOME"
                Card(
                    onClick = { if (isIncome) nav.navigate(Routes.booking(t.bookingId)) else nav.navigate(Routes.expense(t.refId)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null,
                            tint = if (isIncome) SettledGreen else DebtRed
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text((if (isIncome) "دریافت از " else "هزینه: ") + t.title, fontWeight = FontWeight.Bold)
                            Text(
                                t.occurredAt.formatDateTime() + if (t.note.isNotBlank()) "  |  " + t.note else "",
                                style = MaterialTheme.typography.bodySmall, color = MutedGray
                            )
                        }
                        Text(
                            (if (isIncome) "+" else "−") + t.amount.formatMoney(),
                            color = if (isIncome) SettledGreen else DebtRed, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
