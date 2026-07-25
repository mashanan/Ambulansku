package com.example.tugasakhirfinal.ForPassanger.EditProfileFragment

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

        // Ambil data nama dari intent
        val currentName = intent.getStringExtra("current_name")
        Log.d("ActivityEditNama", "Nama saat ini: $currentName")
        editTextName.setText(currentName)

        // 🚩 LOGIKA TOMBOL BACK
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed() // Cara modern untuk kembali
            // atau cukup gunakan finish()
        }

        // Logika Tombol Save
        saveButton.setOnClickListener {
            val newName = editTextName.text.toString().trim()

            if (newName.isEmpty()) {
                Toast.makeText(this, "Nama tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = firebaseAuth.currentUser?.uid
            if (userId == null) {
                Toast.makeText(this, "User belum login", Toast.LENGTH_SHORT).show()
                Log.e("ActivityEditNama", "userId null")
                return@setOnClickListener
            }

            val userData = mapOf("nama" to newName)

            // Menggunakan .set dengan SetOptions.merge() adalah langkah yang sangat tepat
            // untuk menangani dokumen yang mungkin terhapus atau belum ada.
            firestore.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(this, "Nama berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    Log.d("ActivityEditNama", "Nama tersimpan di Firestore")

                    val resultIntent = intent.apply {
                        putExtra("new_name", newName)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish() // Menutup activity dan kembali ke ProfileFragment
                }
                .addOnFailureListener { e ->
                    Log.e("ActivityEditNama", "Gagal simpan nama", e)
                    // Jika muncul PERMISSION_DENIED, cek Rules Firestore di Console
                    Toast.makeText(this, "Gagal simpan nama: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}