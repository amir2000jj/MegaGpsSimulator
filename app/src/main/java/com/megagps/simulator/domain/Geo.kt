package com.megagps.simulator.domain

import kotlin.math.*

/** Spherical-earth geometry helpers. Accurate to well under 0.5% for the distances used here. */
object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Haversine distance in metres. */
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dp = Math.toRadians(b.lat - a.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** Initial bearing a -> b in degrees [0, 360). */
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val p1 = Math.toRadians(a.lat)
        val p2 = Math.toRadians(b.lat)
        val dl = Math.toRadians(b.lon - a.lon)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Linear interpolation; segments are short so this is indistinguishable from great-circle. */
    fun interpolate(a: GeoPoint, b: GeoPoint, f: Double): GeoPoint =
        GeoPoint(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)

    /** Signed shortest rotation from -> to, in (-180, 180]. */
    fun angleDiff(from: Double, to: Double): Double = ((to - from + 540.0) % 360.0) - 180.0

    fun pathLength(points: List<GeoPoint>): Double {
        var sum = 0.0
        for (i in 1 until points.size) sum += distance(points[i - 1], points[i])
        return sum
    }
}

/** A polyline with cumulative distances so any distance-along-route maps to a coordinate in O(log n). */
class RoutePath(raw: List<GeoPoint>) {
    val points: List<GeoPoint>
    private val cum: DoubleArray
    val total: Double

    init {
        val cleaned = ArrayList<GeoPoint>(raw.size)
        for (p in raw) if (cleaned.isEmpty() || Geo.distance(cleaned.last(), p) > 0.05) cleaned.add(p)
        points = cleaned
        cum = DoubleArray(cleaned.size)
        for (i in 1 until cleaned.size) cum[i] = cum[i - 1] + Geo.distance(cleaned[i - 1], cleaned[i])
        total = if (cleaned.isEmpty()) 0.0 else cum[cleaned.size - 1]
    }

    private fun segmentAt(d: Double): Int {
        if (points.size < 2) return 0
        var idx = java.util.Arrays.binarySearch(cum, d)
        if (idx < 0) idx = -idx - 2
        return idx.coerceIn(0, points.size - 2)
    }

    fun pointAt(d: Double): GeoPoint {
        if (points.size == 1) return points[0]
        val dd = d.coerceIn(0.0, total)
        val i = segmentAt(dd)
        val len = cum[i + 1] - cum[i]
        return Geo.interpolate(points[i], points[i + 1], if (len <= 0.0) 0.0 else (dd - cum[i]) / len)
    }

    /** Heading of the segment under [d], flipped when travelling backwards. */
    fun bearingAt(d: Double, direction: Int): Double {
        if (points.size < 2) return 0.0
        val probe = if (direction >= 0) d + 1e-6 else d - 1e-6
        val i = segmentAt(probe.coerceIn(0.0, total))
        val b = Geo.bearing(points[i], points[i + 1])
        return if (direction >= 0) b else (b + 180.0) % 360.0
    }
}
