package com.zaminchaman.app.feature

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.data.repo.*
import com.zaminchaman.app.domain.Period
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.MutedGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class ReportViewModel(private val c: AppContainer) : ViewModel() {
    var filter by mutableStateOf(run {
        val (f, t) = Period.MONTH.range(LocalDate.now())
        ReportFilter(f.toEpochDay(), t.toEpochDay())
    })
    var preset: Period? by mutableStateOf(Period.MONTH)
    var kinds by mutableStateOf(setOf(ReportKind.INCOME, ReportKind.EXPENSE, ReportKind.RENTAL))
    var data: ReportData? by mutableStateOf(null)
    var busy by mutableStateOf(false)
    var error: String? by mutableStateOf(null)
    private var job: Job? = null

    init { refresh() }

    fun refresh() {
        if (filter.fromDay > filter.toDay) {
            error = "تاریخ شروع نباید بعد از تاریخ پایان باشد."
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            busy = true
            try { data = c.reports.build(filter) } catch (e: Exception) { error = e.toUserMessage() }
            busy = false
        }
    }

    fun applyPreset(p: Period) {
        preset = p
        val (f, t) = p.range(LocalDate.now())
        filter = filter.copy(fromDay = f.toEpochDay(), toDay = t.toEpochDay())
        refresh()
    }

    fun setFrom(d: LocalDate) { preset = null; filter = filter.copy(fromDay = d.toEpochDay()); refresh() }
    fun setTo(d: LocalDate) { preset = null; filter = filter.copy(toDay = d.toEpochDay()); refresh() }
    fun setParity(p: Parity) { filter = filter.copy(parity = p); refresh() }
    fun toggleWeekday(i: Int) {
        val s = filter.weekdays.toMutableSet()
        if (!s.add(i)) s.remove(i)
        filter = filter.copy(weekdays = s); refresh()
    }
    fun toggleKind(k: ReportKind) { kinds = if (k in kinds) kinds - k else kinds + k }

    /** Writes the report to cache/exports and returns the file. */
    suspend fun export(pdf: Boolean): File = withContext(Dispatchers.IO) {
        val d = data ?: throw AppError.Validation("ابتدا گزارش را بارگذاری کنید.")
        if (kinds.isEmpty()) throw AppError.Validation("حداقل یک بخش گزارش را انتخاب کنید.")
        val dir = File(c.context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 24L * 3600 * 1000) it.delete() }
        val stamp = LocalDate.now().toJalali().formatLatin().replace('/', '-')
        val file = File(dir, "report_${stamp}_${System.currentTimeMillis() % 100000}." + if (pdf) "pdf" else "xlsx")
        val title = "گزارش زمین چمن ایرانیان"
        if (pdf) PdfExporter.export(file, title, d.filter.describe(), d.summaryLines(), d.tables(kinds))
        else XlsxExporter.export(file, title, d.filter.describe(), d.summaryLines(), d.tables(kinds))
        file
    }
}

@Composable
fun ReportScreen(onBack: () -> Unit) {
    val vm = appViewModel { ReportViewModel(it) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data = vm.data
    var message by remember { mutableStateOf<String?>(null) }

    fun share(pdf: Boolean) {
        scope.launch {
            try {
                val f = vm.export(pdf)
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                val mime = if (pdf) "application/pdf" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(send, "ذخیره یا ارسال گزارش"))
            } catch (e: Exception) {
                message = e.toUserMessage()
            }
        }
    }

    ScreenScaffold(title = "گزارش‌گیری", onBack = onBack) { pad ->
        LazyColumn(
            Modifier.padding(pad), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "بازه‌ی زمانی") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Period.values().forEach { p ->
                            FilterChip(selected = vm.preset == p, onClick = { vm.applyPreset(p) }, label = { Text(p.label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        JalaliDateField("از تاریخ", LocalDate.ofEpochDay(vm.filter.fromDay), { vm.setFrom(it) }, Modifier.weight(1f))
                        JalaliDateField("تا تاریخ", LocalDate.ofEpochDay(vm.filter.toDay), { vm.setTo(it) }, Modifier.weight(1f))
                    }
                }
            }
            item {
                SectionCard(title = "فیلتر روزها (قابل ترکیب)") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Parity.values().forEach { p ->
                            FilterChip(selected = vm.filter.parity == p, onClick = { vm.setParity(p) }, label = { Text(p.label) })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Jalali.weekdayNames.forEachIndexed { i, n ->
                            FilterChip(selected = i in vm.filter.weekdays, onClick = { vm.toggleWeekday(i) }, label = { Text(n) })
                        }
                    }
                    Text("اگر هیچ روز هفته‌ای انتخاب نشود، همه‌ی روزها حساب می‌شوند.", style = MaterialTheme.typography.bodySmall, color = MutedGray)
                }
            }
            item {
                SectionCard(title = "بخش‌های گزارش") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportKind.values().forEach { k ->
                            FilterChip(selected = k in vm.kinds, onClick = { vm.toggleKind(k) }, label = { Text(k.label) })
                        }
                    }
                }
            }
            if (vm.busy && data == null) item { Box(Modifier.fillMaxWidth().padding(24.dp)) { CircularProgressIndicator() } }
            if (data != null) {
                item {
                    SectionCard(title = "خلاصه") {
                        data.summaryLines().forEach { (k, v) ->
                            Row { Text(k, Modifier.weight(1f)); Text(v, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { share(true) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("خروجی PDF")
                        }
                        Button(onClick = { share(false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.TableChart, null); Spacer(Modifier.width(6.dp)); Text("خروجی Excel")
                        }
                    }
                }
                data.tables(vm.kinds).forEach { t ->
                    item { Text(t.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) }
                    item { t.footer?.let { Text(it, color = MutedGray, style = MaterialTheme.typography.bodySmall) } }
                    if (t.rows.isEmpty()) item { EmptyState("موردی برای نمایش نیست.") }
                    items(t.rows.take(200)) { row ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(10.dp).fillMaxWidth()) {
                                Text(row.joinToString("  |  ") { if (it is Long) it.formatMoney() else it.toString() }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (t.rows.size > 200) item { Text("فقط ۲۰۰ ردیف اول نمایش داده شد؛ فایل خروجی همه‌ی ردیف‌ها را دارد.", color = MutedGray, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    vm.error?.let { MessageDialog(it) { vm.error = null } }
    message?.let { MessageDialog(it) { message = null } }
}
