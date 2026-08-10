package com.example.tugasakhirfinal

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.ForAdmin.AdminHomeActivity
import com.example.tugasakhirfinal.ForDriver.DriverHomeActivity
import com.example.tugasakhirfinal.ForPassenger.PassengerHomeActivity
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

        // Jeda 3 detik supaya splash screen sempat terlihat (sebelumnya langsung lompat ke
        // halaman berikutnya, kelihatan cuma sekejap/kedip).
        Handler(Looper.getMainLooper()).postDelayed({
            // LANGKAH 1: Cek apakah ada user yang sudah login
            val currentUser = firebaseAuth.currentUser

            if (currentUser != null) {
                // LANGKAH 2: Jika login, TAHAN di sini sampai data Role keluar dari server
                checkRoleAndNavigate(currentUser.uid)
            } else {
                // LANGKAH 3: Jika belum login, baru ke pemilihan role
                goToRoleSelection()
            }
        }, 3000)
    }

    private fun checkRoleAndNavigate(uid: String) {
        // Kita gunakan Source.SERVER agar aplikasi TIDAK mengambil data lama dari cache HP
        firestore.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val role = document.getString("role")
                    Log.d("SPLASH_DONE", "Role terdeteksi: $role")

                    // Admin tidak tunduk pada isActive (dikelola manual lewat Firebase Console),
                    // tapi tetap tunduk pada aturan 1 akun 1 device seperti Passenger/Driver.
                    if (role.equals("admin", ignoreCase = true)) {
                        firestore.collection("users").document(uid)
                            .update("activeSessionId", DeviceSession.getSessionId(this))
                        val intent = Intent(this, AdminHomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                        return@addOnSuccessListener
                    }

                    // Jaring pengaman terakhir sebelum masuk Home - kalau ada sesi yang "nyangkut"
                    // login (mis. dari percobaan sign in yang ditolak karena email belum
                    // diverifikasi, tapi signOut()-nya lupa dipanggil), auto-login lewat Splash ini
                    // TETAP menolak sampai email-nya benar-benar diverifikasi. Admin dikecualikan
                    // karena akunnya dibuat manual lewat Firebase Console, bukan lewat SignUpActivity.
                    if (firebaseAuth.currentUser?.isEmailVerified == false) {
                        firebaseAuth.signOut()
                        goToRoleSelection()
                        return@addOnSuccessListener
                    }

                    val isActive = document.getBoolean("isActive") ?: true
                    if (!isActive) {
                        firebaseAuth.signOut()
                        goToRoleSelection()
                        return@addOnSuccessListener
                    }

                    val isDriver = role.equals("driver", ignoreCase = true)
                    if (isDriver && document.getBoolean("isApproved") != true) {
                        firebaseAuth.signOut()
                        goToRoleSelection()
                        return@addOnSuccessListener
                    }

                    // Klaim sesi device ini sebagai yang aktif untuk akun ini - device lain yang
                    // masih login (kalau ada) akan otomatis ter-logout lewat listener di HomeActivity
                    // begitu mendeteksi activeSessionId di Firestore sudah bukan miliknya lagi.
                    firestore.collection("users").document(uid)
                        .update("activeSessionId", DeviceSession.getSessionId(this))

                    val intent = if (isDriver) {
                        Intent(this, DriverHomeActivity::class.java)
                    } else {
                        Intent(this, PassengerHomeActivity::class.java)
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