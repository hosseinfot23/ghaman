package com.zaminchaman.app.feature

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.data.db.Expense
import com.zaminchaman.app.data.repo.AttachmentDraft
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.DebtRed
import com.zaminchaman.app.ui.theme.MutedGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class ExpenseEditViewModel(private val c: AppContainer, val id: Long) : ViewModel() {
    var amount by mutableStateOf("")
    var title by mutableStateOf("")
    var note by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var minute by mutableIntStateOf(LocalTime.now().let { it.hour * 60 + it.minute })
    val drafts = mutableStateListOf<AttachmentDraft>()
    var loaded by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var error: String? by mutableStateOf(null)

    private var existing: Expense? = null
    private val removedIds = mutableListOf<Long>()
    private val newFiles = mutableSetOf<String>()      // copied this session, not yet saved
    private val filesToDelete = mutableListOf<String>() // old files, deleted only after a successful save
    private var saved = false

    init {
        viewModelScope.launch {
            try {
                if (id > 0) {
                    val e = c.db.expenseDao().byId(id)
                    if (e == null) error = "این هزینه پیدا نشد." else {
                        existing = e
                        amount = moneyText(e.amount); title = e.title; note = e.note
                        date = e.spentAt.toLocalDate(); minute = e.spentAt.minuteOfDay()
                        c.finance.attachmentsOf(id).forEach {
                            drafts += AttachmentDraft(it.id, it.storedName, it.fileName, it.mimeType, it.note)
                        }
                    }
                }
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                loaded = true
            }
        }
    }

    fun importAttachment(uri: Uri, replaceIndex: Int?) {
        viewModelScope.launch {
            try {
                val imp = withContext(Dispatchers.IO) { c.attachments.import(uri) }
                newFiles += imp.storedName
                if (replaceIndex != null && replaceIndex in drafts.indices) {
                    val old = drafts[replaceIndex]
                    dropFile(old.storedName)
                    drafts[replaceIndex] = old.copy(storedName = imp.storedName, fileName = imp.fileName, mime = imp.mime)
                } else {
                    drafts += AttachmentDraft(0, imp.storedName, imp.fileName, imp.mime, "")
                }
            } catch (e: Exception) {
                error = e.toUserMessage()
            }
        }
    }

    private fun dropFile(stored: String) {
        if (stored in newFiles) { newFiles -= stored; c.attachments.delete(stored) } else filesToDelete += stored
    }

    fun remove(index: Int) {
        val d = drafts.removeAt(index)
        if (d.id != 0L) removedIds += d.id
        dropFile(d.storedName)
    }

    fun setNote(index: Int, n: String) { drafts[index] = drafts[index].copy(note = n) }

    fun uriFor(d: AttachmentDraft): Uri = c.attachments.uriFor(d.storedName)
    fun fileFor(d: AttachmentDraft): File = c.attachments.file(d.storedName)

    fun save(onDone: () -> Unit) {
        if (saving) return
        viewModelScope.launch {
            saving = true
            try {
                val e = Expense(
                    id = existing?.id ?: 0, amount = parseMoney(amount), title = title.trim(), note = note.trim(),
                    spentAt = millisOf(date, minute), createdAt = existing?.createdAt ?: System.currentTimeMillis()
                )
                c.finance.saveExpense(e, drafts.toList(), removedIds.toList())
                saved = true
                filesToDelete.forEach { c.attachments.delete(it) }
                onDone()
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                saving = false
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try { c.finance.softDelete(id); onDone() } catch (e: Exception) { error = e.toUserMessage() }
        }
    }

    /** Files imported but never saved are removed so no orphan invoices stay on the phone. */
    override fun onCleared() {
        if (!saved) newFiles.forEach { c.attachments.delete(it) }
    }
}

private fun decodeSampled(file: File, maxDim: Int): android.graphics.Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    var sample = 1
    while (o.outWidth / sample > maxDim || o.outHeight / sample > maxDim) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
private fun ImageViewerDialog(file: File, onDismiss: () -> Unit) {
    val bmp by produceState<android.graphics.Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { decodeSampled(file, 1600) }.getOrNull() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                val b = bmp
                if (b != null) {
                    Image(b.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
                } else {
                    Text("پیش‌نمایش تصویر ممکن نیست.", Modifier.align(Alignment.Center))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                    Icon(Icons.Default.Close, "بستن")
                }
            }
        }
    }
}

