package com.megagps.simulator.service

import com.megagps.simulator.domain.RouteUpdate
import com.megagps.simulator.domain.SimConfig
import com.megagps.simulator.domain.SimState
import kotlinx.coroutines.flow.MutableStateFlow

/** In-process bridge between the foreground service (writer) and the UI (reader). */
object SimulationHub {
    val state = MutableStateFlow(SimState())

    /** Large route data does not fit in an Intent, so the UI hands it over here right before starting. */
    @Volatile
    var pending: SimConfig? = null

    /** Route extension for a running simulation (same reason: too large for an Intent). */
    @Volatile
    var pendingUpdate: RouteUpdate? = null
}
