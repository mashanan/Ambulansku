package com.example.tugasakhirfinal.ForPassenger

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.ChatFragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.ActivityDriveFragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.HomeFragment
import com.example.tugasakhirfinal.ForPassenger.Fragment.ProfileFragment
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.ActivityHomeBinding

class PassengerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi SharedPreferences
        sharedPreferences = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

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
    private fun checkProfileCompletion() {
        // Ambil data nama (Kunci: user_name sudah benar)
        val currentName = sharedPreferences.getString("user_name", "Masukkan Nama Anda") ?: "Masukkan Nama Anda"

        // 🚩 PERBAIKAN: Mengganti kunci pembacaan nomor telepon menjadi 'user_phone'
        val currentNoTlp = sharedPreferences.getString("user_phone", "Masukkan Nomor Telepon Anda") ?: "Masukkan Nomor Telepon Anda"

        // Jika nama atau nomor telepon masih berupa default, tampilkan pop-up
        if (currentName == "Masukkan Nama Anda" || currentNoTlp == "Masukkan Nomor Telepon Anda") {
            showIncompleteProfileDialog()
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
