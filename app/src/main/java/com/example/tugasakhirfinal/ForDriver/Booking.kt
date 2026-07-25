package com.example.tugasakhirfinal.Model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.GeoPoint

data class Booking(
    var bookingId: String = "",             // Ubah val menjadi var agar ID dokumen Firestore bisa disuntikkan
    var passengerId: String = "",
    var passengerName: String = "",
    var locationName: String = "",
    var hospitalName: String = "",          // Ditambahkan agar sesuai dengan data dari Passenger
    var hospitalLat: Double = 0.0,          // Ditambahkan untuk navigasi ke RS
    var hospitalLng: Double = 0.0,          // Ditambahkan untuk navigasi ke RS
    var pickupLocation: GeoPoint? = null,
    var driverId: String = "",
    var status: String = "SEARCHING",       // Default disamakan menjadi SEARCHING

    // Jarak dari lokasi driver saat ini ke titik jemput, dihitung lokal di client untuk
    // mengurutkan & menampilkan pesanan terdekat. Bukan field Firestore.
    @get:Exclude @set:Exclude
    var distanceMeters: Float = -1f
)