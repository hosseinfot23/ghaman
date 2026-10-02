package com.zaminchaman.app.feature

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.core.*
import com.zaminchaman.app.ui.Routes
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.DebtRed
import com.zaminchaman.app.ui.theme.MutedGray
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val lockSeconds = c.auth.autoLockSeconds().asState(viewModelScope, 300)
    var username by mutableStateOf("")
    var message: String? by mutableStateOf(null)
    var progress: String? by mutableStateOf(null)
    var restoreDone by mutableStateOf(false)

    init { loadUser() }
    private fun loadUser() { viewModelScope.launch { username = c.auth.username() } }

    fun setLock(sec: Int) { viewModelScope.launch { c.auth.setAutoLock(sec) } }

    fun changeCredentials(current: String, newUser: String, newPass: String, onOk: () -> Unit) {
        viewModelScope.launch {
            try {
                c.auth.changeCredentials(current, newUser.takeIf { it.isNotBlank() }, newPass.takeIf { it.isNotEmpty() })
                loadUser(); onOk(); message = "اطلاعات ورود با موفقیت تغییر کرد."
            } catch (e: Exception) { message = e.toUserMessage() }
        }
    }

    fun backup(uri: Uri) {
        viewModelScope.launch {
            progress = "در حال ساخت فایل پشتیبان…"
            try { c.backup.createBackup(uri); message = "فایل پشتیبان با موفقیت ساخته شد. آن را در جای امن (مثلاً حافظه‌ی خارجی یا فضای ابری) نگه دارید." }
            catch (e: Exception) { message = e.toUserMessage() }
            progress = null
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            progress = "در حال بازیابی اطلاعات… لطفاً برنامه را نبندید."
            try { c.backup.restore(uri); restoreDone = true } catch (e: Exception) { message = e.toUserMessage() }
            progress = null
        }
    }

    fun lockNow() = c.session.lock()
    fun restart() = c.restartApp()
}

private val lockOptions = listOf(0 to "هرگز", 60 to "۱ دقیقه", 120 to "۲ دقیقه", 300 to "۵ دقیقه", 600 to "۱۰ دقیقه", 1800 to "۳۰ دقیقه")

@Composable
fun SettingsScreen(nav: NavHostController) {
    val vm = appViewModel { SettingsViewModel(it) }
    val lock by vm.lockSeconds.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showCreds by remember { mutableStateOf(false) }
    var showLock by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestore = uri
    }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }

    ScreenScaffold(title = "تنظیمات", tab = true) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(title = "حساب کاربری") {
                Text("نام کاربری فعلی: ${vm.username}")
                OutlinedButton(onClick = { showCreds = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Key, null); Spacer(Modifier.width(6.dp)); Text("تغییر نام کاربری / رمز عبور")
                }
            }
            SectionCard(title = "قفل خودکار") {
                Text("قفل پس از: " + (lockOptions.firstOrNull { it.first == lock }?.second ?: "${lock.fa()} ثانیه"))
                OutlinedButton(onClick = { showLock = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Lock, null); Spacer(Modifier.width(6.dp)); Text("تغییر زمان قفل")
                }
                OutlinedButton(onClick = { vm.lockNow() }, modifier = Modifier.fillMaxWidth()) { Text("قفل کردن / خروج") }
            }
            SectionCard(title = "پشتیبان‌گیری و بازیابی") {
                Text(
                    "فایل پشتیبان شامل همه‌ی اطلاعات، مشتریان، تنظیمات ورود و فاکتورها است.",
                    style = MaterialTheme.typography.bodySmall, color = MutedGray
                )
                Button(
                    onClick = { backupLauncher.launch("zamin-chaman-backup-" + LocalDate.now().toJalali().formatLatin().replace('/', '-') + ".zip") },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Backup, null); Spacer(Modifier.width(6.dp)); Text("ساخت فایل پشتیبان") }
                OutlinedButton(
                    onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Restore, null); Spacer(Modifier.width(6.dp)); Text("بازیابی از فایل پشتیبان") }
            }
            SectionCard(title = "داده‌ها") {
                OutlinedButton(onClick = { nav.navigate(Routes.TRASH) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("سطل بازیافت")
                }
                OutlinedButton(onClick = { nav.navigate(Routes.REPORTS) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Assessment, null); Spacer(Modifier.width(6.dp)); Text("گزارش‌گیری")
                }
            }
            SectionCard(title = "درباره") {
                Text("زمین چمن ایرانیان — نسخه $version")
                Text("همه‌ی اطلاعات فقط روی همین گوشی ذخیره می‌شود و برنامه به اینترنت نیاز ندارد.", style = MaterialTheme.typography.bodySmall, color = MutedGray)
            }
        }
    }

    if (showCreds) {
        CredentialsDialog(vm.username, { showCreds = false }) { cur, user, pass -> vm.changeCredentials(cur, user, pass) { showCreds = false } }
    }
    if (showLock) {
        AlertDialog(
            onDismissRequest = { showLock = false },
            title = { Text("زمان قفل خودکار") },
            text = {
                Column {
                    lockOptions.forEach { (sec, label) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = lock == sec, onClick = { vm.setLock(sec); showLock = false })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLock = false }) { Text("بستن") } }
        )
    }
    pendingRestore?.let { uri ->
        ConfirmDialog(
            "بازیابی اطلاعات",
            "اطلاعات فعلی این گوشی با اطلاعات داخل فایل پشتیبان جایگزین می‌شود. قبل از آن یک نسخه‌ی امن از اطلاعات فعلی ذخیره می‌شود. ادامه می‌دهید؟",
            "بازیابی", onConfirm = { pendingRestore = null; vm.restore(uri) }, onDismiss = { pendingRestore = null }, danger = true
        )
    }
    vm.progress?.let {
        AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text("لطفاً صبر کنید") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)); Text(it) } }
        )
    }
    if (vm.restoreDone) {
        AlertDialog(
            onDismissRequest = {}, title = { Text("بازیابی انجام شد") },
            text = { Text("اطلاعات با موفقیت بازیابی شد. برنامه برای اعمال تغییرات دوباره باز می‌شود.") },
            confirmButton = { TextButton(onClick = { vm.restart() }) { Text("باز کردن دوباره") } }
        )
    }
    vm.message?.let { MessageDialog(it, title = "پیام") { vm.message = null } }
}

@Composable
private fun CredentialsDialog(currentUser: String, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var cur by remember { mutableStateOf("") }
    var user by remember { mutableStateOf(currentUser) }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغییر اطلاعات ورود") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(cur, { cur = it }, label = { Text("رمز عبور فعلی") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(user, { user = it }, label = { Text("نام کاربری جدید") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("رمز عبور جدید (اختیاری)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass2, { pass2 = it }, label = { Text("تکرار رمز عبور جدید") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                err?.let { Text(it, color = DebtRed, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    cur.isEmpty() -> err = "رمز عبور فعلی را وارد کنید."
                    pass != pass2 -> err = "رمز جدید و تکرار آن یکسان نیست."
                    else -> onSave(cur, user, pass)
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
