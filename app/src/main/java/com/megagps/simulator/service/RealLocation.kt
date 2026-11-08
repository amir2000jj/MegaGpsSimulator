package com.megagps.simulator.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.megagps.simulator.domain.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Reads the device's real position on explicit request. The result stays on the device. */
object RealLocation {
    @Suppress("DEPRECATION")
    private fun isMock(l: Location): Boolean =
        if (Build.VERSION.SDK_INT >= 31) l.isMock else l.isFromMockProvider

    @SuppressLint("MissingPermission")
    suspend fun get(ctx: Context): GeoPoint? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        for (p in providers) {
            val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null }
            if (l != null && !isMock(l)) return GeoPoint(l.latitude, l.longitude)
        }
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!lm.isProviderEnabled(p)) continue
            val l: Location? = suspendCancellableCoroutine { cont ->
                try {
                    LocationManagerCompat.getCurrentLocation(lm, p, null, ContextCompat.getMainExecutor(ctx)) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
            if (l != null && !isMock(l)) return GeoPoint(l.latitude, l.longitude)
        }
        return null
    }
}
