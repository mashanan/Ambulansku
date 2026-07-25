package com.example.tugasakhirfinal.ForPassenger

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.ChatFragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.ActivityDriveFragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.HomeFragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.ProfileFragment
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.ActivityHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

class PassengerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

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
                Log.d("PROFILE_CHECK", "nama='$currentName' phoneNumber='$currentNoTlp'")

                val isNameEmpty = currentName.isBlank() || currentName == "Masukkan Nama Anda"
                val isPhoneEmpty = currentNoTlp.isBlank() || currentNoTlp == "Masukkan Nomor Telepon Anda"

                if (isNameEmpty || isPhoneEmpty) {
                    showIncompleteProfileDialog()
                }
            }
            .addOnFailureListener { e ->
                Log.e("PROFILE_CHECK", "Gagal cek kelengkapan profil: ${e.message}")
            }
    }

    // Fungsi untuk menampilkan dialog jika profil belum lengkap
    private fun showIncompleteProfileDialog() {
        AlertDialog.Builder(this)
            .setTitle("Lengkapi Profil Anda")
            .setMessage("Nama dan nomor telepon Anda masih kosong. Silakan buka halaman Profil untuk melengkapi data Anda.")
            .setPositiveButton("Buka Profil") { _, _ ->
                // Pindah ke ProfileFragment
                loadFragment(ProfileFragment())
                // Ubah menu BottomNavigationView ke item "akun"
                binding.bottomNavigationView.selectedItemId = R.id.navigation_akun
            }
            .setCancelable(false) // Pop-up tidak bisa ditutup tanpa tindakan
            .show()
    }

    // Override tombol Back untuk mencegah kembali ke halaman login
    override fun onBackPressed() {
        moveTaskToBack(true) // Pindahkan aplikasi ke background saat tombol Back ditekan
    }
}
