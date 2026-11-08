package com.megagps.simulator.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.megagps.simulator.R

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MegaTheme { AppRoot(vm) } }
    }
}

private data class NavTab(val label: Int, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel) {
    val tabs = listOf(
        NavTab(R.string.tab_map, Icons.Filled.LocationOn),
        NavTab(R.string.tab_planner, Icons.Filled.Create),
        NavTab(R.string.tab_places, Icons.Filled.Favorite),
        NavTab(R.string.tab_routes, Icons.Filled.List),
        NavTab(R.string.tab_settings, Icons.Filled.Settings),
        NavTab(R.string.tab_about, Icons.Filled.Info),
    )
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(ctx.getString(it)) } }
    LifecycleResumeEffect(Unit) {
        vm.refreshSetup()
        onPauseOrDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = stringResource(t.label)) },
                        label = { Text(stringResource(t.label), maxLines = 1) },
                        alwaysShowLabel = false,
                    )
                }
            }
        },
    ) { padding ->
        val mod = Modifier.padding(padding)
        when (tab) {
            0 -> MapScreen(vm, mod) { showSetup = true }
            1 -> PlannerScreen(vm, mod) { tab = 0 }
            2 -> PlacesScreen(vm, mod) { tab = 0 }
            3 -> RoutesScreen(vm, mod) { tab = 0 }
            4 -> SettingsScreen(vm, mod) { showSetup = true }
            else -> AboutScreen(mod)
        }
    }

    if (showSetup) SetupDialog(vm) { showSetup = false }
}
