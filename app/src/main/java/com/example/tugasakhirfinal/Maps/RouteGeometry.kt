package com.example.tugasakhirfinal.Maps

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Titik koordinat sederhana (pure Kotlin, tanpa class Android/Google Maps) supaya semua
 * perhitungan geometri rute di file ini bisa di-unit test langsung di JVM tanpa emulator.
 * Konversi dari/ke LatLng ada di MapRouteExtensions.kt.
 */
data class RoutePoint(val lat: Double, val lng: Double)

/** Hasil memotong rute di posisi kendaraan saat ini. */
data class RouteTrimResult(
    /** Sisa rute yang belum dilewati, diawali titik proyeksi posisi kendaraan di atas jalur. */
    val remaining: List<RoutePoint>,
    /** Jarak tegak lurus kendaraan ke jalur (meter) - besar berarti kendaraan keluar jalur. */
    val distanceFromRouteMeters: Double
)

object RouteGeometry {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /**
     * Berapa ruas jalur ke depan yang diperiksa saat mencari posisi kendaraan. Dibatasi supaya
     * kalau rute melewati jalan yang sama dua kali (putar balik, flyover), posisi kendaraan
     * tidak "melompat" ke bagian rute yang jauh di depan dan memotong jalur yang belum dilewati.
     */
    private const val SEARCH_WINDOW_SEGMENTS = 40

    /**
     * Memotong bagian rute yang sudah dilewati.
     *
     * Versi lama memotong di TITIK SUDUT rute terdekat. Karena overview_polyline dari Directions
     * API sudah disederhanakan, titik sudut di jalan lurus bisa berjarak ratusan meter, sehingga
     * potongan garis di belakang ambulans tetap terlihat. Di sini posisi kendaraan diproyeksikan
     * ke RUAS (garis antar dua titik sudut) terdekat, lalu rute dimulai tepat dari titik proyeksi
     * itu - jadi garis selalu terpotong pas di posisi ambulans.
     *
     * @return null kalau rute kurang dari 2 titik (tidak ada yang bisa dipotong).
     */
    fun trim(path: List<RoutePoint>, position: RoutePoint): RouteTrimResult? {
        if (path.size < 2) return null

        val lastSegment = minOf(path.size - 2, SEARCH_WINDOW_SEGMENTS - 1)
        var bestSegment = 0
        var bestT = 0.0
        var bestDistance = Double.MAX_VALUE

        for (i in 0..lastSegment) {
            val (t, distance) = projectOntoSegment(position, path[i], path[i + 1])
            if (distance < bestDistance) {
                bestDistance = distance
                bestSegment = i
                bestT = t
            }
        }

        val a = path[bestSegment]
        val b = path[bestSegment + 1]
        val projected = RoutePoint(
            lat = a.lat + (b.lat - a.lat) * bestT,
            lng = a.lng + (b.lng - a.lng) * bestT
        )

        val remaining = ArrayList<RoutePoint>(path.size - bestSegment)
        remaining.add(projected)
        for (i in bestSegment + 1 until path.size) {
            // Lewati titik yang identik dengan titik proyeksi (kendaraan pas di titik sudut).
            if (i == bestSegment + 1 && path[i] == projected) continue
            remaining.add(path[i])
        }
        if (remaining.size < 2) remaining.add(path.last())

        return RouteTrimResult(remaining, bestDistance)
    }

    /** Panjang total sebuah jalur dalam meter. */
    fun lengthMeters(path: List<RoutePoint>): Double {
        var total = 0.0
        for (i in 0 until path.size - 1) total += distanceMeters(path[i], path[i + 1])
        return total
    }

    /** Jarak great-circle (haversine) antar dua titik dalam meter. */
    fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
    }

    /** Arah hadap dari [from] ke [to] dalam derajat 0..360 (0 = utara, searah jarum jam). */
    fun bearingDegrees(from: RoutePoint, to: RoutePoint): Float {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val degrees = Math.toDegrees(atan2(y, x))
        return ((degrees + 360.0) % 360.0).toFloat()
    }

    /**
     * Selisih sudut terpendek dari [from] ke [to] (hasil -180..180). Dipakai animasi rotasi
     * marker supaya dari 350° ke 10° ikon berputar 20° searah jarum jam, bukan 340° balik arah.
     */
    fun shortestRotationDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    /**
     * Proyeksi titik ke ruas a-b. Untuk jarak sependek ruas jalan, bumi dianggap datar
     * (equirectangular) - galatnya jauh di bawah akurasi GPS dan jauh lebih ringan dihitung
     * tiap 3 detik dibanding rumus bola penuh.
     *
     * @return pasangan (t, jarak meter); t = 0 di a, 1 di b.
     */
    private fun projectOntoSegment(p: RoutePoint, a: RoutePoint, b: RoutePoint): Pair<Double, Double> {
        val metersPerDegLat = PI / 180.0 * EARTH_RADIUS_METERS
        val metersPerDegLng = metersPerDegLat * cos(Math.toRadians(a.lat))

        val bx = (b.lng - a.lng) * metersPerDegLng
        val by = (b.lat - a.lat) * metersPerDegLat
        val px = (p.lng - a.lng) * metersPerDegLng
        val py = (p.lat - a.lat) * metersPerDegLat

        val segmentLengthSq = bx * bx + by * by
        val t = if (segmentLengthSq == 0.0) 0.0 else ((px * bx + py * by) / segmentLengthSq).coerceIn(0.0, 1.0)

        val dx = px - bx * t
        val dy = py - by * t
        return t to sqrt(dx * dx + dy * dy)
    }
}
