package com.example.tugasakhirfinal.ForPassenger.Welcome

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.ForPassenger.PassengerHomeActivity
import com.example.tugasakhirfinal.databinding.ActivitySignInPassengerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInPassengerBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInPassengerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()

        // Cek jika user sudah login, langsung ke HomeActivityS
        if (firebaseAuth.currentUser != null) {
            val user = firebaseAuth.currentUser
            // Cek apakah email sudah diverifikasi
            if (user?.isEmailVerified == true) {
                checkActiveThenNavigate()
            } else {
                Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            }
        }

        // Navigasi ke SignUpActivity jika user belum punya akun
        binding.textView.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        // Event untuk tombol Sign In
        binding.signinbutton.setOnClickListener {
            val email = binding.emailEt.text.toString().trim()
            val pass = binding.passET.text.toString().trim()

            // Validasi input email dan password
            if (validateInput(email, pass)) {
                firebaseAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener {
                    if (it.isSuccessful) {
                        val user = firebaseAuth.currentUser
                        if (user?.isEmailVerified == true) {
                            Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                            checkActiveThenNavigate()
                        } else {
                            Toast.makeText(this, "Please verify your email before logging in.", Toast.LENGTH_SHORT).show()
                            showResendVerificationButton()
                        }
                    } else {
                        handleLoginError(it.exception?.message)
                    }
                }
            }
        }

        // Event listener untuk tombol kirim ulang email verifikasi
        binding.btnResendVerification.setOnClickListener {
            val user = firebaseAuth.currentUser
            user?.sendEmailVerification()?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Verification email sent again!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to send verification email.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Menghilangkan Action Bar
        supportActionBar?.hide()
    }

    // Fungsi untuk validasi input email dan password
    private fun validateInput(email: String, password: String): Boolean {
        return when {
            email.isEmpty() -> {
                Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show()
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                false
            }
            password.isEmpty() -> {
                Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show()
                false
            }
            password.length < 6 -> {
                Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    // Fungsi untuk menangani error login dan menampilkan pesan yang lebih user-friendly
    private fun handleLoginError(errorMessage: String?) {
        val displayMessage = when (errorMessage) {
            "The password is invalid or the user does not have a password." -> "Email atau password salah"
            "There is no user record corresponding to this identifier. The user may have been deleted." -> "Email atau password salah"
            else -> "Login gagal. Silakan coba lagi."
        }
        Toast.makeText(this, displayMessage, Toast.LENGTH_SHORT).show()
    }

    // Fungsi untuk menampilkan tombol kirim ulang email verifikasi
    private fun showResendVerificationButton() {
        binding.btnResendVerification.visibility = View.VISIBLE
    }

    // Cek field isActive di Firestore sebelum masuk Home — kalau akun dinonaktifkan Admin,
    // tolak masuk & sign out, jangan cuma andalkan status login Firebase Auth.
    private fun checkActiveThenNavigate() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { doc ->
                val isActive = doc.getBoolean("isActive") ?: true
                if (isActive) {
                    navigateToHomeActivity()
                } else {
                    Toast.makeText(this, "Akun Anda dinonaktifkan oleh admin.", Toast.LENGTH_LONG).show()
                    firebaseAuth.signOut()
                }
            }
            .addOnFailureListener {
                // Gagal cek (mis. offline) - tetap izinkan masuk, jangan blokir karena masalah jaringan.
                navigateToHomeActivity()
            }
    }

    // Fungsi untuk navigasi ke HomeActivity
    private fun navigateToHomeActivity() {
        val intent = Intent(this, PassengerHomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Fungsi untuk navigasi ke MainActivity jika email belum diverifikasi
    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
