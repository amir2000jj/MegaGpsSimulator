
package com.megagps.simulator.domain

/** A plain geographic coordinate (WGS84). Real and simulated coordinates never share a type with I/O code. */
data class GeoPoint(val lat: Double, val lon: Double) {
    val isValid: Boolean get() = lat in -90.0..90.0 && lon in -180.0..180.0
}

enum class MoveMode(val presetsKmh: List<Double>, val defaultKmh: Double, val accel: Double) {
    WALKING(listOf(3.0, 4.0, 5.0, 6.0), 4.0, 1.2),
    RUNNING(listOf(8.0, 10.0, 12.0), 10.0, 2.0),
    CYCLING(listOf(15.0, 20.0, 25.0), 20.0, 1.5),
    DRIVING(listOf(30.0, 50.0, 80.0, 100.0), 50.0, 3.0),
    CUSTOM(emptyList(), 10.0, 2.0);

    fun clampSpeed(kmh: Double): Double = when (this) {
        WALKING -> kmh.coerceIn(2.0, 6.0)
        CUSTOM -> kmh.coerceIn(0.1, 300.0) // custom speed may be very slow
        else -> kmh.coerceIn(1.0, 300.0)
    }
}

data class SavedPlace(val id: String, val name: String, val point: GeoPoint)
/** A waypoint where the traveller waits [seconds] before continuing. */
data class StopPoint(val point: GeoPoint, val seconds: Int)

/**
 * Route being edited. [stops] has the same size as [waypoints] (seconds to wait at each one, 0 = none).
 * [routed] is an imported/online polyline that is followed instead of straight legs.
 */
data class RouteDraft(
    val waypoints: List<GeoPoint> = emptyList(),
    val stops: List<Int> = emptyList(),
    val routed: List<GeoPoint>? = null,
)

/** [points] is the polyline that is simulated; [waypoints]/[stops] allow editing it again later. */
data class SavedRoute(
    val id: String,
    val name: String,
    val points: List<GeoPoint>,
    val waypoints: List<GeoPoint> = emptyList(),
    val stops: List<Int> = emptyList(),
)

enum class TrafficSetting { LOW, MEDIUM, HIGH, AUTO }

data class AppSettings(
    val mode: MoveMode = MoveMode.WALKING,
    val speeds: Map<MoveMode, Double> = emptyMap(),
    val intervalMs: Long = 1000L,
    val humanWalking: Boolean = true,
    val randomPauses: Boolean = false,
    val trafficEnabled: Boolean = false,
    val trafficLevel: TrafficSetting = TrafficSetting.AUTO,
    val customVariation: Boolean = true,
) {
    fun speedOf(m: MoveMode = mode): Double = m.clampSpeed(speeds[m] ?: m.defaultKmh)
}

/** Everything the service needs to start a simulation (one point = static location, 2+ = route). */
data class SimConfig(
    val points: List<GeoPoint>,
    val mode: MoveMode,
    val speedKmh: Double,
    val humanWalking: Boolean,
    val randomPauses: Boolean,
    val intervalMs: Long,
    val stops: List<StopPoint> = emptyList(),
    val traffic: Traffic = Traffic.OFF,
    val customVariation: Boolean = true,
)

enum class SimStatus { IDLE, RUNNING, PAUSED, COMPLETED, ERROR }
enum class SimError { NONE, MOCK_NOT_ALLOWED, PERMISSION, FOREGROUND_DENIED }

data class SimState(
    val status: SimStatus = SimStatus.IDLE,
    val isRoute: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
    val speedKmh: Double = 0.0,
    val headingDeg: Double = 0.0,
    val totalM: Double = 0.0,
    val traveledM: Double = 0.0,
    val remainingM: Double = 0.0,
    val etaEpochMs: Long? = null,
    val reversed: Boolean = false,
    val waitSeconds: Int = 0,
    val error: SimError = SimError.NONE,
) {
    val isActive: Boolean
        get() = status == SimStatus.RUNNING || status == SimStatus.PAUSED || status == SimStatus.COMPLETED
}
