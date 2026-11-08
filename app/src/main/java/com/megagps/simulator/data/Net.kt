package com.megagps.simulator.data

import com.megagps.simulator.domain.GeoPoint
import com.megagps.simulator.domain.MoveMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class PlaceResult(val name: String, val point: GeoPoint)

/** Optional place search. Only the typed query text leaves the device - never any location. */
interface PlaceSearchService {
    suspend fun search(query: String): List<PlaceResult>
}

/** Optional online routing. Only the waypoints the user drew are sent - never the real location. */
interface RoutingService {
    suspend fun route(waypoints: List<GeoPoint>, mode: MoveMode): List<GeoPoint>
}

internal suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "MegaGPSSimulator/1.0 (Android)")
        c.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        c.inputStream.bufferedReader().use { it.readText() }
    } finally {
        c.disconnect()
    }
}

/** OpenStreetMap Nominatim (free, no API key). Swap this class to use another provider. */
class NominatimSearch : PlaceSearchService {
    override suspend fun search(query: String): List<PlaceResult> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val arr = JSONArray(httpGet("https://nominatim.openstreetmap.org/search?q=$q&format=jsonv2&limit=6"))
        return List(arr.length()) { i ->
            val o: JSONObject = arr.getJSONObject(i)
            PlaceResult(o.optString("display_name"), GeoPoint(o.getString("lat").toDouble(), o.getString("lon").toDouble()))
        }
    }
}

/** OSRM-compatible routing (FOSSGIS public servers, no API key). Swap this class to use another provider. */
class OsrmRouting : RoutingService {
    override suspend fun route(waypoints: List<GeoPoint>, mode: MoveMode): List<GeoPoint> {
        require(waypoints.size >= 2)
        val server = when (mode) {
            MoveMode.WALKING, MoveMode.RUNNING -> "routed-foot"
            MoveMode.CYCLING -> "routed-bike"
            else -> "routed-car"
        }
        val coords = waypoints.joinToString(";") { "${it.lon},${it.lat}" }
        val url = "https://routing.openstreetmap.de/$server/route/v1/driving/$coords?overview=full&geometries=geojson"
        val root = JSONObject(httpGet(url))
        val line = root.getJSONArray("routes").getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        return List(line.length()) { i -> line.getJSONArray(i).let { GeoPoint(it.getDouble(1), it.getDouble(0)) } }
    }
}
