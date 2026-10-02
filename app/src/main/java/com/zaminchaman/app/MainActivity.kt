package com.zaminchaman.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.zaminchaman.app.core.AppError
import com.zaminchaman.app.feature.LoginScreen
import com.zaminchaman.app.ui.AppNav
import com.zaminchaman.app.ui.components.LocalContainer
import com.zaminchaman.app.ui.theme.ZaminTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface InitState {
    data object Loading : InitState
    data object Ready : InitState
    data class Failed(val message: String) : InitState
}

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as ZaminApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(5_000)
                    container.session.enforce()
                }
            }
        }
        setContent {
            ZaminTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Root(container) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.session.enforce()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.session.touch()
    }
}

@Composable
private fun Root(container: AppContainer) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<InitState>(InitState.Loading) }

    LaunchedEffect(attempt) {
        state = InitState.Loading
        state = withContext(Dispatchers.IO) {
            try {
                container.openDatabase()
                container.auth.ensureSeeded()
                InitState.Ready
            } catch (e: Throwable) {
                InitState.Failed(AppError.MigrationFailed(e).userMessage)
            }
        }
    }

    when (val s = state) {
        InitState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is InitState.Failed -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(s.message, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { attempt++ }) { Text("تلاش دوباره") }
        }
        InitState.Ready -> {
            LaunchedEffect(Unit) {
                container.auth.autoLockSeconds().collect { container.session.timeoutSeconds = it }
            }
            val unlocked by container.session.unlocked.collectAsStateWithLifecycle()
            if (unlocked) AppNav(container) else LoginScreen()
        }
    }
}
