package com.megagps.simulator.service

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import com.megagps.simulator.domain.GeoPoint

/**
 * Delivers simulated coordinates through Android's official test-provider API
 * (LocationManager.addTestProvider / setTestProviderLocation). This only works while the user has
 * selected this app under Developer options > "Select mock location app". No root, no hooks.
 *
 * Works without Google Play Services: apps that use the fused provider receive these fixes too,
 * because the fused provider is built from the GPS and network providers we mock.
 *
 * Limitation: apps can detect mock locations (Location.isMock / isFromMockProvider) and may also
 * use Wi-Fi, cell or IP signals. This app does not try to hide anything.
 */
class MockLocationProvider(context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val active = ArrayList<String>()

    /** True when this app is currently the selected mock-location app (or is already mocking). */
    fun isAllowed(): Boolean {
        if (active.isNotEmpty()) return true
        return try {
            addProvider(LocationManager.GPS_PROVIDER)
            lm.removeTestProvider(LocationManager.GPS_PROVIDER)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            true // a test provider already exists => we are allowed
        }
    }

    /** Registers GPS (required) and network (best effort) test providers. */
    fun start(): Boolean {
        stop()
        try {
            register(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            stop()
            return false
        }
        try {
            register(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            // network provider is optional
        }
        return active.isNotEmpty()
    }

    private fun register(name: String) {
        try { lm.removeTestProvider(name) } catch (_: Exception) {}
        addProvider(name)
        lm.setTestProviderEnabled(name, true)
        active.add(name)
    }

    @Suppress("DEPRECATION")
    private fun addProvider(name: String) {
        lm.addTestProvider(
            name, false, false, false, false, true, true, true,
            Criteria.POWER_LOW, Criteria.ACCURACY_FINE,
        )
    }

    /** Pushes one fix to every active test provider. Returns false if none accepted it. */
    fun push(point: GeoPoint, speedMs: Double, bearingDeg: Double): Boolean {
        var ok = false
        for (name in active) {
            val loc = Location(name).apply {
                latitude = point.lat
                longitude = point.lon
                altitude = 0.0
                accuracy = if (name == LocationManager.GPS_PROVIDER) 3f else 12f
                speed = speedMs.toFloat()
                bearing = bearingDeg.toFloat()
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                if (Build.VERSION.SDK_INT >= 26) {
                    verticalAccuracyMeters = 3f
                    speedAccuracyMetersPerSecond = 0.5f
                    bearingAccuracyDegrees = 5f
                }
            }
            try {
                lm.setTestProviderLocation(name, loc)
                ok = true
            } catch (_: Exception) {
            }
        }
        return ok
    }

    fun stop() {
        for (name in active) {
            try { lm.setTestProviderEnabled(name, false) } catch (_: Exception) {}
            try { lm.removeTestProvider(name) } catch (_: Exception) {}
        }
        active.clear()
    }
}
