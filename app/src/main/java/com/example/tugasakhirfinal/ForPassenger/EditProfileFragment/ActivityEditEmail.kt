package com.example.tugasakhirfinal.ForPassenger.EditProfileFragment

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore

class ActivityEditEmail : AppCompatActivity() {

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressDialog: ProgressDialog
    private var currentEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_edit_email)

        firebaseAuth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        progressDialog = ProgressDialog(this)
        progressDialog.setTitle("Please Wait")
        progressDialog.setCancelable(false)

        val editTextEmail: EditText = findViewById(R.id.editTextEmail)
        val saveEmailButton: Button = findViewById(R.id.saveEmailButton)
        val resetPasswordButton: Button = findViewById(R.id.resetPasswordButton)

        currentEmail = intent.getStringExtra("current_email") ?: ""
        editTextEmail.setText(currentEmail)
        editTextEmail.isEnabled = false // Email lama dibuat read-only karena input email baru ada di dialog

        // Mengubah teks tombol menjadi "Ganti Email"
        saveEmailButton.text = "Change Email"

        // Klik tombol langsung memunculkan pop-up
        saveEmailButton.setOnClickListener {
            showReAuthDialog()
        }

        resetPasswordButton.setOnClickListener {
            showResetConfirmationDialog()
        }
    }

    private fun showReAuthDialog() {
        val builder = AlertDialog.Builder(this)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reauth_password, null)

        val etOldPass = dialogView.findViewById<TextInputEditText>(R.id.etOldPassword)
        val etNewEmail = dialogView.findViewById<TextInputEditText>(R.id.etNewEmail)
        val etNewPass = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmPass = dialogView.findViewById<TextInputEditText>(R.id.etConfirmNewPassword)

        builder.setView(dialogView)
        builder.setPositiveButton("Continue") { dialog, _ ->
            val oldPass = etOldPass.text.toString().trim()
            val newEmail = etNewEmail.text.toString().trim()
            val newPass = etNewPass.text.toString().trim()
            val confirmPass = etConfirmPass.text.toString().trim()

            // Validasi Password Lama & Email Baru
            if (oldPass.isEmpty()) {
                Toast.makeText(this, "Current password is required", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            if (newEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                Toast.makeText(this, "New email is invalid", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            if (newEmail.equals(currentEmail, ignoreCase = true)) {
                Toast.makeText(this, "New email cannot be the same as the current email", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            // Validasi Password Baru jika diisi
            if (newPass.isNotEmpty()) {
                if (newPass.length < 6) {
                    Toast.makeText(this, "New password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (newPass != confirmPass) {
                    Toast.makeText(this, "Password confirmation does not match", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
            }

            reAuthenticateAndProcess(oldPass, newEmail, newPass)
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { d, _ -> d.cancel() }
        builder.show()
    }

    private fun reAuthenticateAndProcess(oldPass: String, newEmail: String, newPass: String) {
        progressDialog.setMessage("Verifying current password...")
        progressDialog.show()

        val user = firebaseAuth.currentUser
        val credential = EmailAuthProvider.getCredential(currentEmail, oldPass)

        user?.reauthenticate(credential)?.addOnCompleteListener { reAuthTask ->
            if (reAuthTask.isSuccessful) {
                processEmailAndPasswordChange(user, newEmail, newPass)
            } else {
                progressDialog.dismiss()
                Toast.makeText(this, "Current password is incorrect!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processEmailAndPasswordChange(user: FirebaseUser, newEmail: String, newPass: String) {
        // 1. Jika ada password baru, perbarui password terlebih dahulu
        if (newPass.isNotEmpty()) {
            progressDialog.setMessage("Updating password...")
            user.updatePassword(newPass).addOnCompleteListener { passTask ->
                if (passTask.isSuccessful) {
                    sendVerificationToNewEmail(user, newEmail)
                } else {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Failed to change password: ${passTask.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            sendVerificationToNewEmail(user, newEmail)
        }
    }

    private fun sendVerificationToNewEmail(user: FirebaseUser, newEmail: String) {
        progressDialog.setMessage("Sending confirmation to new email...")

        // Menggunakan verifyBeforeUpdateEmail agar perubahan resmi terjadi setelah link diklik oleh user
        user.verifyBeforeUpdateEmail(newEmail).addOnCompleteListener { task ->
            progressDialog.dismiss()
            if (task.isSuccessful) {
                // Update email di Firestore
                firestore.collection("users").document(user.uid).update("email", newEmail)

                Toast.makeText(
                    this,
                    "A confirmation link has been sent to $newEmail. Please check your inbox and verify before logging in again.",
                    Toast.LENGTH_LONG
                ).show()

                startCooldownTimer()
                logoutAndReset()
            } else {
                Toast.makeText(this, "Failed to send confirmation: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this).apply {
            setTitle("Confirm Password Reset")
            setMessage("Are you sure you want to reset your password?")
            setPositiveButton("Yes") { dialog, _ ->
                sendResetPassword()
                dialog.dismiss()
            }
            setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            setCancelable(true)
            show()
        }
    }

    private fun sendResetPassword() {
        if (currentEmail.isNotEmpty()) {
            progressDialog.setMessage("Sending reset email...")
            progressDialog.show()

            firebaseAuth.sendPasswordResetEmail(currentEmail)
                .addOnCompleteListener { task ->
                    progressDialog.dismiss()
                    if (task.isSuccessful) {
                        Toast.makeText(this, "A password reset link has been sent to $currentEmail. Please log in again.", Toast.LENGTH_LONG).show()
                        logoutAndReset()
                    } else {
                        Toast.makeText(this, "Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            Toast.makeText(this, "Email not detected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCooldownTimer() {
        val saveButton: Button = findViewById(R.id.saveEmailButton)
        saveButton.isEnabled = false

        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                saveButton.text = "Wait (${millisUntilFinished / 1000}s)"
            }

            override fun onFinish() {
                saveButton.isEnabled = true
                saveButton.text = "Change Email"
            }
        }.start()
    }

    private fun logoutAndReset() {
        firebaseAuth.signOut()
        val intent = Intent(this, com.example.tugasakhirfinal.ForPassenger.Welcome.SignInActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}