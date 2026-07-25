package com.example.tugasakhirfinal

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.ForDriver.DriverHomeActivity
import com.example.tugasakhirfinal.ForPassanger.PassangerHomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

@SuppressLint("CustomSplashScreen")
class SplashScreenActivity : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        supportActionBar?.hide()
        firebaseAuth = FirebaseAuth.getInstance()

        // LANGKAH 1: Cek apakah ada user yang sudah login
        val currentUser = firebaseAuth.currentUser

        if (currentUser != null) {
            // LANGKAH 2: Jika login, TAHAN di sini sampai data Role keluar dari server
            checkRoleAndNavigate(currentUser.uid)
        } else {
            // LANGKAH 3: Jika belum login, baru ke pemilihan role
            goToRoleSelection()
        }
    }

    private fun checkRoleAndNavigate(uid: String) {
        // Kita gunakan Source.SERVER agar aplikasi TIDAK mengambil data lama dari cache HP
        firestore.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    Log.d("SPLASH_DONE", "Role terdeteksi: $role")

                    val intent = if (role.equals("driver", ignoreCase = true)) {
                        Intent(this, DriverHomeActivity::class.java)
                    } else {
                        Intent(this, PassangerHomeActivity::class.java)
                    }

                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    // Jika dokumen user tidak ada (misal dihapus di Firestore tapi login masih aktif)
                    firebaseAuth.signOut()
                    goToRoleSelection()
                }
            }
            .addOnFailureListener { e ->
                Log.e("SPLASH_ERROR", "Gagal ambil data: ${e.message}")
                // Jika koneksi internet error/gagal, kita tidak pindah halaman dulu agar tidak salah role
                // Atau bisa arahkan kembali ke login
                firebaseAuth.signOut()
                goToRoleSelection()
            }
    }

    private fun goToRoleSelection() {
        val intent = Intent(this, RoleSelectionActivity::class.java)
        startActivity(intent)
        finish()
    }
}