package com.megagps.simulator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megagps.simulator.R
import com.megagps.simulator.domain.Geo

@Composable
fun PlacesScreen(vm: MainViewModel, modifier: Modifier, onGoMap: () -> Unit) {
    val places by vm.places.collectAsStateWithLifecycle()
    if (places.isEmpty()) { EmptyState(R.string.no_places, modifier); return }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(places, key = { it.id }) { pl ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pl.name, style = MaterialTheme.typography.titleMedium)
                        Text("${fmt(pl.point.lat, 5)}, ${fmt(pl.point.lon, 5)}", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = { vm.usePlace(pl); onGoMap() }) { Text(stringResource(R.string.use)) }
                    TextButton(onClick = { vm.deletePlace(pl.id) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}

@Composable
fun RoutesScreen(vm: MainViewModel, modifier: Modifier, onGoMap: () -> Unit) {
    val routes by vm.savedRoutes.collectAsStateWithLifecycle()
    if (routes.isEmpty()) { EmptyState(R.string.no_routes, modifier); return }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(routes, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${distLabel(Geo.pathLength(r.points))}  •  ${r.points.size} ${stringResource(R.string.points)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    FilledTonalButton(onClick = { vm.loadRoute(r); onGoMap() }) { Text(stringResource(R.string.load)) }
                    TextButton(onClick = { vm.deleteRoute(r.id) }) { Text(stringResource(R.string.delete)) }
                }
            }
        }
    }
}
