package com.zaminchaman.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.data.repo.SearchResult
import com.zaminchaman.app.data.repo.SearchTarget
import com.zaminchaman.app.ui.Routes
import com.zaminchaman.app.ui.components.*
import com.zaminchaman.app.ui.theme.MutedGray
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(c: AppContainer) : ViewModel() {
    var query by mutableStateOf("")
    val results = snapshotFlow { query }
        .debounce(250)
        .mapLatest { c.search.search(it) }
        .catch { emit(emptyList()) }
        .asState(viewModelScope, emptyList<SearchResult>())
}

@Composable
fun SearchScreen(nav: NavHostController) {
    val vm = appViewModel { SearchViewModel(it) }
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    ScreenScaffold(title = "جستجو", onBack = { nav.popBackStack() }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = vm.query, onValueChange = { vm.query = it }, singleLine = true,
                label = { Text("مشتری، رزرو، جلسه، تراکنش یا فاکتور") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus)
            )
            if (vm.query.isNotBlank() && results.isEmpty()) EmptyState("نتیجه‌ای پیدا نشد.")
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { r ->
                    Card(
                        onClick = {
                            nav.navigate(
                                when (r.target) {
                                    SearchTarget.CUSTOMER -> Routes.customer(r.id)
                                    SearchTarget.BOOKING -> Routes.booking(r.id)
                                    SearchTarget.EXPENSE -> Routes.expense(r.id)
                                }
                            )
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(12.dp).fillMaxWidth()) {
                            Text(r.group, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(r.title, fontWeight = FontWeight.Bold)
                            if (r.subtitle.isNotBlank()) Text(r.subtitle, style = MaterialTheme.typography.bodySmall, color = MutedGray)
                        }
                    }
                }
            }
        }
    }
}
