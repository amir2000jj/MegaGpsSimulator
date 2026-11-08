package com.megagps.simulator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megagps.simulator.R
import com.megagps.simulator.domain.Geo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(vm: MainViewModel, modifier: Modifier, onGoMap: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showSave by remember { mutableStateOf(false) }
    // GPX files come through the system picker, so no storage permission is needed.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importGpx(it) } }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            OsmMap(
                Modifier.fillMaxSize(),
                MapDisplay(route = ui.polyline, waypoints = ui.waypoints, editableWaypoints = true),
                ui.focus,
                onTap = { vm.addWaypoint(it) },
                onWaypointMoved = { i, p -> vm.moveWaypoint(i, p) },
            )
            Card(Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                Text(stringResource(R.string.planner_hint), Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "${stringResource(R.string.total)}: ${distLabel(Geo.pathLength(ui.polyline))}  •  ${ui.waypoints.size} ${stringResource(R.string.points)}",
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_gpx)) }
                OutlinedButton(onClick = { vm.fetchRoute() }, enabled = !ui.busy && ui.waypoints.size >= 2) {
                    Text(stringResource(R.string.auto_route))
                }
                OutlinedButton(onClick = { vm.reverseRoute() }, enabled = ui.waypoints.size >= 2) { Text(stringResource(R.string.reverse)) }
                OutlinedButton(onClick = { vm.clearRoute() }, enabled = ui.waypoints.isNotEmpty()) { Text(stringResource(R.string.clear)) }
                OutlinedButton(onClick = { showSave = true }, enabled = ui.polyline.size >= 2) { Text(stringResource(R.string.save_route)) }
                Button(onClick = onGoMap, enabled = ui.polyline.size >= 2) { Text(stringResource(R.string.use_route)) }
            }
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (ui.routed != null) Text(stringResource(R.string.routed_note), style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(ui.waypoints) { i, p ->
                    val label = when (i) {
                        0 -> stringResource(R.string.start_point)
                        ui.waypoints.lastIndex -> stringResource(R.string.destination)
                        else -> "${stringResource(R.string.waypoint)} $i"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Text("${fmt(p.lat, 5)}, ${fmt(p.lon, 5)}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { vm.shiftWaypoint(i, -1) }, enabled = i > 0) { Text("▲") }
                        TextButton(onClick = { vm.shiftWaypoint(i, 1) }, enabled = i < ui.waypoints.lastIndex) { Text("▼") }
                        TextButton(onClick = { vm.removeWaypoint(i) }) { Text("✕") }
                    }
                }
            }
        }
    }
    if (showSave) NameDialog(R.string.save_route, "", { showSave = false }) { vm.saveRoute(it) }
}
