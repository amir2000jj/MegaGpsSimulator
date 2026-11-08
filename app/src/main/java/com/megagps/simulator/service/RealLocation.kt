package com.megagps.simulator.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.megagps.simulator.domain.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.function.Consumer
import kotlin.coroutines.resume

/** Reads the device's real position on explicit request. The result stays on the device. */
object RealLocation {

    @Suppress("DEPRECATION")
    private fun isMock(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= 31) {
            location.isMock
        } else {
            location.isFromMockProvider
        }

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
                val cancellationSignal = CancellationSignal()

                continuation.invokeOnCancellation {
                    cancellationSignal.cancel()
                }

                try {
                    val executor = ContextCompat.getMainExecutor(ctx)

                    val consumer = Consumer<Location> { result ->
                        if (continuation.isActive) {
                            continuation.resume(result)
                        }
                    }

                    LocationManagerCompat.getCurrentLocation(
                        lm,
                        provider,
                        cancellationSignal,
                        executor,
                        consumer
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
