package com.zaminchaman.app.feature

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.ui.components.appViewModel
import com.zaminchaman.app.ui.theme.DebtRed
import com.zaminchaman.app.ui.theme.PitchGreen
import kotlinx.coroutines.launch

class LoginViewModel(private val c: AppContainer) : ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var error: String? by mutableStateOf(null)
    var busy by mutableStateOf(false)
    private var fails = 0
    private var blockedUntil = 0L

    fun login() {
        if (busy) return
        val now = SystemClock.elapsedRealtime()
        if (now < blockedUntil) {
            error = "تلاش ناموفق زیاد بود. چند ثانیه صبر کنید و دوباره امتحان کنید."
            return
        }
        viewModelScope.launch {
            busy = true
            val ok = try { c.auth.verify(username, password) } catch (e: Exception) { false }
            busy = false
            if (ok) {
                fails = 0; error = null; password = ""
                c.session.unlock()
            } else {
                fails++
                error = "نام کاربری یا رمز عبور اشتباه است."
                if (fails >= 5) { blockedUntil = SystemClock.elapsedRealtime() + 30_000; fails = 0 }
            }
        }
    }
}

@Composable
fun LoginScreen() {
    val vm = appViewModel { LoginViewModel(it) }
    Column(
        Modifier.fillMaxSize().padding(28.dp).imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.SportsSoccer, null, tint = PitchGreen, modifier = Modifier.size(84.dp))
        Spacer(Modifier.height(8.dp))
        Text("زمین چمن ایرانیان", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = vm.username, onValueChange = { vm.username = it }, singleLine = true,
            label = { Text("نام کاربری") }, leadingIcon = { Icon(Icons.Default.Person, null) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.password, onValueChange = { vm.password = it }, singleLine = true,
            label = { Text("رمز عبور") }, leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.login() }),
            modifier = Modifier.fillMaxWidth()
        )
        vm.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DebtRed, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.login() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("ورود")
        }
    }
}
