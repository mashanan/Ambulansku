package com.example.tugasakhirfinal.ForPassanger.Welcome

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.ForDriver.DriverHomeActivity
import com.example.tugasakhirfinal.ForPassanger.PassangerHomeActivity
import com.example.tugasakhirfinal.databinding.ActivitySignInDriverBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class SignInActivityDriver : AppCompatActivity() {

    private lateinit var binding: ActivitySignInDriverBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        supportActionBar?.hide()

        // Inisialisasi ProgressDialog yang lebih informatif
        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Menghubungkan ke server...")
        progressDialog.setCancelable(false)

        binding.signinbutton.setOnClickListener {
            val email = binding.emailEt.text.toString().trim()
            val pass = binding.passET.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty()) {
                loginProcess(email, pass)
            } else {
                Toast.makeText(this, "Email dan Password harus diisi!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loginProcess(email: String, pass: String) {
        binding.signinbutton.isEnabled = false
        progressDialog.show()

        firebaseAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Jangan dismiss dialog di sini, tunggu role terverifikasi
                progressDialog.setMessage("Memverifikasi Role Driver...")
                checkUserRole()
            } else {
                progressDialog.dismiss()
                binding.signinbutton.isEnabled = true
                Toast.makeText(this, "Login Gagal: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkUserRole() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        Log.d("LOGIN_DEBUG", "Memulai verifikasi untuk UID: $userId")

        // Menggunakan Source.SERVER untuk memaksa pengambilan data terbaru dari internet
        firestore.collection("users").document(userId).get(Source.SERVER)
            .addOnSuccessListener { document ->
                progressDialog.dismiss()
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    Log.d("LOGIN_DEBUG", "Role yang diterima: $role")

                    if (role.equals("driver", ignoreCase = true)) {
                        // Navigasi sukses ke Home Driver
                        val intent = Intent(this, DriverHomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // User login sebagai Passenger di portal Driver
                        Log.w("LOGIN_DEBUG", "Akses ditolak: User adalah passenger")
                        Toast.makeText(this, "Akses Ditolak: Gunakan akun khusus Driver!", Toast.LENGTH_LONG).show()

                        // Penting: Logout agar sesi tidak tertukar saat kembali ke login
                        firebaseAuth.signOut()
                        binding.signinbutton.isEnabled = true
                    }
                } else {
                    Log.e("LOGIN_DEBUG", "Dokumen User tidak ditemukan")
                    Toast.makeText(this, "Data Driver tidak ditemukan di database!", Toast.LENGTH_SHORT).show()
                    firebaseAuth.signOut()
                    binding.signinbutton.isEnabled = true
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                binding.signinbutton.isEnabled = true
                Log.e("LOGIN_DEBUG", "Firestore Error: ${e.message}")

                // Memberikan saran jika error terkait koneksi/SHA-1
                val errorMsg = if (e.message?.contains("Failed to get service from broker") == true) {
                    "Error Koneksi: Pastikan SHA-1 sudah terdaftar di Firebase!"
                } else {
                    "Gagal sinkronisasi data: ${e.message}"
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
    }
}