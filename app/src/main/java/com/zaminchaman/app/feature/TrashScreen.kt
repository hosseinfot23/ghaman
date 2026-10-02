package com.zaminchaman.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
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
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.DebtRed
import com.zaminchaman.app.ui.theme.MutedGray
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

enum class TrashKind(val label: String) {
    CUSTOMER("مشتری"), BOOKING("رزرو / جلسه"), CONTRACT("رزرو دوره‌ای"), EXPENSE("هزینه")
}

data class TrashItem(val kind: TrashKind, val id: Long, val title: String, val subtitle: String)

class TrashViewModel(private val c: AppContainer) : ViewModel() {
    var error: String? by mutableStateOf(null)

    val items = combine(
        c.customers.observeTrash(), c.bookings.observeTrash(), c.contracts.observeTrash(), c.finance.observeTrash()
    ) { customers, bookings, contracts, expenses ->
        customers.map { TrashItem(TrashKind.CUSTOMER, it.id, it.name, it.phone.toPersianDigits()) } +
            bookings.map {
                TrashItem(
                    TrashKind.BOOKING, it.booking.id, it.customerName,
                    it.booking.startAt.formatDateTime() + "  |  " + it.booking.totalAmount.formatMoney() + " تومان"
                )
            } +
            contracts.map { TrashItem(TrashKind.CONTRACT, it.contract.id, it.customerName, "قرارداد دوره‌ای") } +
            expenses.map { TrashItem(TrashKind.EXPENSE, it.id, it.title, it.spentAt.formatDateTime() + "  |  " + it.amount.formatMoney() + " تومان") }
    }.asState(viewModelScope, emptyList())

    fun restore(i: TrashItem) = launchSafe {
        when (i.kind) {
            TrashKind.CUSTOMER -> c.customers.restore(i.id)
            TrashKind.BOOKING -> c.bookings.restore(i.id)
            TrashKind.CONTRACT -> c.contracts.restore(i.id)
            TrashKind.EXPENSE -> c.finance.restore(i.id)
        }
    }

    fun deleteForever(i: TrashItem) = launchSafe {
        when (i.kind) {
            TrashKind.CUSTOMER -> c.customers.deletePermanently(i.id)
            TrashKind.BOOKING -> c.bookings.deletePermanently(i.id)
            TrashKind.CONTRACT -> c.contracts.deletePermanently(i.id)
            TrashKind.EXPENSE -> c.finance.deletePermanently(i.id)
        }
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { error = e.toUserMessage() }
        }
    }
}

@Composable
fun TrashScreen(onBack: () -> Unit) {
    val vm = appViewModel { TrashViewModel(it) }
    val items by vm.items.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var toDelete by remember { mutableStateOf<TrashItem?>(null) }

    ScreenScaffold(title = "سطل بازیافت", onBack = onBack) { pad ->
        if (items.isEmpty()) EmptyState("سطل بازیافت خالی است.", Modifier.padding(pad))
        LazyColumn(
            Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.kind.name + it.id }) { i ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(i.kind.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(i.title, fontWeight = FontWeight.Bold)
                            if (i.subtitle.isNotBlank()) Text(i.subtitle, style = MaterialTheme.typography.bodySmall, color = MutedGray)
                        }
                        IconButton(onClick = { vm.restore(i); toast(ctx, "بازگردانی شد.") }) { Icon(Icons.Default.Restore, "بازگردانی") }
                        IconButton(onClick = { toDelete = i }) { Icon(Icons.Default.DeleteForever, "حذف دائمی", tint = DebtRed) }
                    }
                }
            }
        }
    }
    toDelete?.let { i ->
        ConfirmDialog(
            "حذف دائمی", "«${i.title}» برای همیشه پاک می‌شود و قابل بازگشت نیست. مطمئن هستید؟", "حذف دائمی",
            onConfirm = { vm.deleteForever(i); toDelete = null }, onDismiss = { toDelete = null }, danger = true
        )
    }
    vm.error?.let { MessageDialog(it) { vm.error = null } }
}
