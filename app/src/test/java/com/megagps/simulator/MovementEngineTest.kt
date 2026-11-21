
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

    // ~400 m straight north.
    private val straight = listOf(GeoPoint(35.0, 51.0), GeoPoint(35.0036, 51.0))

    @Test fun waitsAtStopThenContinues() {
        val path = RoutePath(straight)
        val e = MovementEngine(path, MoveMode.CYCLING, 20.0, human = false, pauses = false,
            stops = listOf(StopAt(200.0, 10.0)))
        var waited = 0
        var steps = 0
        while (!e.completed && steps < 2000) {
            val s = e.step(1.0)
            if (s.waitSeconds > 0.0) { waited++; assertEquals(0.0, s.speedMs, 1e-9) }
            steps++
        }
        assertTrue(e.completed)
        assertTrue("waited $waited s", waited in 9..11)
    }

    @Test fun trafficMakesDrivingSlowerWithoutJumps() {
        val long = listOf(GeoPoint(35.0, 51.0), GeoPoint(35.045, 51.0)) // ~5 km
        fun run(t: Traffic): Int {
            val e = MovementEngine(RoutePath(long), MoveMode.DRIVING, 50.0, human = false, pauses = false,
                traffic = t, rnd = Random(7))
            var prev = e.snapshot().point
            var n = 0
            while (!e.completed && n < 5000) {
                val s = e.step(1.0)
                assertTrue(Geo.distance(prev, s.point) < 30.0)
                prev = s.point; n++
            }
            assertTrue(e.completed)
            return n
        }
        assertTrue(run(Traffic.HIGH) > run(Traffic.OFF) * 1.1)
    }

    @Test fun verySlowCustomSpeedIsSupported() {
        assertEquals(0.1, MoveMode.CUSTOM.clampSpeed(0.0), 1e-9)
        val e = MovementEngine(RoutePath(straight), MoveMode.CUSTOM, 0.5, human = false, pauses = false)
        repeat(10) { e.step(1.0) }
        assertEquals(0.5 / 3.6, e.step(1.0).speedMs, 0.02)
    }

    @Test fun autoTrafficFollowsRushHours() {
        assertEquals(Traffic.HIGH, TrafficPlanner.auto(8.0))
        assertEquals(Traffic.LOW, TrafficPlanner.auto(2.0))
        assertEquals(Traffic.OFF, TrafficPlanner.resolve(false, TrafficSetting.HIGH, 8.0))
    }

    @Test fun loopRouteCompletes() {
        val loop = listOf(GeoPoint(35.0, 51.0), GeoPoint(35.0, 51.001), GeoPoint(35.001, 51.001), GeoPoint(35.0, 51.0))
        val e = MovementEngine(RoutePath(loop), MoveMode.RUNNING, 12.0, human = false, pauses = false)
        var n = 0
        while (!e.completed && n < 2000) { e.step(1.0); n++ }
        assertTrue(e.completed)
    }

    @Test fun extendingRouteMakesFinishedTravellerContinue() {
        val first = listOf(GeoPoint(35.0, 51.0), GeoPoint(35.0018, 51.0)) // ~200 m
        val e = MovementEngine(RoutePath(first), MoveMode.CYCLING, 20.0, human = false, pauses = false)
        var n = 0
        while (!e.completed && n < 2000) { e.step(1.0); n++ }
        assertTrue(e.completed)
        val longer = first + GeoPoint(35.0036, 51.0) // another ~200 m north
        assertTrue(e.updateRoute(RoutePath(longer), emptyList()))
        assertFalse(e.completed)
        n = 0
        while (!e.completed && n < 2000) { e.step(1.0); n++ }
        assertTrue(e.completed)
        assertEquals(RoutePath(longer).total, e.snapshot().traveledM, 5.0)
    }

    @Test fun changedRouteIsNotAppliedWhileRunning() {
        val first = listOf(GeoPoint(35.0, 51.0), GeoPoint(35.0018, 51.0))
        val e = MovementEngine(RoutePath(first), MoveMode.CYCLING, 20.0, human = false, pauses = false)
        val other = listOf(GeoPoint(35.0, 51.0), GeoPoint(35.0, 51.002))
        assertFalse(e.updateRoute(RoutePath(other), emptyList()))
    }
}
