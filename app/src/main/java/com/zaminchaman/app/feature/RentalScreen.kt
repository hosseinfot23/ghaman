package com.zaminchaman.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.ui.Routes
import com.zaminchaman.app.ui.components.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class BookingsListViewModel(c: AppContainer) : ViewModel() {
    val day = MutableStateFlow(LocalDate.now())
    val rows = day.flatMapLatest { d ->
        val (f, t) = dayRangeMillis(d, d)
        c.db.bookingDao().observeRange(f, t)
    }.asState(viewModelScope, emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentalScreen(nav: NavHostController) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var showAddCustomer by remember { mutableStateOf(false) }
    val customersVm = appViewModel { CustomersListViewModel(it) }
    val titles = listOf("رزروها", "دوره‌ای", "مشتریان")

    ScreenScaffold(
        title = "اجاره تایم", tab = true,
        actions = { IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Default.Search, "جستجو") } },
        floating = {
            ExtendedFloatingActionButton(
                onClick = {
                    when (tab) {
                        0 -> nav.navigate(Routes.booking())
                        1 -> nav.navigate(Routes.contractEdit())
                        else -> showAddCustomer = true
                    }
                },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(when (tab) { 0 -> "رزرو جدید"; 1 -> "رزرو دوره‌ای جدید"; else -> "مشتری جدید" }) }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> BookingsTab(nav)
                1 -> ContractsTab(nav)
                else -> CustomersTab(nav, customersVm)
            }
        }
    }

    if (showAddCustomer) {
        CustomerEditDialog(
            initial = null,
            onDismiss = { showAddCustomer = false },
            onSave = { n, p -> customersVm.add(n, p); showAddCustomer = false }
        )
    }
    customersVm.error?.let { MessageDialog(it) { customersVm.error = null } }
}

@Composable
private fun BookingsTab(nav: NavHostController) {
    val vm = appViewModel { BookingsListViewModel(it) }
    val day by vm.day.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.day.value = day.minusDays(1) }) { Icon(Icons.Default.KeyboardArrowRight, "روز قبل") }
            TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
                Text(day.formatJalaliLong(), style = MaterialTheme.typography.titleSmall)
            }
            IconButton(onClick = { vm.day.value = day.plusDays(1) }) { Icon(Icons.Default.KeyboardArrowLeft, "روز بعد") }
            TextButton(onClick = { vm.day.value = LocalDate.now() }) { Text("امروز") }
        }
        if (rows.isEmpty()) EmptyState("برای این روز رزروی ثبت نشده است.")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(rows, key = { it.booking.id }) { r ->
                BookingCard(r, showDate = false) { nav.navigate(Routes.booking(r.booking.id)) }
            }
        }
    }
    if (picking) JalaliDateDialog(day, { picking = false }, { vm.day.value = it; picking = false })
}
