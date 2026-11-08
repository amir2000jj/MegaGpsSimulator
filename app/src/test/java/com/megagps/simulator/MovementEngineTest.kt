package com.megagps.simulator

import com.megagps.simulator.domain.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MovementEngineTest {
    // ~91 m east, then ~111 m north.
    private val line = listOf(GeoPoint(35.0, 51.0), GeoPoint(35.0, 51.001), GeoPoint(35.001, 51.001))

    @Test fun haversineOneDegreeLatitude() {
        assertEquals(111_195.0, Geo.distance(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 200.0)
    }

    @Test fun bearingNorthAndEast() {
        assertEquals(0.0, Geo.bearing(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 0.01)
        assertEquals(90.0, Geo.bearing(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0)), 0.01)
    }

    @Test fun walkingAtFourKmhIsAboutOnePointOneOneMetresPerSecond() {
        val e = MovementEngine(RoutePath(line), MoveMode.WALKING, 4.0, human = false, pauses = false)
        repeat(10) { e.step(1.0) }
        assertEquals(4.0 / 3.6, e.step(1.0).speedMs, 0.05)
    }

    @Test fun completesRouteWithoutJumps() {
        val e = MovementEngine(RoutePath(line), MoveMode.WALKING, 6.0, human = true, pauses = true, rnd = Random(1))
        var prev = e.snapshot().point
        var steps = 0
        while (!e.completed && steps < 5000) {
            val s = e.step(1.0)
            assertTrue("jump of ${Geo.distance(prev, s.point)} m", Geo.distance(prev, s.point) < 3.0)
            prev = s.point
            steps++
        }
        assertTrue(e.completed)
        assertEquals(0.0, e.snapshot().remainingM, 0.5)
    }

    @Test fun reverseGoesBackwards() {
        val e = MovementEngine(RoutePath(line), MoveMode.CYCLING, 20.0, human = false, pauses = false)
        repeat(10) { e.step(1.0) }
        val fromStart = Geo.distance(line[0], e.snapshot().point)
        assertTrue(e.reverse())
        repeat(5) { e.step(1.0) }
        assertTrue(Geo.distance(line[0], e.snapshot().point) < fromStart)
    }

    @Test fun speedChangeRampsSmoothly() {
        val e = MovementEngine(RoutePath(line), MoveMode.DRIVING, 30.0, human = false, pauses = false)
        repeat(8) { e.step(1.0) }
        e.setSpeed(MoveMode.DRIVING, 100.0)
        val s = e.step(1.0)
        assertTrue(s.speedMs < 100.0 / 3.6)
    }
}
