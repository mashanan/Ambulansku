package com.example.tugasakhirfinal.ForAdmin.Welcome

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.DeviceSession
import com.example.tugasakhirfinal.ForAdmin.AdminHomeActivity
import com.example.tugasakhirfinal.databinding.ActivitySignInAdminBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

// Tidak ada AdminSignUpActivity dengan sengaja — akun admin dibuat manual lewat Firebase Console
// supaya role ini eksklusif (dipagari Firestore Security Rules, bukan cuma disembunyikan di UI).
class AdminSignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInAdminBinding
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var progressDialog: ProgressDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignInAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        progressDialog = ProgressDialog(this).apply {
            setMessage("Verifying Admin account...")
            setCancelable(false)
        }

        binding.signinbutton.setOnClickListener {
            val email = binding.emailEt.text.toString().trim()
            val pass = binding.passET.text.toString().trim()

            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Email and password are required!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressDialog.show()
            firebaseAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    checkAdminRole()
                } else {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkAdminRole() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { document ->
                progressDialog.dismiss()
                val role = document?.getString("role")
                if (document != null && document.exists() && role.equals("admin", ignoreCase = true)) {
                    // Klaim sesi device ini - device lain yang masih login akan otomatis
                    // ter-logout lewat listener di AdminHomeActivity.
                    firestore.collection("users").document(uid)
                        .update("activeSessionId", DeviceSession.getSessionId(this))
                    val intent = Intent(this, AdminHomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this, "This account is not an Admin.", Toast.LENGTH_LONG).show()
                    firebaseAuth.signOut()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Failed to verify: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
