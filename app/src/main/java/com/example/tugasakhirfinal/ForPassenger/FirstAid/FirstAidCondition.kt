package com.example.tugasakhirfinal.ForPassenger.FirstAid

// Data referensi statis (bukan dari Firestore) - daftar kondisi gawat darurat yang umum
// ditemukan di IGD beserta langkah pertolongan pertama sebelum ambulans/tenaga medis tiba.
data class FirstAidCondition(
    val id: String = "",
    val title: String,
    val symptoms: String,
    val steps: List<String>,
    var isExpanded: Boolean = false
)
