package com.megagps.simulator.data

import com.megagps.simulator.domain.GeoPoint
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

    fun points(list: List<GeoPoint>): String = pointsToArray(list).toString()

    fun parsePoints(s: String?): List<GeoPoint> = try {
        if (s == null) emptyList() else arrayToPoints(JSONArray(s))
    } catch (e: Exception) {
        emptyList()
    }

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

    fun routes(list: List<SavedRoute>): String {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("pts", pointsToArray(it.points)))
        }
        return a.toString()
    }

    fun parseRoutes(s: String?): List<SavedRoute> = try {
        if (s == null) emptyList() else JSONArray(s).let { a ->
            List(a.length()) { i ->
                a.getJSONObject(i).let {
                    SavedRoute(it.getString("id"), it.getString("name"), arrayToPoints(it.getJSONArray("pts")))
                }
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
