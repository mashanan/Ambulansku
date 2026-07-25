package com.example.tugasakhirfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.ForDriver.DriverHomeActivity
import com.example.tugasakhirfinal.ForDriver.Welcome.MainActivityDriver
import com.example.tugasakhirfinal.ForPassanger.PassangerHomeActivity
import com.example.tugasakhirfinal.ForPassanger.Welcome.MainActivity
import com.example.tugasakhirfinal.databinding.ActivityRoleSelectionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class RoleSelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoleSelectionBinding
    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // 1. Logika Auto-Navigate (Hanya jika belum ditangani oleh SplashScreen)
        // Jika SplashScreen sudah mengecek role, bagian ini sebenarnya bisa dihapus
        // untuk menghindari "double check" yang memperlambat aplikasi.
        if (firebaseAuth.currentUser != null) {
            checkUserRoleAndNavigate()
        }

        // 2. Tombol untuk Peran Penumpang (Passenger)
        binding.passengerButton.setOnClickListener {
            Log.d("ROLE_SELECTION", "Memilih Passenger")
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra("USER_ROLE", "passenger")
            }
            startActivity(intent)
        }

        // 3. Tombol untuk Peran Pengemudi (Driver)
        binding.driverButton.setOnClickListener {
            Log.d("ROLE_SELECTION", "Memilih Driver")
            // Pastikan MainActivityDriver adalah titik masuk login/register untuk Driver
            val intent = Intent(this, MainActivityDriver::class.java).apply {
                putExtra("USER_ROLE", "driver")
            }
            startActivity(intent)
        }
    }

    private fun checkUserRoleAndNavigate() {
        val userId = firebaseAuth.currentUser?.uid ?: return

        // Gunakan Source.SERVER untuk memastikan tidak terjebak cache Passenger/Driver yang lama
        firestore.collection("users").document(userId).get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val userRole = document.getString("role")
                    Log.d("ROLE_SELECTION", "Role terdeteksi: $userRole")

                    val targetActivity = if (userRole.equals("driver", ignoreCase = true)) {
                        DriverHomeActivity::class.java
                    } else {
                        PassangerHomeActivity::class.java
                    }

                    val intent = Intent(this, targetActivity)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ROLE_SELECTION", "Gagal cek role: ${e.message}")
            }
    }
}