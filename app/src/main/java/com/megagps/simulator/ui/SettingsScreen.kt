
package com.megagps.simulator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megagps.simulator.R
import com.megagps.simulator.domain.ThemeMode
import com.megagps.simulator.domain.TrafficSetting

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier, onOpenSetup: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Section(R.string.sec_appearance) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ThemeMode.values().forEach { m ->
                    FilterChip(
                        selected = s.themeMode == m, onClick = { vm.setTheme(m) },
                        label = { Text(stringResource(themeRes(m))) },
                    )
                }
            }
        }

        Section(R.string.sec_simulation) {
            Text(stringResource(R.string.update_interval), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(500L, 1000L, 2000L, 5000L).forEach { ms ->
                    FilterChip(selected = s.intervalMs == ms, onClick = { vm.setInterval(ms) }, label = { Text("${ms / 1000.0} s") })
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(R.string.human_walking, R.string.human_walking_desc, s.humanWalking) { vm.setHuman(it) }
            SwitchRow(R.string.random_pauses, R.string.random_pauses_desc, s.randomPauses) { vm.setPauses(it) }
            SwitchRow(R.string.custom_variation, R.string.custom_variation_desc, s.customVariation) { vm.setCustomVariation(it) }
        }

        Section(R.string.sec_traffic) {
            SwitchRow(R.string.traffic_enable, R.string.traffic_desc, s.trafficEnabled) { vm.setTrafficEnabled(it) }
            if (s.trafficEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrafficSetting.values().forEach { t ->
                        FilterChip(
                            selected = s.trafficLevel == t, onClick = { vm.setTrafficLevel(t) },
                            label = { Text(stringResource(trafficRes(t))) },
                        )
                    }
                }
            }
        }

        Section(R.string.sec_setup) {
            Button(onClick = onOpenSetup) { Text(stringResource(R.string.setup_guide)) }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { SystemIntents.developerOptions(ctx) }) { Text(stringResource(R.string.open_dev_options)) }
                OutlinedButton(onClick = { SystemIntents.appDetails(ctx) }) { Text(stringResource(R.string.app_permissions)) }
                OutlinedButton(onClick = { SystemIntents.appLanguage(ctx) }) { Text(stringResource(R.string.language)) }
            }
        }

        Section(R.string.sec_xiaomi) {
            Text(stringResource(R.string.xiaomi_desc), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { SystemIntents.xiaomiAutostart(ctx) }) { Text(stringResource(R.string.autostart)) }
                OutlinedButton(onClick = { SystemIntents.batteryOptimization(ctx) }) { Text(stringResource(R.string.battery_opt)) }
                OutlinedButton(onClick = { SystemIntents.notificationSettings(ctx) }) { Text(stringResource(R.string.notif_settings)) }
                OutlinedButton(onClick = { SystemIntents.appDetails(ctx) }) { Text(stringResource(R.string.background_activity)) }
            }
        }
    }
}

private fun themeRes(m: ThemeMode): Int = when (m) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun trafficRes(t: TrafficSetting): Int = when (t) {
    TrafficSetting.LOW -> R.string.traffic_low
    TrafficSetting.MEDIUM -> R.string.traffic_medium
    TrafficSetting.HIGH -> R.string.traffic_high
    TrafficSetting.AUTO -> R.string.traffic_auto
}

@Composable
private fun SwitchRow(title: Int, desc: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
