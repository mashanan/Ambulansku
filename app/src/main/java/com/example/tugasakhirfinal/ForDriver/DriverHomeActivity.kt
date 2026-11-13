package com.example.tugasakhirfinal.ForDriver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.DeviceSession
import com.example.tugasakhirfinal.ForDriver.Fragment.HomeFragmentDriver // Nama sudah diperbaiki
import com.example.tugasakhirfinal.ForDriver.Fragment.ProfileFragmentDriver
import com.example.tugasakhirfinal.ForDriver.Fragment.ActivityDriveFragmentDriver
import com.example.tugasakhirfinal.ForDriver.Fragment.ChatFragmentDriver
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.RoleSelectionActivity
import com.example.tugasakhirfinal.databinding.ActivityDriverHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source

class DriverHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverHomeBinding

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var accountStatusListener: ListenerRegistration? = null

    // Cache dari listenToAccountStatus() - dipakai gerbang switchOnline supaya driver yang
    // sedang menunggu verifikasi ulang Admin (isApproved=false, mis. baru saja edit data
    // kualifikasi) tidak bisa online/menerima pesanan sementara.
    private var isApprovedCache = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🚩 Menampilkan HomeFragmentDriver sebagai tampilan awal
        if (savedInstanceState == null) {
            loadFragment(HomeFragmentDriver())
            checkProfileCompletion() // Periksa profil saat pertama kali HomeActivity dibuka
        }

        listenToAccountStatus()

        // Setup Bottom Navigation
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragmentDriver())
                    true
                }
                R.id.nav_drive -> {
                    loadFragment(ActivityDriveFragmentDriver())
                    true
                }
                R.id.nav_chat -> {
                    loadFragment(ChatFragmentDriver())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragmentDriver())
                    true
                }
                else -> false
            }
        }

        // Setup Toggle Button Online/Offline
        // 🛠️ Lokasi & status "online"/"offline" ditangani oleh DriverOnlineService (Foreground
        // Service), BUKAN langsung di sini — supaya driver tetap terlacak & bisa dapat notifikasi
        // pesanan baru walau app di-background. Lihat DriverOnlineService.kt.
        binding.switchOnline.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!isApprovedCache) {
                    Toast.makeText(this, "Your account is pending re-verification by the Admin, you cannot go online for now.", Toast.LENGTH_LONG).show()
                    binding.switchOnline.isChecked = false
                    return@setOnCheckedChangeListener
                }
                requestOnlinePermissionsIfNeeded()
                ContextCompat.startForegroundService(this, Intent(this, DriverOnlineService::class.java))
                binding.statusText.text = "You're online"
                binding.tvStatusHint.text = "Ready to accept nearby bookings"
                binding.statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.v2_success)
            } else {
                stopService(Intent(this, DriverOnlineService::class.java))
                binding.statusText.text = "You're offline"
                binding.tvStatusHint.text = "Turn on to receive bookings"
                binding.statusDot.backgroundTintList = ContextCompat.getColorStateList(this, R.color.v2_text_disabled)
            }
        }
    }

    // Minta izin lokasi & notifikasi kalau belum diberikan. DriverOnlineService tidak bisa
    // memicu dialog izin sendiri (bukan Activity), jadi permintaannya harus dari sini.
    private fun requestOnlinePermissionsIfNeeded() {
        val missingPermissions = mutableListOf<String>()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // Fungsi untuk memeriksa apakah profil driver sudah lengkap
    private fun checkProfileCompletion() {
        val uid = auth.currentUser?.uid ?: return

        // Source.SERVER agar tidak terjebak cache Firestore lokal yang mungkin masih menyimpan data lama
        firestore.collection("users").document(uid).get(Source.SERVER)
            .addOnSuccessListener { doc ->
                val currentName = doc.getString("nama") ?: ""
                val currentNoTlp = doc.getString("phoneNumber") ?: ""

                val isNameEmpty = currentName.isBlank() || currentName == "New Driver"
                val isPhoneEmpty = currentNoTlp.isBlank() || currentNoTlp == "Not filled in"

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
                loadFragment(ProfileFragmentDriver())
                binding.bottomNavigation.selectedItemId = R.id.nav_profile
            }
            .setCancelable(false) // Pop-up tidak bisa ditutup tanpa tindakan
            .show()
    }

    // 🛠️ Pantau real-time dokumen sendiri — kalau Admin menonaktifkan akun ini SAAT sedang
    // aktif dipakai, hentikan dulu DriverOnlineService (kalau sedang Online) lalu paksa logout.
    // Juga pantau isApproved: kalau turun jadi false (mis. driver baru saja edit data
    // kualifikasi & menunggu verifikasi ulang) SAAT sedang online, paksa offline - TAPI beda
    // dari isActive=false, ini BUKAN sign-out penuh, driver tetap boleh pakai app.
    // Juga pantau activeSessionId: kalau akun ini login lagi di device LAIN, field itu ditimpa
    // device baru tsb, dan device ini (yang lama) langsung dipaksa logout begitu terdeteksi -
    // menegakkan aturan 1 akun cuma boleh aktif di 1 device.
    private fun listenToAccountStatus() {
        val uid = auth.currentUser?.uid ?: return
        accountStatusListener?.remove()
        accountStatusListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val remoteSessionId = snapshot?.getString("activeSessionId")
                val localSessionId = DeviceSession.getSessionId(this)
                if (remoteSessionId != null && remoteSessionId != localSessionId) {
                    forceLogout("Your account was signed in on another device.")
                    return@addSnapshotListener
                }

                val isActive = snapshot?.getBoolean("isActive") ?: true
                if (!isActive) {
                    forceLogout("Your account has been deactivated by the admin.")
                    return@addSnapshotListener
                }

                val isApproved = snapshot?.getBoolean("isApproved") ?: true
                isApprovedCache = isApproved
                if (!isApproved && binding.switchOnline.isChecked) {
                    // Set isChecked=false memicu listener switch di atas (cabang else: stopService
                    // + update statusText), jadi tidak perlu duplikasi logika itu di sini.
                    binding.switchOnline.isChecked = false
                    Toast.makeText(this, "Your account is pending re-verification by the Admin. You cannot go online for now.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun forceLogout(message: String) {
        stopService(Intent(this, DriverOnlineService::class.java))
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        auth.signOut()
        startActivity(Intent(this, RoleSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        accountStatusListener?.remove()
    }
}