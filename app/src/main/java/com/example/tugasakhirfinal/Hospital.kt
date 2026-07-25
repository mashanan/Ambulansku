package com.example.tugasakhirfinal.Model

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint

data class Hospital(
    val id: String = "",
    val nama: String = "",
    val alamat: String = "",
    val telepon: String = "",
    // Field 'lokasi' tetap menggunakan GeoPoint Firestore agar sinkron dengan database
    val lokasi: GeoPoint? = null
) {
    // 🚩 REVISI: Fungsi bantuan untuk mendapatkan koordinat LatLng Google Maps
    fun getLatLng(): LatLng? {
        return lokasi?.let {
            LatLng(it.latitude, it.longitude)
        }
    }

    // 🚩 Properti tambahan agar kode 'hospital.lat' dan 'hospital.lng' di Activity tidak merah
    val lat: Double? get() = lokasi?.latitude
    val lng: Double? get() = lokasi?.longitude
}