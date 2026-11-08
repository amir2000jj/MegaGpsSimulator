package com.megagps.simulator.ui

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.megagps.simulator.R


@Composable
fun NameDialog(titleRes: Int, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(R.string.name)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name); onDismiss() }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun EmptyState(textRes: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp)) {
        Text(
            stringResource(textRes), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
        )
    }
}

@Composable
fun Section(titleRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** Mock-location setup guide + permission/Xiaomi helpers. Nothing is enabled silently. */
@Composable
fun SetupDialog(vm: MainViewModel, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val setup by vm.setup.collectAsState()
    fun mark(ok: Boolean) = if (ok) "✅ " else "⚠️ "
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.setup_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.setup_steps))
                Text(mark(setup.permission) + stringResource(R.string.status_permission))
                Text(mark(setup.mockSelected) + stringResource(R.string.status_mock))
                Text(mark(setup.locationEnabled) + stringResource(R.string.status_location_on))
                Text(stringResource(R.string.setup_limit), style = MaterialTheme.typography.bodySmall)
                if (!setup.locationEnabled) TextButton(onClick = { SystemIntents.locationSettings(ctx) }) {
                    Text(stringResource(R.string.open_location_settings))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { SystemIntents.developerOptions(ctx) }) { Text(stringResource(R.string.open_dev_options)) }
        },
        dismissButton = {
            TextButton(onClick = { vm.refreshSetup(); onClose() }) { Text(stringResource(R.string.close)) }
        },
    )
}
