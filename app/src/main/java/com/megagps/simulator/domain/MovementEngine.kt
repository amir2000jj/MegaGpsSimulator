package com.megagps.simulator.domain

import kotlin.math.*
import kotlin.random.Random

/**
 * Moves a virtual traveller along a [RoutePath]. Pure Kotlin (no Android types) so it is unit-testable.
 *
 * Guarantees:
 *  - the position is always exactly on the route (no random lateral deviation);
 *  - speed changes are rate-limited by the mode's acceleration, so there are no position jumps;
 *  - the heading turns at a limited rate and speed drops slightly before sharp corners;
 *  - the traveller brakes smoothly before the end of the route.
 *
 * Human walking (WALKING mode + [human]) adds a small mean-reverting speed variation (max +-20%)
 * and optional short stops; the variation only changes *speed along the route*.
 */
class MovementEngine(
    val path: RoutePath,
    mode: MoveMode,
    speedKmh: Double,
    private val human: Boolean,
    private val pauses: Boolean,
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
    )

    var mode: MoveMode = mode
        private set
    var speedKmh: Double = mode.clampSpeed(speedKmh)
        private set
    var direction: Int = 1
        private set
    var completed: Boolean = false
        private set

    private var distance = 0.0
    private var odometer = 0.0 // cumulative distance actually travelled (all directions)
    private var currentSpeed = 0.0
    private var heading = path.bearingAt(0.0, 1)
    private var noise = 0.0
    private var pauseLeft = 0.0

    /** Changes mode/speed while running; the actual speed ramps to the new target. */
    fun setSpeed(mode: MoveMode, kmh: Double) {
        this.mode = mode
        this.speedKmh = mode.clampSpeed(kmh)
    }

    /** Reverses travel direction. Returns false when there is no room to move that way. */
    fun reverse(): Boolean {
        val newDir = -direction
        val room = if (newDir > 0) path.total - distance else distance
        if (room < 0.5) return false
        direction = newDir
        currentSpeed = 0.0
        completed = false
        heading = path.bearingAt(distance, direction)
        return true
    }

    fun snapshot(): Sample {
        val remaining = remaining()
        return Sample(
            path.pointAt(distance), currentSpeed, heading,
            odometer, path.total, remaining, completed,
        )
    }

    /** Advances the simulation by [dt] seconds of wall-clock time. */
    fun step(dt: Double): Sample {
        if (completed || dt <= 0.0) return snapshot()

        val base = speedKmh / 3.6
        var target = base

        if (human && mode == MoveMode.WALKING) {
            noise += -0.5 * noise * dt + 0.06 * sqrt(dt) * gauss()
            noise = noise.coerceIn(-0.2, 0.2)
            target = base * (1.0 + noise)
            if (pauses) {
                if (pauseLeft > 0.0) {
                    pauseLeft -= dt
                    target = 0.0
                } else if (rnd.nextDouble() < 0.004 * dt) {
                    pauseLeft = 1.5 + rnd.nextDouble() * 2.5
                }
            }
        }

        // Slow down for sharp corners ahead.
        val here = path.bearingAt(distance, direction)
        val lookAhead = max(5.0, currentSpeed * 2.0)
        val aheadD = (distance + direction * lookAhead).coerceIn(0.0, path.total)
        val turn = abs(Geo.angleDiff(here, path.bearingAt(aheadD, direction)))
        if (turn > 20.0) {
            val cornerSpeed = base * (1.0 - 0.7 * (turn / 180.0))
            target = min(target, max(cornerSpeed, min(base, 1.5)))
        }

        // Brake smoothly towards the end of the route.
        val a = mode.accel
        val remainingBefore = remaining()
        target = min(target, max(0.4, sqrt(2.0 * a * remainingBefore)))

        // Rate-limited acceleration / (stronger) deceleration.
        currentSpeed = if (target > currentSpeed) min(target, currentSpeed + a * dt)
        else max(target, currentSpeed - 1.5 * a * dt)

        val move = min(currentSpeed * dt, remainingBefore)
        distance += direction * move
        odometer += move

        if (remainingBefore - move <= 0.3) {
            completed = true
            currentSpeed = 0.0
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
