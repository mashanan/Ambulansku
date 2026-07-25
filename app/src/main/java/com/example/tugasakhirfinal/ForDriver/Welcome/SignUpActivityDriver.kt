package com.example.tugasakhirfinal.ForDriver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.ForPassenger.Welcome.SignInActivity
import com.example.tugasakhirfinal.databinding.ActivitySignUpDriverBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignUpActivityDriver : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpDriverBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        supportActionBar?.hide()

        // Navigasi ke Sign In
        binding.textView.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
        }

        binding.signupbutton.setOnClickListener {
            val email = binding.emailEt.text.toString().trim()
            val pass = binding.passET.text.toString().trim()
            val confirmPass = binding.confirmPassEt.text.toString().trim()

            if (email.isNotEmpty() && pass.isNotEmpty() && confirmPass.isNotEmpty()) {
                if (pass.length < 6) {
                    Toast.makeText(this, "Password minimal 6 karakter", Toast.LENGTH_SHORT).show()
                } else if (pass != confirmPass) {
                    Toast.makeText(this, "Password tidak cocok", Toast.LENGTH_SHORT).show()
                } else {
                    // 1. Buat Akun di Auth
                    firebaseAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = task.result?.user
                            val userId = user?.uid

                            if (userId != null) {
                                // 2. Buat Dokumen di Firestore dengan ROLE DRIVER
                                val driverMap = hashMapOf(
                                    "uid" to userId,
                                    "email" to email,
                                    "nama" to "Driver Baru", // Default
                                    "phoneNumber" to "Belum diisi", // Default
                                    "role" to "driver", // 🚩 SANGAT PENTING
                                    "status" to "offline",
                                    "profileImageUrl" to ""
                                )

                                firestore.collection("users").document(userId).set(driverMap)
                                    .addOnSuccessListener {
                                        // 3. Kirim Email Verifikasi
                                        user.sendEmailVerification().addOnCompleteListener { emailTask ->
                                            if (emailTask.isSuccessful) {
                                                Toast.makeText(this, "Registrasi Driver Berhasil! Cek email verifikasi.", Toast.LENGTH_LONG).show()
                                                firebaseAuth.signOut()

                                                val intent = Intent(this, SignInActivity::class.java)
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                startActivity(intent)
                                                finish()
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Gagal simpan data: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        } else {
                            Toast.makeText(this, task.exception?.message ?: "Gagal Daftar", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Semua kolom wajib diisi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}