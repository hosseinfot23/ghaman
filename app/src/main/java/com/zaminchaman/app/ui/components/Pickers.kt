@file:OptIn(ExperimentalMaterial3Api::class)

package com.zaminchaman.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zaminchaman.app.core.*
import java.time.LocalDate

@Composable
fun JalaliDateField(label: String, date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ClickableField(label, date.formatJalaliLong(), Icons.Default.Event, { open = true }, modifier)
    if (open) JalaliDateDialog(date, { open = false }, { onChange(it); open = false })
}

@Composable
private fun Stepper(label: String, value: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        IconButton(onClick = onUp) { Icon(Icons.Default.KeyboardArrowUp, "بیشتر") }
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 64.dp))
        IconButton(onClick = onDown) { Icon(Icons.Default.KeyboardArrowDown, "کمتر") }
    }
}

@Composable
fun JalaliDateDialog(initial: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val start = remember { initial.toJalali() }
    var y by remember { mutableIntStateOf(start.year) }
    var m by remember { mutableIntStateOf(start.month) }
    var d by remember { mutableIntStateOf(start.day) }
    fun clamp() { d = d.coerceAtMost(Jalali.monthLength(y, m)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stepper("سال", y.fa(),
                    onUp = { y = (y + 1).coerceAtMost(1500); clamp() },
                    onDown = { y = (y - 1).coerceAtLeast(1300); clamp() })
                Stepper("ماه", Jalali.monthNames[m - 1],
                    onUp = { m = if (m == 12) 1 else m + 1; clamp() },
                    onDown = { m = if (m == 1) 12 else m - 1; clamp() })
                Stepper("روز", d.fa(),
                    onUp = { d = if (d >= Jalali.monthLength(y, m)) 1 else d + 1 },
                    onDown = { d = if (d <= 1) Jalali.monthLength(y, m) else d - 1 })
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(JalaliDate(y, m, d).toLocalDate()) }) { Text("تأیید") } },
        dismissButton = {
            Row {
                TextButton(onClick = { val t = LocalDate.now().toJalali(); y = t.year; m = t.month; d = t.day }) { Text("امروز") }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )
}

@Composable
fun TimeField(label: String, minuteOfDay: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    ClickableField(label, formatMinutes(minuteOfDay), Icons.Default.Schedule, { open = true }, modifier)
    if (open) TimeDialog(minuteOfDay, { open = false }, { onChange(it); open = false })
}

@Composable
fun TimeDialog(initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } }
    )
}
