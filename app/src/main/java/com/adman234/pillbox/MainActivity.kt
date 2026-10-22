package com.adman234.pillbox

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.adman234.pillbox.data.PillShape
import com.adman234.pillbox.data.Settings
import com.adman234.pillbox.ui.HistoryScreen
import com.adman234.pillbox.ui.MedsScreen
import com.adman234.pillbox.ui.PillColors
import com.adman234.pillbox.ui.PillIcon
import com.adman234.pillbox.ui.PillboxTheme
import com.adman234.pillbox.ui.ScheduleScreen
import com.adman234.pillbox.ui.SettingsScreen
import com.adman234.pillbox.ui.editor.EditorScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PillboxTheme { PillboxRoot() } }
    }

    override fun onStart() {
        super.onStart()
        // Cheap safety net: make sure alarms match the database whenever the app is opened.
        app.appScope.launch { app.scheduler.reconcile() }
    }
}

private enum class Tab(val route: String, val label: String) {
    Meds("meds", "Medications"),
    Schedule("schedule", "Schedule"),
    History("history", "History"),
    Settings("settings", "Settings"),
}

@Composable
private fun TabIcon(tab: Tab) {
    when (tab) {
        Tab.Meds -> PillIcon(PillShape.CAPSULE, PillColors[9], 26.dp)
        Tab.Schedule -> Icon(Icons.Default.DateRange, null)
        Tab.History -> Icon(Icons.AutoMirrored.Filled.List, null)
        Tab.Settings -> Icon(Icons.Default.Settings, null)
    }
}

@Composable
private fun PillboxRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.values().any { it.route == route }

    Onboarding()

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    Tab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { TabIcon(tab) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Tab.Meds.route, modifier = Modifier.padding(pad)) {
            composable(Tab.Meds.route) {
                MedsScreen(onAdd = { nav.navigate("editor/0") }, onEdit = { nav.navigate("editor/$it") })
            }
            composable(Tab.Schedule.route) { ScheduleScreen() }
            composable(Tab.History.route) { HistoryScreen() }
            composable(Tab.Settings.route) { SettingsScreen() }
            composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                EditorScreen(it.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() })
            }
        }
    }
}

/** First launch: explain why Pillbox needs to notify you, then ask. */
@Composable
private fun Onboarding() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by ctx.app.settings.flow.collectAsStateWithLifecycle(null)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val s: Settings = settings ?: return
    if (s.onboarded) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Welcome to Pillbox") },
        text = {
            Text(
                "Pillbox reminds you to take your medication. It needs permission to show notifications. " +
                    "Everything stays on this phone, there is no account and no internet access.\n\n" +
                    "Settings has a Reminder health section that shows anything that could stop a reminder from arriving.",
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                scope.launch { ctx.app.settings.update { it.copy(onboarded = true) } }
            }) { Text("Continue") }
        },
    )
}
