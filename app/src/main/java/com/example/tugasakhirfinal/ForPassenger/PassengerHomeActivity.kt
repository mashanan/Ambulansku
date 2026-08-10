package com.example.tugasakhirfinal.ForPassenger

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.DeviceSession
import com.example.tugasakhirfinal.ForPassenger.Fragment.ChatFragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.ActivityDriveFragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.HomeFragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.ProfileFragment
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.RoleSelectionActivity
import com.example.tugasakhirfinal.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source

class PassengerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var accountStatusListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Menghilangkan Action Bar
        supportActionBar?.hide()

        // Load default fragment (HomeFragment) saat pertama kali dibuka
        if (savedInstanceState == null) {
            loadFragment(HomeFragment()) // Fragment pertama yang ditampilkan
            checkProfileCompletion() // Periksa profil saat pertama kali HomeActivity dibuka
        }

        listenToAccountStatus()

        // Listener untuk BottomNavigationView
        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_beranda -> loadFragment(HomeFragment()) // Memuat HomeFragment
                R.id.navigation_layanan -> loadFragment(ActivityDriveFragment()) // Memuat DriveFragment
                R.id.navigation_aktivitas -> loadFragment(ChatFragment()) // Memuat ChatFragment
                R.id.navigation_akun -> loadFragment(ProfileFragment()) // Memuat ProfileFragment
            }
            true
        }
    }

    // Fungsi untuk memuat fragment ke dalam FrameLayout
    private fun loadFragment(fragment: Fragment) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()

        // Pastikan fragment yang sama tidak ditambahkan kembali
        val existingFragment = supportFragmentManager.findFragmentByTag(fragment.javaClass.simpleName)
        if (existingFragment == null) {
            fragmentTransaction.replace(R.id.fragment_container, fragment, fragment.javaClass.simpleName)
            fragmentTransaction.commit()
        }
    }

    // Fungsi untuk memeriksa apakah profil pengguna sudah lengkap
    // Sumber data yang benar adalah dokumen Firestore users/{uid} (yang sama dipakai/diupdate oleh ProfileFragment),
    // bukan SharedPreferences yang tidak pernah diisi oleh alur edit profil manapun.
    private fun checkProfileCompletion() {
        val uid = firebaseAuth.currentUser?.uid ?: return

        // Source.SERVER agar tidak terjebak cache Firestore lokal yang mungkin masih menyimpan data lama
        firestore.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { doc ->
                val currentName = doc.getString("nama") ?: ""
                val currentNoTlp = doc.getString("phoneNumber") ?: ""

                val isNameEmpty = currentName.isBlank() || currentName == "Enter Your Name"
                val isPhoneEmpty = currentNoTlp.isBlank() || currentNoTlp == "Enter Your Phone Number"

                if (isNameEmpty || isPhoneEmpty) {
                    showIncompleteProfileDialog()
                }
            }
    }

    // Fungsi untuk menampilkan dialog jika profil belum lengkap
    private fun showIncompleteProfileDialog() {
        AlertDialog.Builder(this)
            .setTitle("Complete Your Profile")
            .setMessage("Your name and phone number are still empty. Please open the Profile page to complete your details.")
            .setPositiveButton("Open Profile") { _, _ ->
                // Pindah ke ProfileFragment
                loadFragment(ProfileFragment())
                // Ubah menu BottomNavigationView ke item "akun"
                binding.bottomNavigationView.selectedItemId = R.id.navigation_akun
            }
            .setCancelable(false) // Pop-up tidak bisa ditutup tanpa tindakan
            .show()
    }

    // 🛠️ Pantau real-time dokumen sendiri — kalau Admin menonaktifkan akun ini SAAT sedang
    // aktif dipakai, langsung paksa logout (tidak perlu tutup-buka app dulu). Juga memantau
    // activeSessionId: kalau akun ini login lagi di device LAIN, field itu akan ditimpa device
    // baru tsb, dan device ini (yang lama) langsung dipaksa logout begitu terdeteksi -
    // menegakkan aturan 1 akun cuma boleh aktif di 1 device.
    private fun listenToAccountStatus() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        accountStatusListener?.remove()
        accountStatusListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val remoteSessionId = snapshot?.getString("activeSessionId")
                val localSessionId = DeviceSession.getSessionId(this)
                if (remoteSessionId != null && remoteSessionId != localSessionId) {
                    forceLogout("Your account has logged in on another device.")
                    return@addSnapshotListener
                }

                val isActive = snapshot?.getBoolean("isActive") ?: true
                if (!isActive) {
                    forceLogout("Your account has been deactivated by an admin.")
                }
            }
    }

    private fun forceLogout(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        firebaseAuth.signOut()
        startActivity(Intent(this, RoleSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        accountStatusListener?.remove()
    }

    // Override tombol Back untuk mencegah kembali ke halaman login
    override fun onBackPressed() {
        moveTaskToBack(true) // Pindahkan aplikasi ke background saat tombol Back ditekan
    }
}
