package com.megagps.simulator.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.megagps.simulator.domain.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.function.Consumer
import kotlin.coroutines.resume

/** Reads the device's real position on explicit request. The result stays on the device. */
object RealLocation {
    @Suppress("DEPRECATION")
    private fun isMock(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= 31) location.isMock
        else location.isFromMockProvider

    @SuppressLint("MissingPermission")
    suspend fun get(ctx: Context): GeoPoint? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providers) {
            val location = try {
                lm.getLastKnownLocation(provider)
            } catch (_: Exception) {
                null
            }

            if (location != null && !isMock(location)) {
                return GeoPoint(location.latitude, location.longitude)
            }
        }

        for (provider in listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )) {
            if (!lm.isProviderEnabled(provider)) continue

            val location: Location? = suspendCancellableCoroutine { continuation ->
                try {
                    LocationManagerCompat.getCurrentLocation(
                        lm,
                        provider,
                        CancellationSignal(),
                        ContextCompat.getMainExecutor(ctx),
                        Consumer<Location> { result ->
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        }
                    )
                } catch (_: Exception) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }

            if (location != null && !isMock(location)) {
                return GeoPoint(location.latitude, location.longitude)
            }
        }

        return null
    }
}
