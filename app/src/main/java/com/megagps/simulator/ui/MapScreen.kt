package com.megagps.simulator.ui

import androidx.compose.runtime.saveable.rememberSaveable
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megagps.simulator.R
import com.megagps.simulator.data.PlaceResult
import com.megagps.simulator.domain.*
import java.util.Date

@Composable
fun MapScreen(vm: MainViewModel, modifier: Modifier, onOpenSetup: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sim by vm.sim.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val setup by vm.setup.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    /** Runs [action] once permissions are granted and this app is the selected mock-location app. */
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun withMock(action: () -> Unit) {
        if (vm.checkMockAllowed()) action() else onOpenSetup()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refreshSetup()
        val p = pending
        pending = null
        if (hasLocationPermission(ctx)) p?.invoke() else vm.toast(R.string.msg_need_location_permission)
    }
    fun guarded(needsMock: Boolean, action: () -> Unit) {
        val run = { if (needsMock) withMock(action) else action() }
        if (hasLocationPermission(ctx)) run() else { pending = run; permLauncher.launch(requiredPermissions()) }
    }

    Box(modifier.fillMaxSize()) {
        OsmMap(
            Modifier.fillMaxSize(),
            MapDisplay(
                selected = ui.selected,
                simulated = if (sim.isActive && sim.lat != null && sim.lon != null) GeoPoint(sim.lat!!, sim.lon!!) else null,
                real = ui.real,
                route = ui.polyline,
            ),
            ui.focus,
            onTap = { vm.selectPoint(it) },
        )

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchBox(vm, ui.results, ui.busy)
            if (!setup.ready || sim.status == SimStatus.ERROR) {
                Card(
                    Modifier.fillMaxWidth().clickable { onOpenSetup() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.Icon(Icons.Filled.Warning, null)
                        Text(
                            stringResource(errorText(sim.error, setup)),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        ControlPanel(
            vm, ui, sim, settings, ::guarded, onShowReal = { guarded(false) { vm.showRealLocation() } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun errorText(err: SimError, s: SetupStatus): Int = when {
    err == SimError.MOCK_NOT_ALLOWED -> R.string.err_mock
    err == SimError.PERMISSION -> R.string.err_permission
    err == SimError.FOREGROUND_DENIED -> R.string.err_foreground
    !s.permission -> R.string.warn_permission
    !s.mockSelected -> R.string.warn_mock
    else -> R.string.warn_location_off
}

@Composable
private fun SearchBox(vm: MainViewModel, results: List<PlaceResult>, busy: Boolean) {
    var q by rememberSaveable { mutableStateOf("") }
    Card(shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(6.dp)) {
        Column {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true, shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.search_hint)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(q) }),
                trailingIcon = {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { vm.search(q) }) {
                        androidx.compose.material3.Icon(Icons.Filled.Search, stringResource(R.string.search))
                    }
                },
            )
            results.forEach { r ->
                Text(
                    r.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clickable { vm.pickResult(r) }.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlPanel(
    vm: MainViewModel, ui: UiState, sim: SimState, settings: AppSettings,
    guarded: (Boolean, () -> Unit) -> Unit, onShowReal: () -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    var latT by rememberSaveable { mutableStateOf("") }
    var lonT by rememberSaveable { mutableStateOf("") }
    var showSave by remember { mutableStateOf(false) }
    LaunchedEffect(ui.selected) { ui.selected?.let { latT = fmt(it.lat); lonT = fmt(it.lon) } }

    val hasRoute = ui.polyline.size >= 2
    val running = sim.isActive

    Card(
        modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Column(Modifier.padding(12.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(statusRes(sim.status)), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) R.string.hide else R.string.show))
                }
            }
            if (expanded) Column(
                Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // --- coordinates ---
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        latT, { latT = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.latitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        lonT, { lonT = it }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.longitude)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    FilledTonalButton(onClick = { vm.setManual(latT, lonT) }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text(stringResource(R.string.go))
                    }
                }

                // --- actions ---
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { guarded(true) { vm.startPoint() } }, enabled = ui.selected != null) {
                        Text(stringResource(if (running && !sim.isRoute) R.string.set_location else R.string.start_here))
                    }
                    if (hasRoute) Button(onClick = { guarded(true) { vm.startRoute() } }) {
                        Text(stringResource(R.string.start_route))
                    }
                    OutlinedButton(onClick = onShowReal) { Text(stringResource(R.string.my_location)) }
                    OutlinedButton(onClick = { showSave = true }, enabled = ui.selected != null) {
                        Text(stringResource(R.string.save_favorite))
                    }
                }

                // --- live status ---
                if (running) {
                    Text(
                        "${stringResource(R.string.latitude)}: ${fmt(sim.lat ?: 0.0)}   ${stringResource(R.string.longitude)}: ${fmt(sim.lon ?: 0.0)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${stringResource(R.string.speed)}: ${fmt(sim.speedKmh, 1)} ${stringResource(R.string.unit_kmh)}   " +
                            "${stringResource(R.string.heading)}: ${sim.headingDeg.toInt()}°",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (sim.isRoute) {
                        Text(
                            "${stringResource(R.string.total)}: ${distLabel(sim.totalM)}   ${stringResource(R.string.traveled)}: ${distLabel(sim.traveledM)}   " +
                                "${stringResource(R.string.remaining)}: ${distLabel(sim.remainingM)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        sim.etaEpochMs?.let {
                            Text(
                                "${stringResource(R.string.eta)}: ${DateFormat.getTimeFormat(ctx).format(Date(it))}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (sim.isRoute) {
                            if (sim.status == SimStatus.PAUSED) Button(onClick = { vm.resume() }) { Text(stringResource(R.string.resume)) }
                            else if (sim.status == SimStatus.RUNNING) FilledTonalButton(onClick = { vm.pause() }) { Text(stringResource(R.string.pause)) }
                            OutlinedButton(onClick = { vm.restart() }) { Text(stringResource(R.string.restart)) }
                            OutlinedButton(onClick = { vm.reverse() }) { Text(stringResource(R.string.reverse)) }
                        }
                        Button(
                            onClick = { vm.stop() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        ) { Text(stringResource(R.string.stop)) }
                    }
                }

                // --- mode & speed ---
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MoveMode.values().toList()) { m ->
                        FilterChip(
                            selected = settings.mode == m, onClick = { vm.setMode(m) },
                            label = { Text(stringResource(modeRes(m))) },
                        )
                    }
                }
                SpeedSelector(vm, settings)
            }
        }
    }

    if (showSave) NameDialog(R.string.save_favorite, "", { showSave = false }) { vm.savePlace(it) }
}

@Composable
private fun SpeedSelector(vm: MainViewModel, settings: AppSettings) {
    val mode = settings.mode
    val speed = settings.speedOf()
    if (mode.presetsKmh.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(mode.presetsKmh) { v ->
                FilterChip(
                    selected = speed == v, onClick = { vm.setSpeed(v) },
                    label = { Text("${v.toInt()} ${stringResource(R.string.unit_kmh)}") },
                )
            }
        }
    }
    if (mode == MoveMode.WALKING) {
        var slider by remember(speed) { mutableFloatStateOf(speed.toFloat()) }
        Text("${stringResource(R.string.walking_speed)}: ${fmt(slider.toDouble(), 1)} ${stringResource(R.string.unit_kmh)}", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = slider, onValueChange = { slider = it }, valueRange = 2f..6f, steps = 7,
            onValueChangeFinished = { vm.setSpeed(slider.toDouble()) },
        )
    }
    if (mode == MoveMode.CUSTOM) {
        var text by rememberSaveable(speed) { mutableStateOf(fmt(speed, 0)) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, Modifier.weight(1f), singleLine = true,
                label = { Text("${stringResource(R.string.custom_speed)} (${stringResource(R.string.unit_kmh)})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            FilledTonalButton(onClick = { parseNum(text)?.let { vm.setSpeed(it) } }) { Text(stringResource(R.string.apply)) }
        }
    }
}

fun modeRes(m: MoveMode): Int = when (m) {
    MoveMode.WALKING -> R.string.mode_walking
    MoveMode.RUNNING -> R.string.mode_running
    MoveMode.CYCLING -> R.string.mode_cycling
    MoveMode.DRIVING -> R.string.mode_driving
    MoveMode.CUSTOM -> R.string.mode_custom
}

private fun statusRes(s: SimStatus): Int = when (s) {
    SimStatus.IDLE -> R.string.status_idle
    SimStatus.RUNNING -> R.string.status_running
    SimStatus.PAUSED -> R.string.status_paused
    SimStatus.COMPLETED -> R.string.status_completed
    SimStatus.ERROR -> R.string.status_error
}
