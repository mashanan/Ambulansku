package com.example.tugasakhirfinal.ForAdmin

// Data ringan khusus tampilan daftar di ManageUsersActivity — bukan model Firestore lengkap.
data class AdminUserItem(
    val uid: String = "",
    val nama: String = "",
    val email: String = "",
    val role: String = "",
    val isActive: Boolean = true
)
