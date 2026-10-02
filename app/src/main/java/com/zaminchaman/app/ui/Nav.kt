@file:OptIn(ExperimentalMaterial3Api::class)

package com.zaminchaman.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zaminchaman.app.AppContainer
import com.zaminchaman.app.feature.*

object Routes {
    const val HOME = "home"
    const val RENTAL = "rental"
    const val FINANCE = "finance"
    const val SETTINGS = "settings"
    const val REPORTS = "reports"
    const val SEARCH = "search"
    const val TRASH = "trash"
    const val BOOKING = "booking?id={id}&contractId={contractId}&day={day}"
    const val CUSTOMER = "customer/{id}"
    const val CONTRACT = "contract/{id}"
    const val CONTRACT_EDIT = "contractEdit?id={id}"
    const val EXPENSE = "expense?id={id}"

    fun booking(id: Long = -1, contractId: Long = -1, day: Long = -1) = "booking?id=$id&contractId=$contractId&day=$day"
    fun customer(id: Long) = "customer/$id"
    fun contract(id: Long) = "contract/$id"
    fun contractEdit(id: Long = -1) = "contractEdit?id=$id"
    fun expense(id: Long = -1) = "expense?id=$id"
}

private class TabItem(val route: String, val label: String, val icon: ImageVector)

private fun longArg(name: String) = navArgument(name) { type = NavType.LongType; defaultValue = -1L }

@Composable
fun AppNav(@Suppress("UNUSED_PARAMETER") container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        TabItem(Routes.HOME, "خانه", Icons.Default.Home),
        TabItem(Routes.RENTAL, "اجاره تایم", Icons.Default.SportsSoccer),
        TabItem(Routes.FINANCE, "مالی", Icons.Default.AccountBalanceWallet),
        TabItem(Routes.SETTINGS, "تنظیمات", Icons.Default.Settings)
    )
    val showBar = tabs.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(t.icon, null) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.RENTAL) { RentalScreen(nav) }
            composable(Routes.FINANCE) { FinanceScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.REPORTS) { ReportScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.SEARCH) { SearchScreen(nav) }
            composable(Routes.TRASH) { TrashScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.BOOKING,
                arguments = listOf(longArg("id"), longArg("contractId"), longArg("day"))
            ) { e ->
                BookingEditScreen(
                    id = e.arguments?.getLong("id") ?: -1L,
                    contractId = e.arguments?.getLong("contractId") ?: -1L,
                    day = e.arguments?.getLong("day") ?: -1L,
                    onBack = { nav.popBackStack() },
                    onOpenContract = { nav.navigate(Routes.contract(it)) }
                )
            }
            composable(Routes.CUSTOMER, arguments = listOf(longArg("id"))) { e ->
                CustomerDetailScreen(e.arguments?.getLong("id") ?: -1L, nav)
            }
            composable(Routes.CONTRACT, arguments = listOf(longArg("id"))) { e ->
                ContractDetailScreen(e.arguments?.getLong("id") ?: -1L, nav)
            }
            composable(Routes.CONTRACT_EDIT, arguments = listOf(longArg("id"))) { e ->
                ContractEditScreen(e.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
            }
            composable(Routes.EXPENSE, arguments = listOf(longArg("id"))) { e ->
                ExpenseEditScreen(e.arguments?.getLong("id") ?: -1L, onBack = { nav.popBackStack() })
            }
        }
    }
}