@Composable
fun ExpenseEditScreen(id: Long, onBack: () -> Unit) {
    val vm = appViewModel { ExpenseEditViewModel(it, id) }
    val ctx = LocalContext.current
    var replaceIndex by remember { mutableStateOf<Int?>(null) }
    var viewing by remember { mutableStateOf<File?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removeIndex by remember { mutableStateOf<Int?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importAttachment(uri, replaceIndex)
        replaceIndex = null
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAttachment(uri, replaceIndex)
        replaceIndex = null
    }

    fun open(d: AttachmentDraft) {
        try {
            if (d.mime.startsWith("image/")) {
                val f = vm.fileFor(d)
                if (!f.exists()) throw AppError.AttachmentMissing()
                viewing = f
            } else {
                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(vm.uriFor(d), d.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(intent)
            }
        } catch (e: ActivityNotFoundException) {
            vm.error = "برنامه‌ای برای باز کردن این فایل روی گوشی پیدا نشد."
        } catch (e: Exception) {
            vm.error = e.toUserMessage()
        }
    }

    ScreenScaffold(title = if (id > 0) "ویرایش هزینه" else "هزینه جدید", onBack = onBack) { pad ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@ScreenScaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MoneyField("مبلغ هزینه", vm.amount, { vm.amount = it })
            OutlinedTextField(vm.title, { vm.title = it }, label = { Text("بابت (دلیل هزینه)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            JalaliDateField("تاریخ", vm.date, { vm.date = it })
            TimeField("ساعت", vm.minute, { vm.minute = it })
            OutlinedTextField(vm.note, { vm.note = it }, label = { Text("توضیحات") }, minLines = 2, modifier = Modifier.fillMaxWidth())

            SectionCard(title = "فاکتور و رسید") {
                if (vm.drafts.isEmpty()) Text("هنوز فایلی پیوست نشده است.", color = MutedGray, style = MaterialTheme.typography.bodySmall)
                vm.drafts.forEachIndexed { i, d ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (d.mime.startsWith("image/")) Icons.Default.Image else Icons.Default.Description, null)
                                Spacer(Modifier.width(8.dp))
                                Text(d.fileName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                            }
                            OutlinedTextField(
                                d.note, { vm.setNote(i, it) }, label = { Text("توضیح فاکتور") },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { open(d) }) { Text("مشاهده") }
                                TextButton(onClick = {
                                    replaceIndex = i
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }) { Text("جایگزینی") }
                                TextButton(onClick = { removeIndex = i }) { Text("حذف", color = DebtRed) }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { replaceIndex = null; photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.Image, null); Spacer(Modifier.width(6.dp)); Text("از گالری") }
                    OutlinedButton(
                        onClick = { replaceIndex = null; filePicker.launch(arrayOf("image/*", "application/pdf", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("انتخاب فایل") }
                }
            }

            Button(
                onClick = { vm.save { toast(ctx, "ذخیره شد."); onBack() } },
                enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("ذخیره") }
            if (id > 0) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("حذف هزینه", color = DebtRed)
                }
            }
        }
    }

    viewing?.let { ImageViewerDialog(it) { viewing = null } }
    removeIndex?.let { i ->
        ConfirmDialog(
            "حذف فایل", "این فایل از هزینه جدا می‌شود. پس از ذخیره‌ی هزینه، فایل برای همیشه پاک می‌شود. ادامه می‌دهید؟",
            "حذف", onConfirm = { vm.remove(i); removeIndex = null }, onDismiss = { removeIndex = null }, danger = true
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "حذف هزینه", "این هزینه به سطل بازیافت منتقل می‌شود. ادامه می‌دهید؟", "حذف",
            onConfirm = { confirmDelete = false; vm.delete { toast(ctx, "هزینه به سطل بازیافت منتقل شد."); onBack() } },
            onDismiss = { confirmDelete = false }, danger = true
        )
    }
    vm.error?.let { MessageDialog(it) { vm.error = null } }
}
