package com.example.tugasakhirfinal.ForPassenger.EditProfileFragment

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView // Tambahkan import ini
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class ActivityEditNama : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_nama)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Inisialisasi View
        val editTextName = findViewById<EditText>(R.id.editTextName)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val btnBack = findViewById<ImageView>(R.id.btnBack) // Inisialisasi tombol back

        // Ambil data nama dari intent. Field SENGAJA dikosongkan (bukan di-setText) dan nilai
        // saat ini ditaruh sebagai hint - supaya user bisa langsung ketik nama baru tanpa perlu
        // hapus teks lama dulu. Placeholder default dari sign up ("Masukkan Nama Anda" untuk
        // passenger, "Driver Baru" untuk driver - ActivityEditNama ini dipakai kedua role) juga
        // otomatis jadi hint instruksional biasa, bukan teks yang harus dihapus manual.
        val currentName = intent.getStringExtra("current_name") ?: ""
        Log.d("ActivityEditNama", "Current name: $currentName")
        val isPlaceholderName = currentName.isBlank() || currentName == "Enter Your Name" || currentName == "Driver Baru"
        editTextName.hint = if (isPlaceholderName) "Enter Your Name" else currentName

        // 🚩 LOGIKA TOMBOL BACK
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Cara modern untuk kembali
            // atau cukup gunakan finish()
        }

        // Logika Tombol Save
        saveButton.setOnClickListener {
            val typedName = editTextName.text.toString().trim()
            // Dibiarkan kosong = user tidak mau mengubah nama - pakai nilai lama (kalau memang
            // sudah nama asli, bukan placeholder) supaya tidak perlu ketik ulang cuma untuk
            // menutup layar ini.
            val newName = typedName.ifEmpty { if (!isPlaceholderName) currentName else "" }

            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = firebaseAuth.currentUser?.uid
            if (userId == null) {
                Toast.makeText(this, "User is not logged in", Toast.LENGTH_SHORT).show()
                Log.e("ActivityEditNama", "userId null")
                return@setOnClickListener
            }

            val userData = mapOf("nama" to newName)

            // Menggunakan .set dengan SetOptions.merge() adalah langkah yang sangat tepat
            // untuk menangani dokumen yang mungkin terhapus atau belum ada.
            firestore.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(this, "Name updated successfully!", Toast.LENGTH_SHORT).show()
                    Log.d("ActivityEditNama", "Name saved to Firestore")

                    val resultIntent = intent.apply {
                        putExtra("new_name", newName)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish() // Menutup activity dan kembali ke ProfileFragment
                }
                .addOnFailureListener { e ->
                    Log.e("ActivityEditNama", "Failed to save name", e)
                    // Jika muncul PERMISSION_DENIED, cek Rules Firestore di Console
                    Toast.makeText(this, "Failed to save name: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}