package com.example.tugasakhirfinal.ForAdmin

// Data ringan khusus tampilan ApproveDriverActivity — bukan model Firestore lengkap.
data class PendingDriverItem(
    val uid: String = "",
    val nama: String = "",
    val email: String = "",
    val licenseNumber: String = "",
    val ambulancePlateNumber: String = "",
    val licensePhotoUrl: String = "",
    val ambulancePhotoUrl: String = ""
)
