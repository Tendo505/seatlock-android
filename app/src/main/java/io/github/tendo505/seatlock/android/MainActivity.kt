package io.github.tendo505.seatlock.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.tendo505.seatlock.android.ui.EventDetailScreen
import io.github.tendo505.seatlock.android.ui.EventDetailViewModel
import io.github.tendo505.seatlock.android.ui.EventsScreen
import io.github.tendo505.seatlock.android.ui.EventsViewModel
import io.github.tendo505.seatlock.android.ui.HoldScreen
import io.github.tendo505.seatlock.android.ui.HoldViewModel
import io.github.tendo505.seatlock.android.ui.SeatLockTheme
import io.github.tendo505.seatlock.android.ui.SettingsScreen
import io.github.tendo505.seatlock.android.ui.SettingsViewModel
import io.github.tendo505.seatlock.android.ui.TicketsScreen
import io.github.tendo505.seatlock.android.ui.TicketsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SeatLockApp).container
        setContent { SeatLockTheme { SeatLockNavHost(container) } }
    }
}

/** Creates a ViewModel wired to the app container; [key] separates instances per screen argument. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(container: AppContainer, key: String? = null, crossinline create: (AppContainer) -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })

private enum class Tab(val route: String, val label: String) { Events("events", "Events"), Tickets("tickets", "My tickets"), Settings("settings", "Settings") }

@Composable
fun SeatLockNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    Scaffold(bottomBar = {
        if (showBar) NavigationBar {
            Tab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = route == tab.route,
                    onClick = {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            when (tab) { Tab.Events -> Icons.Filled.Event; Tab.Tickets -> Icons.Filled.ConfirmationNumber; Tab.Settings -> Icons.Filled.Settings },
                            contentDescription = null,
                        )
                    },
                    label = { Text(tab.label) },
                )
            }
        }
    }) { outer ->
        NavHost(nav, startDestination = Tab.Events.route, modifier = Modifier.padding(bottom = outer.calculateBottomPadding()).consumeWindowInsets(PaddingValues(bottom = outer.calculateBottomPadding()))) {
            composable(Tab.Events.route) {
                EventsScreen(appViewModel(container) { EventsViewModel(it) }, onOpen = { id -> nav.navigate("event/$id") },
                    onOpenSettings = { nav.navigate(Tab.Settings.route) })
            }
            composable("event/{id}") { entry ->
                val id = entry.arguments?.getString("id")!!
                EventDetailScreen(
                    appViewModel(container, key = "event-$id") { EventDetailViewModel(it, id) },
                    onBack = { nav.popBackStack() },
                    onHoldCreated = { holdId -> nav.navigate("hold/$holdId") { popUpTo("event/$id") } },
                )
            }
            composable("hold/{id}") { entry ->
                val id = entry.arguments?.getString("id")!!
                HoldScreen(appViewModel(container, key = "hold-$id") { HoldViewModel(it, id) }, onBack = { nav.popBackStack() })
            }
            composable(Tab.Tickets.route) {
                TicketsScreen(appViewModel(container) { TicketsViewModel(it) }, onOpen = { id -> nav.navigate("hold/$id") })
            }
            composable(Tab.Settings.route) { SettingsScreen(appViewModel(container) { SettingsViewModel(it) }) }
        }
    }
}
