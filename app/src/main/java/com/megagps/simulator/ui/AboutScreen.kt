package com.megagps.simulator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.megagps.simulator.R

@Composable
fun AboutScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    val version = remember(ctx) {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text("v$version", style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.about_desc))
        Section(R.string.about_limits_title) { Text(stringResource(R.string.about_limits)) }
        Section(R.string.about_privacy_title) { Text(stringResource(R.string.about_privacy)) }
        Text(stringResource(R.string.about_osm), style = MaterialTheme.typography.bodySmall)
    }
}
