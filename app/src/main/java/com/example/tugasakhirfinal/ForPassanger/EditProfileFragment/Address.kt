package com.example.tugasakhirfinal.ForPassanger.EditProfileFragment

data class Address(
    var id: String = "",
    val label: String = "",
    val fullAddress: String = "",
    val note: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val createdAt: Long = 0,
    @field:JvmField
    var isFavorite: Boolean = false, // untuk alamat favorit
    @field:JvmField
    var isPrimary: Boolean = false // untuk menandai Alamat Utama
)