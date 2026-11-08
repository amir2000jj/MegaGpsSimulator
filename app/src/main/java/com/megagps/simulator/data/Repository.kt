package com.megagps.simulator.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.megagps.simulator.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "mega_gps")

/**
 * Persistence (Preferences DataStore). Nothing here ever starts a simulation:
 * the app never auto-starts after reboot or app restart.
 */
class Repository(context: Context) {
    private val store = context.applicationContext.dataStore

    private object K {
        val MODE = stringPreferencesKey("mode")
        val INTERVAL = longPreferencesKey("interval_ms")
        val HUMAN = booleanPreferencesKey("human_walking")
        val PAUSES = booleanPreferencesKey("random_pauses")
        val LAST_POINT = stringPreferencesKey("last_point")
        val LAST_ROUTE = stringPreferencesKey("last_route")
        val PLACES = stringPreferencesKey("places")
        val ROUTES = stringPreferencesKey("routes")
        fun speed(m: MoveMode) = doublePreferencesKey("speed_${m.name}")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            mode = p[K.MODE]?.let { runCatching { MoveMode.valueOf(it) }.getOrNull() } ?: MoveMode.WALKING,
            speeds = MoveMode.values().mapNotNull { m -> p[K.speed(m)]?.let { m to it } }.toMap(),
            intervalMs = p[K.INTERVAL] ?: 1000L,
            humanWalking = p[K.HUMAN] ?: true,
            randomPauses = p[K.PAUSES] ?: false,
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings.first())
        store.edit { p ->
            p[K.MODE] = next.mode.name
            p[K.INTERVAL] = next.intervalMs
            p[K.HUMAN] = next.humanWalking
            p[K.PAUSES] = next.randomPauses
            next.speeds.forEach { (m, v) -> p[K.speed(m)] = v }
        }
    }

    val lastPoint: Flow<GeoPoint?> = store.data.map { Json.parsePoints(it[K.LAST_POINT]).firstOrNull() }
    val lastRoute: Flow<List<GeoPoint>> = store.data.map { Json.parsePoints(it[K.LAST_ROUTE]) }
    val places: Flow<List<SavedPlace>> = store.data.map { Json.parsePlaces(it[K.PLACES]) }
    val routes: Flow<List<SavedRoute>> = store.data.map { Json.parseRoutes(it[K.ROUTES]) }

    suspend fun saveLastPoint(p: GeoPoint) { store.edit { it[K.LAST_POINT] = Json.points(listOf(p)) } }
    suspend fun saveLastRoute(r: List<GeoPoint>) { store.edit { it[K.LAST_ROUTE] = Json.points(r) } }
    suspend fun savePlaces(l: List<SavedPlace>) { store.edit { it[K.PLACES] = Json.places(l) } }
    suspend fun saveRoutes(l: List<SavedRoute>) { store.edit { it[K.ROUTES] = Json.routes(l) } }
}
