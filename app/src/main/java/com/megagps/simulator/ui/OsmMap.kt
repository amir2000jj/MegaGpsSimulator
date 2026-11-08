package com.megagps.simulator.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.megagps.simulator.R
import com.megagps.simulator.domain.GeoPoint
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import org.osmdroid.util.GeoPoint as OsmPoint

/** What the map should show. Real, simulated and selected points are separate on purpose. */
data class MapDisplay(
    val selected: GeoPoint? = null,
    val simulated: GeoPoint? = null,
    val real: GeoPoint? = null,
    val route: List<GeoPoint> = emptyList(),
    val waypoints: List<GeoPoint> = emptyList(),
    val editableWaypoints: Boolean = false,
)

/** Ask the map to centre on one point, or fit several points. [id] makes each request unique. */
data class FocusRequest(val points: List<GeoPoint>, val zoom: Double? = null, val id: Long = System.nanoTime())

private fun GeoPoint.toOsm() = OsmPoint(lat, lon)

@Composable
fun OsmMap(
    modifier: Modifier,
    display: MapDisplay,
    focus: FocusRequest?,
    onTap: (GeoPoint) -> Unit,
    onWaypointMoved: (Int, GeoPoint) -> Unit = { _, _ -> },
) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val tap = rememberUpdatedState(onTap)
    val moved = rememberUpdatedState(onWaypointMoved)

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
            osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(5.0)
            controller.setCenter(OsmPoint(35.6892, 51.3890))
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmPoint): Boolean {
                    tap.value(GeoPoint(p.latitude, p.longitude)); return true
                }
                override fun longPressHelper(p: OsmPoint): Boolean = false
            }))
        }
    }

    LifecycleResumeEffect(mapView) {
        mapView.onResume()
        onPauseOrDispose { mapView.onPause() }
    }
    DisposableEffect(mapView) { onDispose { mapView.onDetach() } }

    LaunchedEffect(focus?.id) {
        val f = focus ?: return@LaunchedEffect
        if (f.points.size == 1) {
            f.zoom?.let { mapView.controller.setZoom(it) }
            mapView.controller.animateTo(f.points[0].toOsm())
        } else if (f.points.size > 1) {
            mapView.post {
                mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(f.points.map { it.toOsm() }), true, 100)
            }
        }
    }

    fun marker(mv: MapView, p: GeoPoint, icon: Int, title: String?): Marker = Marker(mv).apply {
        position = p.toOsm()
        this.icon = ContextCompat.getDrawable(ctx, icon)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        this.title = title
    }

    AndroidView(modifier = modifier, factory = { mapView }, update = { mv ->
        mv.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        mv.overlays.removeAll { it !is MapEventsOverlay }

        if (display.route.size >= 2) {
            mv.overlays.add(Polyline().apply {
                setPoints(display.route.map { it.toOsm() })
                outlinePaint.color = 0xFF1E88E5.toInt()
                outlinePaint.strokeWidth = 10f
            })
        }
        display.waypoints.forEachIndexed { i, p ->
            mv.overlays.add(marker(mv, p, R.drawable.ic_marker_wp, "#${i + 1}").apply {
                isDraggable = display.editableWaypoints
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDrag(m: Marker) {}
                    override fun onMarkerDragStart(m: Marker) {}
                    override fun onMarkerDragEnd(m: Marker) {
                        moved.value(i, GeoPoint(m.position.latitude, m.position.longitude))
                    }
                })
            })
        }
        display.selected?.let { mv.overlays.add(marker(mv, it, R.drawable.ic_marker_sel, null)) }
        display.real?.let { mv.overlays.add(marker(mv, it, R.drawable.ic_marker_real, null)) }
        display.simulated?.let { mv.overlays.add(marker(mv, it, R.drawable.ic_marker_sim, null)) }
        mv.invalidate()
    })
}
