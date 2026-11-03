package com.example.tugasakhirfinal.Maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {

    // Rute berbentuk "L" di sekitar Surabaya: lurus ke utara ~1,1 km, lalu belok ke timur ~1,1 km.
    // Sengaja hanya 3 titik sudut, meniru overview_polyline yang sudah disederhanakan.
    private val start = RoutePoint(-7.2900, 112.7500)
    private val corner = RoutePoint(-7.2800, 112.7500)
    private val end = RoutePoint(-7.2800, 112.7600)
    private val lRoute = listOf(start, corner, end)

    @Test
    fun `trim cuts the line exactly at the vehicle, not at the nearest corner`() {
        // Kendaraan di tengah ruas pertama. Versi lama memotong di titik sudut terdekat (start),
        // jadi ~550 m garis di belakang kendaraan tetap terlihat.
        val vehicle = RoutePoint(-7.2850, 112.7500)

        val result = RouteGeometry.trim(lRoute, vehicle)!!

        assertEquals(vehicle.lat, result.remaining.first().lat, 1e-9)
        assertEquals(vehicle.lng, result.remaining.first().lng, 1e-9)
        assertEquals(listOf(corner, end), result.remaining.drop(1))
        assertTrue(result.distanceFromRouteMeters < 0.5)
    }

    @Test
    fun `trim projects a vehicle slightly off the road back onto the route`() {
        // ~11 m di sebelah timur jalan (noise GPS biasa).
        val vehicle = RoutePoint(-7.2850, 112.7501)

        val result = RouteGeometry.trim(lRoute, vehicle)!!

        assertEquals(112.7500, result.remaining.first().lng, 1e-9)
        assertEquals(-7.2850, result.remaining.first().lat, 1e-6)
        assertEquals(11.0, result.distanceFromRouteMeters, 1.0)
    }

    @Test
    fun `trim on the second leg drops the whole first leg`() {
        val vehicle = RoutePoint(-7.2800, 112.7550)

        val result = RouteGeometry.trim(lRoute, vehicle)!!

        assertEquals(2, result.remaining.size)
        assertEquals(112.7550, result.remaining.first().lng, 1e-9)
        assertEquals(end, result.remaining.last())
    }

    @Test
    fun `trim reports a large distance when the vehicle leaves the route`() {
        val farAway = RoutePoint(-7.2850, 112.7600) // ~550 m dari jalur terdekat

        val result = RouteGeometry.trim(lRoute, farAway)!!

        assertTrue(result.distanceFromRouteMeters > 500)
    }

    @Test
    fun `trim keeps at least two points when the vehicle reaches the destination`() {
        val result = RouteGeometry.trim(lRoute, end)!!

        assertTrue(result.remaining.size >= 2)
        assertEquals(end, result.remaining.last())
    }

    @Test
    fun `trim returns null for a route with fewer than two points`() {
        assertNull(RouteGeometry.trim(listOf(start), start))
        assertNull(RouteGeometry.trim(emptyList(), start))
    }

    @Test
    fun `lengthMeters sums every segment`() {
        // 0,01 derajat lintang ~ 1.112 m; 0,01 derajat bujur di lintang -7,28 ~ 1.103 m.
        assertEquals(2_215.0, RouteGeometry.lengthMeters(lRoute), 10.0)
        assertEquals(0.0, RouteGeometry.lengthMeters(listOf(start)), 0.0)
    }

    @Test
    fun `bearingDegrees points north and east correctly`() {
        assertEquals(0f, RouteGeometry.bearingDegrees(start, corner), 0.5f)
        assertEquals(90f, RouteGeometry.bearingDegrees(corner, end), 0.5f)
        assertEquals(270f, RouteGeometry.bearingDegrees(end, corner), 0.5f)
    }

    @Test
    fun `shortestRotationDelta always turns the short way round`() {
        assertEquals(20f, RouteGeometry.shortestRotationDelta(350f, 10f), 0.001f)
        assertEquals(-20f, RouteGeometry.shortestRotationDelta(10f, 350f), 0.001f)
        assertEquals(90f, RouteGeometry.shortestRotationDelta(0f, 90f), 0.001f)
        assertEquals(0f, RouteGeometry.shortestRotationDelta(45f, 45f), 0.001f)
    }
}
