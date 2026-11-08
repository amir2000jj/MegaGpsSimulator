package com.megagps.simulator.ui

import android.app.Application
import android.content.Context
import android.location.LocationManager
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.megagps.simulator.R
import com.megagps.simulator.data.*
import com.megagps.simulator.domain.*
import com.megagps.simulator.service.MockLocationProvider
import com.megagps.simulator.service.RealLocation
import com.megagps.simulator.service.SimulationHub
import com.megagps.simulator.service.SimulationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class UiState(
    val selected: GeoPoint? = null,
    val waypoints: List<GeoPoint> = emptyList(),
    val routed: List<GeoPoint>? = null,
    val real: GeoPoint? = null,
    val focus: FocusRequest? = null,
    val results: List<PlaceResult> = emptyList(),
    val busy: Boolean = false,
) {
    /** The polyline that will be simulated: routed geometry if present, else straight legs between waypoints. */
    val polyline: List<GeoPoint> get() = routed ?: waypoints
}

data class SetupStatus(
    val permission: Boolean = false,
    val mockSelected: Boolean = false,
    val locationEnabled: Boolean = true,
) {
    val ready: Boolean get() = permission && mockSelected && locationEnabled
}

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val searchService: PlaceSearchService = NominatimSearch()
    private val routingService: RoutingService = OsrmRouting()

    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val places = repo.places.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val savedRoutes = repo.routes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sim = SimulationHub.state

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    private val _setup = MutableStateFlow(SetupStatus())
    val setup: StateFlow<SetupStatus> = _setup

    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val events: SharedFlow<Int> = _events
    fun toast(@StringRes res: Int) { _events.tryEmit(res) }

    init {
        viewModelScope.launch {
            val point = repo.lastPoint.first()
            val route = repo.lastRoute.first()
            _ui.update { it.copy(selected = point, waypoints = route, focus = point?.let { p -> FocusRequest(listOf(p), 14.0) }) }
        }
        refreshSetup()
    }

    // ---- setup / prerequisites -----------------------------------------------------------------

    fun refreshSetup() {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        _setup.value = SetupStatus(
            permission = hasLocationPermission(app),
            mockSelected = sim.value.isActive || MockLocationProvider(app).isAllowed(),
            locationEnabled = LocationManagerCompat.isLocationEnabled(lm),
        )
        // A stale error banner disappears once the problem is fixed.
        if (sim.value.status == SimStatus.ERROR && _setup.value.ready) SimulationHub.state.value = SimState()
    }

    /** Fresh check used right before starting. */
    fun checkMockAllowed(): Boolean { refreshSetup(); return _setup.value.mockSelected }

    // ---- map / single point --------------------------------------------------------------------

    fun selectPoint(p: GeoPoint, focus: Boolean = false) {
        _ui.update { it.copy(selected = p, results = emptyList(), focus = if (focus) FocusRequest(listOf(p), 15.0) else it.focus) }
        viewModelScope.launch { repo.saveLastPoint(p) }
    }

    fun setManual(latText: String, lonText: String) {
        val lat = parseNum(latText)
        val lon = parseNum(lonText)
        val p = if (lat != null && lon != null) GeoPoint(lat, lon) else null
        if (p == null || !p.isValid) toast(R.string.msg_invalid_coords) else selectPoint(p, true)
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val r = searchService.search(query)
                _ui.update { it.copy(results = r) }
                if (r.isEmpty()) toast(R.string.msg_no_results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(R.string.msg_network_error)
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    fun pickResult(r: PlaceResult) = selectPoint(r.point, true)

    fun showRealLocation() {
        if (sim.value.isActive) { toast(R.string.msg_real_while_sim); return }
        viewModelScope.launch {
            val p = RealLocation.get(app)
            if (p == null) toast(R.string.msg_real_unavailable)
            else _ui.update { it.copy(real = p, focus = FocusRequest(listOf(p), 16.0)) }
        }
    }

    // ---- route editing -------------------------------------------------------------------------

    private fun setWaypoints(list: List<GeoPoint>, routed: List<GeoPoint>? = null, focus: Boolean = false) {
        _ui.update {
            it.copy(waypoints = list, routed = routed,
                focus = if (focus) FocusRequest(routed ?: list) else it.focus)
        }
        viewModelScope.launch { repo.saveLastRoute(_ui.value.polyline) }
    }

    fun addWaypoint(p: GeoPoint) = setWaypoints(_ui.value.waypoints + p)
    fun removeWaypoint(i: Int) = setWaypoints(_ui.value.waypoints.toMutableList().also { it.removeAt(i) })
    fun moveWaypoint(i: Int, p: GeoPoint) = setWaypoints(_ui.value.waypoints.toMutableList().also { it[i] = p })
    fun shiftWaypoint(i: Int, delta: Int) {
        val l = _ui.value.waypoints.toMutableList()
        val j = i + delta
        if (j !in l.indices) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        setWaypoints(l)
    }
    fun clearRoute() = setWaypoints(emptyList())
    fun reverseRoute() {
        val s = _ui.value
        setWaypoints(s.waypoints.reversed(), s.routed?.reversed())
    }

    /** Optional online routing between the user's waypoints. */
    fun fetchRoute() {
        val wps = _ui.value.waypoints
        if (wps.size < 2) { toast(R.string.msg_need_two_points); return }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val geometry = routingService.route(wps, settings.value.mode)
                setWaypoints(wps, geometry, focus = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(R.string.msg_route_failed)
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    fun importGpx(uri: Uri) {
        viewModelScope.launch {
            val pts = try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { GpxParser.parse(it) } ?: emptyList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (pts.size < 2) toast(R.string.msg_gpx_invalid) else applyPolyline(pts)
        }
    }

    private fun applyPolyline(pts: List<GeoPoint>) {
        if (pts.size <= 25) setWaypoints(pts, null, focus = true)
        else setWaypoints(listOf(pts.first(), pts.last()), pts, focus = true)
    }

    // ---- saved places / routes -----------------------------------------------------------------

    fun savePlace(name: String) {
        val p = _ui.value.selected ?: return
        val n = name.ifBlank { "${fmt(p.lat, 5)}, ${fmt(p.lon, 5)}" }
        viewModelScope.launch { repo.savePlaces(places.value + SavedPlace(UUID.randomUUID().toString(), n, p)) }
        toast(R.string.msg_saved)
    }
    fun deletePlace(id: String) { viewModelScope.launch { repo.savePlaces(places.value.filter { it.id != id }) } }
    fun usePlace(pl: SavedPlace) = selectPoint(pl.point, true)

    fun saveRoute(name: String) {
        val pts = _ui.value.polyline
        if (pts.size < 2) { toast(R.string.msg_need_two_points); return }
        val n = name.ifBlank { "Route ${savedRoutes.value.size + 1}" }
        viewModelScope.launch { repo.saveRoutes(savedRoutes.value + SavedRoute(UUID.randomUUID().toString(), n, pts)) }
        toast(R.string.msg_saved)
    }
    fun deleteRoute(id: String) { viewModelScope.launch { repo.saveRoutes(savedRoutes.value.filter { it.id != id }) } }
    fun loadRoute(r: SavedRoute) = applyPolyline(r.points)

    // ---- settings ------------------------------------------------------------------------------

    private fun update(f: (AppSettings) -> AppSettings) { viewModelScope.launch { repo.updateSettings(f) } }

    fun setMode(m: MoveMode) {
        update { it.copy(mode = m) }
        if (sim.value.isActive) SimulationService.command(app, SimulationService.ACTION_SPEED, m, settings.value.speedOf(m))
    }

    fun setSpeed(kmh: Double) {
        val m = settings.value.mode
        val v = m.clampSpeed(kmh)
        update { it.copy(speeds = it.speeds + (m to v)) }
        if (sim.value.isActive) SimulationService.command(app, SimulationService.ACTION_SPEED, m, v)
    }

    fun setInterval(ms: Long) = update { it.copy(intervalMs = ms) }
    fun setHuman(v: Boolean) = update { it.copy(humanWalking = v) }
    fun setPauses(v: Boolean) = update { it.copy(randomPauses = v) }

    // ---- simulation commands -------------------------------------------------------------------

    private fun startSim(points: List<GeoPoint>) {
        val s = settings.value
        SimulationService.start(app, SimConfig(points, s.mode, s.speedOf(), s.humanWalking, s.randomPauses, s.intervalMs))
    }

    fun startPoint() {
        val p = _ui.value.selected
        if (p == null) toast(R.string.msg_select_point) else startSim(listOf(p))
    }

    fun startRoute() {
        val r = _ui.value.polyline
        if (r.size < 2) toast(R.string.msg_need_two_points) else startSim(r)
    }

    fun pause() = SimulationService.command(app, SimulationService.ACTION_PAUSE)
    fun resume() = SimulationService.command(app, SimulationService.ACTION_RESUME)
    fun stop() = SimulationService.command(app, SimulationService.ACTION_STOP)
    fun restart() = SimulationService.command(app, SimulationService.ACTION_RESTART)
    fun reverse() = SimulationService.command(app, SimulationService.ACTION_REVERSE)
}
