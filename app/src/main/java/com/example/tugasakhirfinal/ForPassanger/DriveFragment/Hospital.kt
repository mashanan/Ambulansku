package com.example.tugasakhirfinal.ForPassanger

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.PropertyName

data class Hospital(
    var id: String = "",

    @get:PropertyName("nama") @set:PropertyName("nama")
    var nama: String = "",

    @get:PropertyName("alamat") @set:PropertyName("alamat")
    var alamat: String = "",

    @get:PropertyName("telepon") @set:PropertyName("telepon")
    var telepon: String = "",

    @get:PropertyName("igdStatus") @set:PropertyName("igdStatus")
    var igdStatus: String = "Tersedia",

    @get:PropertyName("latitude") @set:PropertyName("latitude")
    var latitude: Double = 0.0,

    @get:PropertyName("longitude") @set:PropertyName("longitude")
    var longitude: Double = 0.0,

    @get:PropertyName("lokasi") @set:PropertyName("lokasi")
    var lokasi: GeoPoint? = null,

    @get:PropertyName("imageUrl") @set:PropertyName("imageUrl")
    var imageUrl: String = ""
) {
    // Helper function untuk mengambil LatLng ke Google Maps (Aman dari GeoPoint & Double)
    fun getLatLng(): LatLng? {
        return if (lokasi != null) {
            LatLng(lokasi!!.latitude, lokasi!!.longitude)
        } else if (latitude != 0.0 && longitude != 0.0) {
            LatLng(latitude, longitude)
        } else {
            null
        }
    }
}