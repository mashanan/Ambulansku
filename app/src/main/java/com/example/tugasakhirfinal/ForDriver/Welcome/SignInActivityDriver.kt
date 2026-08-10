package com.example.tugasakhirfinal.ForDriver.Welcome

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.DeviceSession
import com.example.tugasakhirfinal.ForDriver.DriverHomeActivity
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

        progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Connecting to server...")
        progressDialog.setCancelable(false)

        // Cek jika driver sudah login sebelumnya (mis. baru saja daftar & verifikasi email lalu
        // kembali ke app) - kalau email sudah terverifikasi, langsung cek role/status tanpa perlu
        // login ulang. Kalau belum, minta verifikasi dulu, sama seperti sisi Passenger.
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            if (currentUser.isEmailVerified) {
                progressDialog.setMessage("Verifying Driver Role...")
                checkUserRole()
            } else {
                Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_SHORT).show()
                showResendVerificationButton()
            }
        }

        // Navigasi ke SignUpActivityDriver jika driver belum punya akun
        binding.textView.setOnClickListener {
            val intent = Intent(this, SignUpActivityDriver::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
        }

        binding.signinbutton.setOnClickListener {
            val email = binding.emailEt.text.toString().trim()
            val pass = binding.passET.text.toString().trim()

            if (validateInput(email, pass)) {
                loginProcess(email, pass)
            }
        }

        // Event listener untuk tombol kirim ulang email verifikasi
        binding.btnResendVerification.setOnClickListener {
            val user = firebaseAuth.currentUser
            user?.sendEmailVerification()?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Verification email resent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to send verification email.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Validasi input email dan password, sama seperti sisi Passenger
    private fun validateInput(email: String, password: String): Boolean {
        return when {
            email.isEmpty() -> {
                Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show()
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                false
            }
            password.isEmpty() -> {
                Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show()
                false
            }
            password.length < 6 -> {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }

    private fun loginProcess(email: String, pass: String) {
        binding.signinbutton.isEnabled = false
        progressDialog.setMessage("Connecting to server...")
        progressDialog.show()

        firebaseAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = firebaseAuth.currentUser
                if (user?.isEmailVerified == true) {
                    progressDialog.setMessage("Verifying Driver Role...")
                    checkUserRole()
                } else {
                    progressDialog.dismiss()
                    binding.signinbutton.isEnabled = true
                    Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_SHORT).show()
                    showResendVerificationButton()
                }
            } else {
                progressDialog.dismiss()
                binding.signinbutton.isEnabled = true
                handleLoginError(task.exception?.message)
            }
        }
    }

    // Pesan error yang lebih ramah dibanding menampilkan mentah exception Firebase, sama seperti sisi Passenger
    private fun handleLoginError(errorMessage: String?) {
        val displayMessage = when {
            errorMessage == "The password is invalid or the user does not have a password." ||
                errorMessage == "There is no user record corresponding to this identifier. The user may have been deleted." ->
                "Incorrect email or password"
            errorMessage?.contains("Failed to get service from broker") == true ->
                "Connection Error: Make sure SHA-1 is registered in Firebase!"
            else -> "Login failed. Please try again."
        }
        Toast.makeText(this, displayMessage, Toast.LENGTH_SHORT).show()
    }

    private fun showResendVerificationButton() {
        binding.btnResendVerification.visibility = View.VISIBLE
    }

    private fun checkUserRole() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        Log.d("LOGIN_DEBUG", "Memulai verifikasi untuk UID: $userId")
        if (!progressDialog.isShowing) progressDialog.show()

        // Menggunakan Source.SERVER untuk memaksa pengambilan data terbaru dari internet
        firestore.collection("users").document(userId).get(Source.SERVER)
            .addOnSuccessListener { document ->
                progressDialog.dismiss()
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    Log.d("LOGIN_DEBUG", "Role yang diterima: $role")

                    if (role.equals("driver", ignoreCase = true)) {
                        val isActive = document.getBoolean("isActive") ?: true
                        val isApproved = document.getBoolean("isApproved") ?: false

                        if (!isActive) {
                            Toast.makeText(this, "Your account has been deactivated by the admin.", Toast.LENGTH_LONG).show()
                            firebaseAuth.signOut()
                            binding.signinbutton.isEnabled = true
                            return@addOnSuccessListener
                        }
                        if (!isApproved) {
                            Toast.makeText(this, "Your account is still pending Admin approval.", Toast.LENGTH_LONG).show()
                            firebaseAuth.signOut()
                            binding.signinbutton.isEnabled = true
                            return@addOnSuccessListener
                        }

                        // Klaim sesi device ini - device lain yang masih login akan otomatis
                        // ter-logout lewat listener di DriverHomeActivity.
                        firestore.collection("users").document(userId)
                            .update("activeSessionId", DeviceSession.getSessionId(this))

                        // Navigasi sukses ke Home Driver
                        val intent = Intent(this, DriverHomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // User login sebagai Passenger di portal Driver
                        Log.w("LOGIN_DEBUG", "Akses ditolak: User adalah passenger")
                        Toast.makeText(this, "Access Denied: Use a Driver account!", Toast.LENGTH_LONG).show()

                        // Penting: Logout agar sesi tidak tertukar saat kembali ke login
                        firebaseAuth.signOut()
                        binding.signinbutton.isEnabled = true
                    }
                } else {
                    Log.e("LOGIN_DEBUG", "Dokumen User tidak ditemukan")
                    Toast.makeText(this, "Driver data not found in database!", Toast.LENGTH_SHORT).show()
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
                    "Connection Error: Make sure SHA-1 is registered in Firebase!"
                } else {
                    "Failed to sync data: ${e.message}"
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
    }
}