package com.example.tugasakhirfinal.ForAdmin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.DeviceSession
import com.example.tugasakhirfinal.ForAdmin.Fragment.HomeFragmentAdmin
import com.example.tugasakhirfinal.ForAdmin.Fragment.ProfileFragmentAdmin
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.RoleSelectionActivity
import com.example.tugasakhirfinal.databinding.ActivityAdminHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class AdminHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminHomeBinding

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var sessionListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        if (savedInstanceState == null) {
            loadFragment(HomeFragmentAdmin())
        }

        listenToSessionStatus()

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragmentAdmin())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragmentAdmin())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    // Admin tidak tunduk pada isActive (lihat SplashScreenActivity), tapi tetap tunduk pada
    // aturan 1 akun 1 device - kalau akun ini login lagi di device LAIN, activeSessionId di
    // Firestore ditimpa device baru tsb, dan device ini (yang lama) langsung dipaksa logout
    // begitu terdeteksi.
    private fun listenToSessionStatus() {
        val uid = auth.currentUser?.uid ?: return
        sessionListener?.remove()
        sessionListener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val remoteSessionId = snapshot?.getString("activeSessionId")
                val localSessionId = DeviceSession.getSessionId(this)
                if (remoteSessionId != null && remoteSessionId != localSessionId) {
                    Toast.makeText(this, "Your account has been logged in on another device.", Toast.LENGTH_LONG).show()
                    auth.signOut()
                    startActivity(Intent(this, RoleSelectionActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionListener?.remove()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
