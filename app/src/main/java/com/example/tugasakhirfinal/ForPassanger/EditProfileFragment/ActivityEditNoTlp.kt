package com.example.tugasakhirfinal.ForPassanger.EditProfileFragment

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

        // Mengambil nomor telepon lama yang dikirim dari ProfileFragment
        val currentNoTlp = intent.getStringExtra("current_no_tlp")
        editTextNoTlp.setText(currentNoTlp)

        // 🚩 LOGIKA TOMBOL BACK
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Cara modern untuk kembali
            // atau cukup gunakan finish()
        }
        saveButton.setOnClickListener {
            val newNoTlp = editTextNoTlp.text.toString().trim()

            // 1. Validasi Input
            if (newNoTlp.isEmpty() || !newNoTlp.matches(Regex("^[0-9]+$"))) {
                Toast.makeText(this, "Nomor telepon tidak valid!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = firebaseAuth.currentUser?.uid
            if (userId == null) {
                Toast.makeText(this, "User belum login.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Nomor telepon berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    Log.d("ActivityEditNoTlp", "Nomor telepon baru berhasil disimpan di Firestore dan dikirim kembali.")
                    finish()
                }
                .addOnFailureListener { e ->
                    // 5. Penanganan Kegagalan
                    Log.e("ActivityEditNoTlp", "Gagal simpan nomor telepon", e)
                    Toast.makeText(this, "Gagal menyimpan nomor telepon. Cek koneksi atau izin.", Toast.LENGTH_SHORT).show()
                }
        }
    }
}