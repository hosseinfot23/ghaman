@file:OptIn(ExperimentalMaterial3Api::class)

package com.zaminchaman.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.data.db.BookingRow
import com.zaminchaman.app.data.db.BookingStatus
import com.zaminchaman.app.data.db.Customer
import com.zaminchaman.app.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

@Composable
inline fun <reified T : ViewModel> appViewModel(crossinline build: (AppContainer) -> T): T {
    val container = LocalContainer.current
    return viewModel(factory = viewModelFactory { initializer { build(container) } })
}

fun <T> Flow<T>.asState(scope: CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(5000), initial)

fun toast(context: android.content.Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    tab: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
                    }
                },
                actions = actions
            )
        },
        floatingActionButton = floating,
        contentWindowInsets = if (tab) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        content = content
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MutedGray)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DebtBadge(debt: Long) {
    if (debt > 0) StatusChip("دارای بدهی", DebtRed) else StatusChip("تسویه شده", SettledGreen)
}

@Composable
fun statusColor(status: String): Color = when (status) {
    BookingStatus.DONE -> SettledGreen
    BookingStatus.CANCELLED -> MutedGray
    else -> PlannedBlue
}

@Composable
fun BookingCard(row: BookingRow, showDate: Boolean, onClick: () -> Unit) {
    val b = row.booking
    Card(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (showDate) b.startAt.toLocalDate().formatJalali() + "  " else "") +
                        formatMinutes(b.startAt.minuteOfDay()) + " تا " + formatMinutes(b.endAt.minuteOfDay()),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusChip(BookingStatus.label(b.status), statusColor(b.status))
            }
            Text(row.customerName + if (b.contractId != null) "  (دوره‌ای)" else "", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "کل: ${b.totalAmount.formatMoney()}   پرداخت: ${row.paid.formatMoney()}",
                    style = MaterialTheme.typography.bodySmall, color = MutedGray, modifier = Modifier.weight(1f)
                )
                if (b.status != BookingStatus.CANCELLED) DebtBadge(row.debt)
            }
            if (row.debt > 0 && b.status != BookingStatus.CANCELLED) {
                Text("مبلغ بدهی: ${row.debt.formatMoney()} تومان", color = DebtRed, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MutedGray, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirmLabel: String,
    onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) DebtRed else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun MessageDialog(text: String, title: String = "توجه", onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("متوجه شدم") } }
    )
}

@Composable
fun MoneyField(label: String, text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = text,
        onValueChange = { onTextChange(formatMoneyInput(it)) },
        label = { Text(label) },
        singleLine = true,
        suffix = { Text("تومان") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ClickableField(label: String, value: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MutedGray)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** State holder shared by the booking and contract forms: pick an existing customer or type a new one. */
class CustomerPickerState {
    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var selectedId: Long? by mutableStateOf(null)
    var all: List<Customer> by mutableStateOf(emptyList())

    val suggestions: List<Customer>
        get() {
            val q = name.normalizeFa()
            if (selectedId != null || q.isBlank()) return emptyList()
            return all.filter { it.name.contains(q) }.take(4)
        }

    fun onNameChange(v: String) { name = v; selectedId = null }
    fun onPhoneChange(v: String) { phone = normalizePhone(v).take(11); selectedId = null }
    fun select(c: Customer) { name = c.name; phone = c.phone; selectedId = c.id }
}

@Composable
fun CustomerPicker(state: CustomerPickerState, enabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val trailing: (@Composable () -> Unit)? =
            if (state.selectedId != null) ({ Icon(Icons.Default.Check, null, tint = SettledGreen) }) else null
        OutlinedTextField(
            value = state.name, onValueChange = state::onNameChange, enabled = enabled,
            label = { Text("نام مشتری") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Person, null) }, trailingIcon = trailing,
            modifier = Modifier.fillMaxWidth()
        )
        state.suggestions.forEach { s ->
            TextButton(onClick = { state.select(s) }, modifier = Modifier.fillMaxWidth()) {
                Text(s.name + if (s.phone.isNotEmpty()) "   " + s.phone.toPersianDigits() else "", modifier = Modifier.fillMaxWidth())
            }
        }
        OutlinedTextField(
            value = state.phone.toPersianDigits(), onValueChange = state::onPhoneChange, enabled = enabled,
            label = { Text("شماره موبایل") }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
