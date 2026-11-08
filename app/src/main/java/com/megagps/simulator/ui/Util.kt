package com.megagps.simulator.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.megagps.simulator.R
import java.util.Locale
import kotlin.math.roundToInt

fun fmt(d: Double, digits: Int = 6): String = String.format(Locale.US, "%.${digits}f", d)

/** Parses a decimal typed with Latin, Persian or Arabic-Indic digits and ',' / '٫' separators. */
fun parseNum(s: String): Double? {
    val sb = StringBuilder()
    for (c in s.trim()) {
        sb.append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                '٫', '،', ',' -> '.'
                else -> c
            }
        )
    }
    return sb.toString().toDoubleOrNull()
}

@Composable
fun distLabel(m: Double): String =
    if (m >= 1000) fmt(m / 1000.0, 2) + " " + stringResource(R.string.unit_km)
    else m.roundToInt().toString() + " " + stringResource(R.string.unit_m)

fun hasLocationPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun requiredPermissions(): Array<String> {
    val l = arrayListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
    return l.toTypedArray()
}

/** Opens system screens. Every call is guarded: OEM builds may lack some of these activities. */
object SystemIntents {
    private fun tryStart(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    fun developerOptions(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun appDetails(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
    )

    fun batteryOptimization(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
    )

    fun notificationSettings(ctx: Context) = tryStart(
        ctx,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
    )

    /** Xiaomi MIUI / HyperOS autostart manager; falls back to the app details page. */
    fun xiaomiAutostart(ctx: Context) = tryStart(
        ctx,
        Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
    )

    fun locationSettings(ctx: Context) = tryStart(ctx, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    fun appLanguage(ctx: Context) = tryStart(
        ctx,
        if (Build.VERSION.SDK_INT >= 33)
            Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        else Intent(Settings.ACTION_LOCALE_SETTINGS),
    )
}
