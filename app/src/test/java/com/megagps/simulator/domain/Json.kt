
package com.megagps.simulator.data

import com.megagps.simulator.domain.GeoPoint
import com.megagps.simulator.domain.RouteDraft
import com.megagps.simulator.domain.SavedPlace
import com.megagps.simulator.domain.SavedRoute
import org.json.JSONArray
import org.json.JSONObject

/** Tiny org.json based (de)serialisation used by the DataStore repository. */
object Json {
    fun pointsToArray(list: List<GeoPoint>): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(JSONArray().put(it.lat).put(it.lon)) }
        return a
    }

    private fun arrayToPoints(a: JSONArray): List<GeoPoint> =
        List(a.length()) { i -> a.getJSONArray(i).let { GeoPoint(it.getDouble(0), it.getDouble(1)) } }

    private fun intsToArray(list: List<Int>): JSONArray {
        val a = JSONArray()
        list.forEach { a.put(it) }
        return a
    }

    /** Stops are kept parallel to the waypoints: pad with 0 / truncate to [n]. */
    private fun arrayToStops(a: JSONArray?, n: Int): List<Int> =
        List(n) { i -> if (a != null && i < a.length()) a.optInt(i, 0).coerceIn(0, 3600) else 0 }

    fun points(list: List<GeoPoint>): String = pointsToArray(list).toString()

    fun parsePoints(s: String?): List<GeoPoint> = try {
        if (s == null) emptyList() else arrayToPoints(JSONArray(s))
    } catch (e: Exception) {
        emptyList()
    }

    // ---- route being edited -------------------------------------------------------------------

    fun draft(d: RouteDraft): String {
        val o = JSONObject()
            .put("wps", pointsToArray(d.waypoints))
            .put("stops", intsToArray(d.stops))
        if (d.routed != null) o.put("routed", pointsToArray(d.routed))
        return o.toString()
    }

    /** Reads the new object format and the old format (a plain array of points). */
    fun parseDraft(s: String?): RouteDraft = try {
        when {
            s == null -> RouteDraft()
            s.trimStart().startsWith("[") -> {
                val pts = arrayToPoints(JSONArray(s))
                if (pts.size <= 25) RouteDraft(pts, List(pts.size) { 0 }, null)
                else RouteDraft(listOf(pts.first(), pts.last()), listOf(0, 0), pts)
            }
            else -> {
                val o = JSONObject(s)
                val wps = arrayToPoints(o.getJSONArray("wps"))
                val routed = o.optJSONArray("routed")?.let { arrayToPoints(it) }
                RouteDraft(wps, arrayToStops(o.optJSONArray("stops"), wps.size), routed)
            }
        }
    } catch (e: Exception) {
        RouteDraft()
    }

    // ---- saved places -------------------------------------------------------------------------

    fun places(list: List<SavedPlace>): String {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("lat", it.point.lat).put("lon", it.point.lon))
        }
        return a.toString()
    }

    fun parsePlaces(s: String?): List<SavedPlace> = try {
        if (s == null) emptyList() else JSONArray(s).let { a ->
            List(a.length()) { i ->
                a.getJSONObject(i).let {
                    SavedPlace(it.getString("id"), it.getString("name"), GeoPoint(it.getDouble("lat"), it.getDouble("lon")))
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    // ---- saved routes -------------------------------------------------------------------------

    fun routes(list: List<SavedRoute>): String {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("name", it.name).put("pts", pointsToArray(it.points))
                    .put("wps", pointsToArray(it.waypoints)).put("stops", intsToArray(it.stops))
            )
        }
        return a.toString()
    }

    fun parseRoutes(s: String?): List<SavedRoute> = try {
        if (s == null) emptyList() else JSONArray(s).let { a ->
            List(a.length()) { i ->
                a.getJSONObject(i).let {
                    val wps = it.optJSONArray("wps")?.let { w -> arrayToPoints(w) } ?: emptyList()
                    SavedRoute(
                        it.getString("id"), it.getString("name"), arrayToPoints(it.getJSONArray("pts")),
                        wps, arrayToStops(it.optJSONArray("stops"), wps.size),
                    )
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
