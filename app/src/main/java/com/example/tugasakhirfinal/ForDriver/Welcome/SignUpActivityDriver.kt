package com.example.tugasakhirfinal.ForDriver

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.ForDriver.Welcome.DriverQualificationActivity
import com.example.tugasakhirfinal.ForDriver.Welcome.SignInActivityDriver
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
            val intent = Intent(this, SignInActivityDriver::class.java)
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
                                // isApproved=false: driver belum bisa mengemudi sampai data kualifikasinya
                                // (SIM, ambulans) diperiksa & disetujui Admin. isActive hanya boleh diubah Admin.
                                val driverMap = hashMapOf(
                                    "uid" to userId,
                                    "email" to email,
                                    "nama" to "Driver Baru", // Default
                                    "phoneNumber" to "Belum diisi", // Default
                                    "role" to "driver", // 🚩 SANGAT PENTING
                                    "status" to "offline",
                                    "profileImageUrl" to "",
                                    "isActive" to true,
                                    "isApproved" to false
                                )

                                firestore.collection("users").document(userId).set(driverMap)
                                    .addOnSuccessListener {
                                        // 3. Lanjut ke form kualifikasi (SIM, plat ambulans, foto dokumen)
                                        // sebelum kirim email verifikasi & sign out.
                                        val intent = Intent(this, DriverQualificationActivity::class.java)
                                        startActivity(intent)
                                        finish()
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