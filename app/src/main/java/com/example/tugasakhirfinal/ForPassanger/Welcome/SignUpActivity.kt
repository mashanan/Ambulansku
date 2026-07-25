package com.example.tugasakhirfinal.ForPassanger.Welcome

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.databinding.ActivitySignUpPassangerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore // 🚩 Tambahkan Import Firestore

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpPassangerBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance() // 🚩 Inisialisasi Firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySignUpPassangerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        supportActionBar?.hide()

        binding.textView.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        binding.signupbutton.setOnClickListener {
            val email = binding.emailEt.text.toString().trim()
            val pass = binding.passET.text.toString().trim()
            val confirmPass = binding.confirmPassEt.text.toString().trim()

            // Ambil role dari Intent (dikirim dari RoleSelectionActivity)
            val selectedRole = intent.getStringExtra("USER_ROLE") ?: "passenger"

            if (email.isNotEmpty() && pass.isNotEmpty() && confirmPass.isNotEmpty()) {
                if (pass.length < 6) {
                    Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
                } else if (pass != confirmPass) {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                } else {
                    // 1. Proses Pembuatan Akun di Firebase Auth
                    firebaseAuth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = task.result?.user
                            val userId = user?.uid

                            if (userId != null) {
                                // 2. Buat Dokumen User di Firestore agar database tidak kosong
                                val userMap = hashMapOf(
                                    "uid" to userId,
                                    "email" to email,
                                    "nama" to "Masukkan Nama Anda",
                                    "phoneNumber" to "Masukkan Nomor Telepon Anda",
                                    "role" to selectedRole, // Menyimpan role yang dipilih di awal
                                    "status" to "offline", // Default untuk driver (tidak masalah jika untuk passenger)
                                    "profileImageUrl" to ""
                                )

                                firestore.collection("users").document(userId).set(userMap)
                                    .addOnSuccessListener {
                                        // 3. Kirim Email Verifikasi setelah Data Firestore Terbuat
                                        user.sendEmailVerification().addOnCompleteListener { emailTask ->
                                            if (emailTask.isSuccessful) {
                                                Toast.makeText(this, "Account created! Verification email sent.", Toast.LENGTH_LONG).show()

                                                firebaseAuth.signOut()

                                                val intent = Intent(this, SignInActivity::class.java)
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                startActivity(intent)
                                                finish()
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Failed to create user data: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        } else {
                            Toast.makeText(this, task.exception?.message ?: "Registration Failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Empty fields are not allowed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}