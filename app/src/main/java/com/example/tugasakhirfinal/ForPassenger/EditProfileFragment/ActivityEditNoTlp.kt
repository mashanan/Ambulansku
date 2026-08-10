package com.example.tugasakhirfinal.ForPassenger.EditProfileFragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions // Untuk operasi merge saat update

class ActivityEditNoTlp : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_no_tlp)

        // --- INISIALISASI FIREBASE ---
        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        // -----------------------------

        val editTextNoTlp = findViewById<EditText>(R.id.editTextNoTlp)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        // Mengambil nomor telepon lama yang dikirim dari ProfileFragment. Field SENGAJA
        // dikosongkan (bukan di-setText) dan nilai saat ini ditaruh sebagai hint - supaya user
        // bisa langsung ketik nomor baru tanpa perlu hapus teks lama dulu. Placeholder default
        // dari sign up ("Masukkan Nomor Telepon Anda" untuk passenger, "Belum diisi" untuk driver
        // - ActivityEditNoTlp ini dipakai kedua role) juga otomatis jadi hint biasa.
        val currentNoTlp = intent.getStringExtra("current_no_tlp") ?: ""
        val isPlaceholderNoTlp = currentNoTlp.isBlank() || currentNoTlp == "Enter Your Phone Number" || currentNoTlp == "Belum diisi"
        editTextNoTlp.hint = if (isPlaceholderNoTlp) "Enter Your Phone Number" else currentNoTlp

        // 🚩 LOGIKA TOMBOL BACK
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Cara modern untuk kembali
            // atau cukup gunakan finish()
        }
        saveButton.setOnClickListener {
            val typedNoTlp = editTextNoTlp.text.toString().trim()
            // Dibiarkan kosong = user tidak mau mengubah nomor - pakai nilai lama (kalau memang
            // sudah nomor asli, bukan placeholder) supaya tidak perlu ketik ulang cuma untuk
            // menutup layar ini.
            val newNoTlp = typedNoTlp.ifEmpty { if (!isPlaceholderNoTlp) currentNoTlp else "" }

            // 1. Validasi Input
            if (newNoTlp.isEmpty() || !newNoTlp.matches(Regex("^[0-9]+$"))) {
                Toast.makeText(this, "Invalid phone number!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = firebaseAuth.currentUser?.uid
            if (userId == null) {
                Toast.makeText(this, "User is not logged in.", Toast.LENGTH_SHORT).show()
                Log.e("ActivityEditNoTlp", "userId null")
                return@setOnClickListener
            }

            // Data yang akan diupdate ke Firestore
            val userData = mapOf("phoneNumber" to newNoTlp)

            // 2. Lakukan Update ke Firestore
            firestore.collection("users").document(userId)
                .set(userData, SetOptions.merge()) // Menggunakan merge agar tidak menimpa field lain
                .addOnSuccessListener {
                    // --- BERHASIL UPDATE DI FIREBASE ---

                    // 3. Kirim hasil kembali ke ProfileFragment
                    val resultIntent = Intent().apply {
                        putExtra("new_no_tlp", newNoTlp)
                    }
                    setResult(RESULT_OK, resultIntent)

                    // 4. Tampilkan Toast dan tutup Activity
                    Toast.makeText(this, "Phone number updated successfully!", Toast.LENGTH_SHORT).show()
                    Log.d("ActivityEditNoTlp", "New phone number saved to Firestore and returned successfully.")
                    finish()
                }
                .addOnFailureListener { e ->
                    // 5. Penanganan Kegagalan
                    Log.e("ActivityEditNoTlp", "Failed to save phone number", e)
                    Toast.makeText(this, "Failed to save phone number. Check your connection or permissions.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}