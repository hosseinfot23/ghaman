package com.zaminchaman.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.zaminchaman.app.domain.describePattern
import com.zaminchaman.app.ui.Routes
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.DebtRed
import com.zaminchaman.app.ui.theme.MutedGray
import com.zaminchaman.app.ui.theme.SettledGreen
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class HomeViewModel(c: AppContainer) : ViewModel() {
    val today: LocalDate = LocalDate.now()
    private val range = dayRangeMillis(today, today)
    val bookings = c.db.bookingDao().observeRange(range.first, range.second).asState(viewModelScope, emptyList())
    val income = c.db.paymentDao().observeIncome(range.first, range.second).asState(viewModelScope, 0L)
    val expense = c.db.expenseDao().observeSum(range.first, range.second).asState(viewModelScope, 0L)
    val debt = c.db.bookingDao().observeTotalDebt().asState(viewModelScope, 0L)
    val activity = c.db.activityDao().recent(8).asState(viewModelScope, emptyList())
    val expected = bookings.map { c.contracts.expectedFor(today) }.asState(viewModelScope, emptyList())
}

@Composable
fun HomeScreen(nav: NavHostController) {
    val vm = appViewModel { HomeViewModel(it) }
    val bookings by vm.bookings.collectAsStateWithLifecycle()
    val income by vm.income.collectAsStateWithLifecycle()
    val expense by vm.expense.collectAsStateWithLifecycle()
    val debt by vm.debt.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val expected by vm.expected.collectAsStateWithLifecycle()
    val live = bookings.filter { it.booking.status != com.zaminchaman.app.data.db.BookingStatus.CANCELLED }

    ScreenScaffold(
        title = "زمین چمن ایرانیان", tab = true,
        actions = { IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Default.Search, "جستجو") } }
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text(vm.today.formatJalaliLong(), style = MaterialTheme.typography.titleMedium, color = MutedGray) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("رزروهای امروز", live.size.fa(), Modifier.weight(1f))
                        StatCard("درآمد امروز", income.formatMoney(), Modifier.weight(1f), SettledGreen)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("هزینه امروز", expense.formatMoney(), Modifier.weight(1f), DebtRed)
                        StatCard("مجموع بدهی‌ها", debt.formatMoney(), Modifier.weight(1f), if (debt > 0) DebtRed else SettledGreen)
                    }
                }
            }
            item {
                Button(
                    onClick = { nav.navigate(Routes.booking()) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("رزرو جدید")
                }
            }
            if (expected.isNotEmpty()) {
                item { Text("قراردادهای دوره‌ای امروز", fontWeight = FontWeight.Bold) }
                items(expected, key = { "k${it.contract.id}" }) { k ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(k.customerName, fontWeight = FontWeight.Bold)
                                Text(
                                    formatMinutes(k.contract.startMinute) + " تا " + formatMinutes(k.contract.endMinute) +
                                        "  |  " + k.contract.describePattern(),
                                    style = MaterialTheme.typography.bodySmall, color = MutedGray
                                )
                            }
                            FilledTonalButton(onClick = {
                                nav.navigate(Routes.booking(contractId = k.contract.id, day = vm.today.toEpochDay()))
                            }) { Text("ثبت جلسه") }
                        }
                    }
                }
            }
            item { Text("رزروهای امروز", fontWeight = FontWeight.Bold) }
            if (bookings.isEmpty()) item { EmptyState("امروز رزروی ثبت نشده است.") }
            items(bookings, key = { "b${it.booking.id}" }) { r ->
                BookingCard(r, showDate = false) { nav.navigate(Routes.booking(r.booking.id)) }
            }
            if (activity.isNotEmpty()) {
                item { Text("آخرین فعالیت‌ها", fontWeight = FontWeight.Bold) }
                items(activity, key = { "a${it.id}" }) { a ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(a.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(a.createdAt.formatDateTime(), style = MaterialTheme.typography.labelSmall, color = MutedGray)
                    }
                }
            }
        }
    }
}
