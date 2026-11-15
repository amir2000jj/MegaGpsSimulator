
package com.megagps.simulator.domain

import kotlin.random.Random

/**
 * Simulated (not real) traffic for DRIVING mode. Real traffic data needs a paid API key, so this
 * generates congested sections along the route; inside them the speed drops (and in heavy traffic
 * the car sometimes crawls to a stop) and then recovers smoothly.
 */
enum class Traffic(
    val zonesPerKm: Double,
    val minFactor: Double,
    val maxFactor: Double,
    val minLenM: Double,
    val maxLenM: Double,
) {
    OFF(0.0, 1.0, 1.0, 0.0, 0.0),
    LOW(0.5, 0.65, 0.85, 100.0, 400.0),
    MEDIUM(1.0, 0.40, 0.65, 150.0, 600.0),
    HIGH(1.6, 0.12, 0.40, 200.0, 900.0),
}

/** A congested stretch [start, end] (metres along the route) where speed is scaled by [factor]. */
data class CongestionZone(val start: Double, val end: Double, val factor: Double)

object TrafficPlanner {
    /** Picks a level from the setting; AUTO guesses it from the time of day (a guess, not live data). */
    fun resolve(enabled: Boolean, setting: TrafficSetting, hourOfDay: Double): Traffic {
        if (!enabled) return Traffic.OFF
        return when (setting) {
            TrafficSetting.LOW -> Traffic.LOW
            TrafficSetting.MEDIUM -> Traffic.MEDIUM
            TrafficSetting.HIGH -> Traffic.HIGH
            TrafficSetting.AUTO -> auto(hourOfDay)
        }
    }

    fun auto(h: Double): Traffic = when {
        h in 7.0..9.5 || h in 16.5..19.5 -> Traffic.HIGH
        h in 6.0..7.0 || h in 9.5..16.5 || h in 19.5..21.0 -> Traffic.MEDIUM
        else -> Traffic.LOW
    }

    /** Random but non-overlapping congested sections; none in the first ~200 m or the last ~50 m. */
    fun generateZones(total: Double, t: Traffic, rnd: Random): List<CongestionZone> {
        if (t == Traffic.OFF || total < 400.0) return emptyList()
        val meanGap = 1000.0 / t.zonesPerKm
        val zones = ArrayList<CongestionZone>()
        var pos = 200.0 + rnd.nextDouble() * meanGap * 0.5
        while (pos < total - 150.0) {
            val len = t.minLenM + rnd.nextDouble() * (t.maxLenM - t.minLenM)
            val end = minOf(total - 50.0, pos + len)
            if (end - pos < 80.0) break
            zones.add(CongestionZone(pos, end, t.minFactor + rnd.nextDouble() * (t.maxFactor - t.minFactor)))
            pos = end + meanGap * (0.5 + rnd.nextDouble())
        }
        return zones
    }
}
