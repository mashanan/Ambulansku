package com.example.tugasakhirfinal.Maps

import android.graphics.Color

/**
 * Warna garis rute trip, dipakai bersama oleh pov passenger (ActivityDrive) dan driver
 * (ActivityDriveFragmentDriver) supaya selalu identik.
 *
 * Sengaja TIDAK memakai hijau/oranye/merah: warna-warna itu dipakai traffic layer Google Maps
 * (lancar/padat/macet), jadi rute jemput yang dulu merah gampang tertukar dengan jalan macet.
 */
object RouteStyle {
    /** Etape jemput (ambulans menuju pasien). */
    val PICKUP_COLOR: Int = Color.parseColor("#7B1FA2")

    /** Etape antar (ambulans menuju rumah sakit). */
    val TO_HOSPITAL_COLOR: Int = Color.parseColor("#1E88E5")

    /** @param mode "jemput" atau "antar", sesuai yang dipakai di kedua layar trip. */
    fun colorFor(mode: String): Int = if (mode == "jemput") PICKUP_COLOR else TO_HOSPITAL_COLOR
}
