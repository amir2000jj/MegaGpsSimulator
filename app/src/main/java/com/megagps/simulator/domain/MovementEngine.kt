package com.megagps.simulator.domain

import kotlin.math.*
import kotlin.random.Random

/** The traveller waits [seconds] when it reaches [distance] metres along the route. */
data class StopAt(val distance: Double, val seconds: Double)

/**
 * Moves a virtual traveller along a [RoutePath]. Pure Kotlin (no Android types) so it is unit-testable.
 *
 * Guarantees:
 *  - the position is always exactly on the route (no random lateral deviation);
 *  - speed changes are rate-limited by the mode's acceleration, so there are no position jumps;
 *  - the heading turns at a limited rate and speed drops slightly before sharp corners;
 *  - the traveller brakes smoothly before the end of the route and before each [StopAt].
 *
 * Natural speed variation (small, mean-reverting) is applied to human walking, to CUSTOM speed when
 * [customVariation] is on, and to driving when [traffic] is on. Simulated traffic slows the traveller
 * inside generated congested zones (DRIVING only).
 */
class MovementEngine(
    path: RoutePath,
    mode: MoveMode,
    speedKmh: Double,
    private val human: Boolean,
    private val pauses: Boolean,
    stops: List<StopAt> = emptyList(),
    private val traffic: Traffic = Traffic.OFF,
    private val customVariation: Boolean = false,
    private val rnd: Random = Random.Default,
) {
    data class Sample(
        val point: GeoPoint,
        val speedMs: Double,
        val headingDeg: Double,
        val traveledM: Double,
        val totalM: Double,
        val remainingM: Double,
        val completed: Boolean,
        val waitSeconds: Double = 0.0,
    )

    var mode: MoveMode = mode
        private set
    var speedKmh: Double = mode.clampSpeed(speedKmh)
        private set
    var direction: Int = 1
        private set
    var completed: Boolean = false
        private set

    /** The route being followed. It can be extended while running, see [updateRoute]. */
    var path: RoutePath = path
        private set

    private var distance = 0.0
    private var odometer = 0.0 // cumulative distance actually travelled (all directions)
    private var currentSpeed = 0.0
    private var heading = path.bearingAt(0.0, 1)
    private var noise = 0.0
    private var pauseLeft = 0.0
    private var crawlLeft = 0.0
    private var dwellLeft = 0.0

    private var stopList: List<StopAt> = stops.filter { it.seconds > 0.0 }.sortedBy { it.distance }
    private var stopDone = BooleanArray(stopList.size)
    private var zones: List<CongestionZone> = TrafficPlanner.generateZones(path.total, traffic, rnd)

    init {
        // A stop at the very start means: wait there before moving.
        stopList.forEachIndexed { i, s ->
            if (s.distance < 0.5) {
                stopDone[i] = true
                dwellLeft = max(dwellLeft, s.seconds)
            }
        }
    }

    /** Changes mode/speed while running; the actual speed ramps to the new target. */
    fun setSpeed(mode: MoveMode, kmh: Double) {
        this.mode = mode
        this.speedKmh = mode.clampSpeed(kmh)
    }

    /**
     * Applies an edited route to a running engine. Only accepted when [newPath] starts with exactly the
     * points of the current route (points were appended at the end, or nothing changed): the current
     * position stays valid, and a traveller that had already finished continues to the new end.
     * Returns false when the route was changed in any other way (it is then ignored).
     */
    fun updateRoute(newPath: RoutePath, newStops: List<StopAt>): Boolean {
        val old = path.points
        val np = newPath.points
        if (np.size < old.size) return false
        for (i in old.indices) if (np[i] != old[i]) return false

        if (np.size > old.size) {
            val oldTotal = path.total
            path = newPath
            // Traffic for the new stretch only; the part already generated is kept.
            val extra = TrafficPlanner.generateZones(path.total - oldTotal, traffic, rnd)
                .map { it.copy(start = it.start + oldTotal, end = it.end + oldTotal) }
            zones = zones + extra
            if (completed && direction > 0 && path.total - distance > 0.5) completed = false
        }
        applyStops(newStops)
        return true
    }

    /** Replaces the stops; those already passed (in the current direction) are not repeated. */
    private fun applyStops(list: List<StopAt>) {
        stopList = list.filter { it.seconds > 0.0 }.sortedBy { it.distance }
        stopDone = BooleanArray(stopList.size) { i ->
            val d = stopList[i].distance
            if (direction > 0) d <= distance + 0.3 else d >= distance - 0.3
        }
    }

    /** Reverses travel direction. Returns false when there is no room to move that way. */
    fun reverse(): Boolean {
        val newDir = -direction
        val room = if (newDir > 0) path.total - distance else distance
        if (room < 0.5) return false
        direction = newDir
        currentSpeed = 0.0
        completed = false
        dwellLeft = 0.0
        for (i in stopList.indices) stopDone[i] = abs(stopList[i].distance - distance) < 1.0
        heading = path.bearingAt(distance, direction)
        return true
    }

    fun snapshot(): Sample {
        val remaining = remaining()
        return Sample(
            path.pointAt(distance), currentSpeed, heading,
            odometer, path.total, remaining, completed, dwellLeft,
        )
    }

    /** Advances the simulation by [dt] seconds of wall-clock time. */
    fun step(dt: Double): Sample {
        if (completed || dt <= 0.0) return snapshot()

        // Waiting at a stop.
        if (dwellLeft > 0.0) {
            dwellLeft = max(0.0, dwellLeft - dt)
            currentSpeed = 0.0
            return snapshot()
        }

        val base = speedKmh / 3.6
        var target = base

        // Natural speed variation.
        val sigma: Double
        val limit: Double
        when {
            human && mode == MoveMode.WALKING -> { sigma = 0.06; limit = 0.20 }
            mode == MoveMode.CUSTOM && customVariation -> { sigma = 0.05; limit = 0.15 }
            mode == MoveMode.DRIVING && traffic != Traffic.OFF -> { sigma = 0.03; limit = 0.10 }
            else -> { sigma = 0.0; limit = 0.0 }
        }
        if (limit > 0.0) {
            noise += -0.5 * noise * dt + sigma * sqrt(dt) * gauss()
            noise = noise.coerceIn(-limit, limit)
            target = base * (1.0 + noise)
        } else {
            noise = 0.0
        }

        // Short random stops while walking.
        if (human && mode == MoveMode.WALKING && pauses) {
            if (pauseLeft > 0.0) {
                pauseLeft -= dt
                target = 0.0
            } else if (rnd.nextDouble() < 0.004 * dt) {
                pauseLeft = 1.5 + rnd.nextDouble() * 2.5
            }
        }

        // Simulated traffic (driving only): slow down smoothly before and inside congested zones.
        var deepJam = false
        if (mode == MoveMode.DRIVING && zones.isNotEmpty()) {
            val probe = (distance + direction * currentSpeed * 2.0).coerceIn(0.0, path.total)
            val z = zones.firstOrNull { probe >= it.start && probe <= it.end }
            if (z != null) {
                val x = min((probe - z.start) / 80.0, (z.end - probe) / 80.0).coerceIn(0.0, 1.0)
                val w = x * x * (3.0 - 2.0 * x)
                target *= 1.0 - w * (1.0 - z.factor)
                if (z.factor < 0.4 && w > 0.8) {
                    deepJam = true
                    if (crawlLeft > 0.0) {
                        crawlLeft -= dt
                        target = 0.0
                    } else if (rnd.nextDouble() < 0.04 * dt) {
                        crawlLeft = 2.0 + rnd.nextDouble() * 4.0
                    }
                }
            }
        }
        if (!deepJam) crawlLeft = 0.0

        // Slow down for sharp corners ahead.
        val here = path.bearingAt(distance, direction)
        val lookAhead = max(5.0, currentSpeed * 2.0)
        val aheadD = (distance + direction * lookAhead).coerceIn(0.0, path.total)
        val turn = abs(Geo.angleDiff(here, path.bearingAt(aheadD, direction)))
        if (turn > 20.0) {
            val cornerSpeed = base * (1.0 - 0.7 * (turn / 180.0))
            target = min(target, max(cornerSpeed, min(base, 1.5)))
        }

        val a = mode.accel
        val remainingBefore = remaining()

        // Brake smoothly towards the end of the route.
        target = min(target, max(0.4, sqrt(2.0 * a * remainingBefore)))

        // Brake smoothly towards the next stop in the direction of travel.
        var nextStop = -1
        var gap = Double.MAX_VALUE
        for (i in stopList.indices) {
            if (stopDone[i]) continue
            val g = (stopList[i].distance - distance) * direction
            if (g >= -0.3 && g < gap) { gap = g; nextStop = i }
        }
        if (nextStop >= 0) target = min(target, max(0.3, sqrt(2.0 * a * max(0.0, gap))))

        // Rate-limited acceleration / (stronger) deceleration.
        currentSpeed = if (target > currentSpeed) min(target, currentSpeed + a * dt)
        else max(target, currentSpeed - 1.5 * a * dt)

        var move = min(currentSpeed * dt, remainingBefore)
        if (nextStop >= 0) move = min(move, max(0.0, gap)) // never run past a stop
        distance += direction * move
        odometer += move

        // Arrived at a stop: wait there.
        if (nextStop >= 0 && gap - move <= 0.3) {
            distance = stopList[nextStop].distance
            stopDone[nextStop] = true
            currentSpeed = 0.0
            dwellLeft = stopList[nextStop].seconds
        }

        if (remainingBefore - move <= 0.3) {
            completed = true
            currentSpeed = 0.0
            dwellLeft = 0.0
            distance = if (direction > 0) path.total else 0.0
        }

        // Smooth heading.
        val wanted = path.bearingAt(distance, direction)
        val maxTurn = (360.0 / (1.0 + currentSpeed / 3.0)) * dt
        heading = (heading + Geo.angleDiff(heading, wanted).coerceIn(-maxTurn, maxTurn) + 360.0) % 360.0

        return snapshot()
    }

    private fun remaining(): Double = if (direction > 0) path.total - distance else distance

    private fun gauss(): Double =
        sqrt(-2.0 * ln(1.0 - rnd.nextDouble())) * cos(2.0 * PI * rnd.nextDouble())
}
