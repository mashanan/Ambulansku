package com.example.tugasakhirfinal.ForDriver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.ForDriver.Fragment.HomeFragmentDriver // Nama sudah diperbaiki
import com.example.tugasakhirfinal.ForDriver.Fragment.ProfileFragmentDriver
import com.example.tugasakhirfinal.ForDriver.Fragment.ActivityDriveFragmentDriver
import com.example.tugasakhirfinal.ForDriver.Fragment.ChatFragmentDriver
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.ActivityDriverHomeBinding
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint

class DriverHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDriverHomeBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var isOnline = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 🚩 Menampilkan HomeFragmentDriver sebagai tampilan awal
        if (savedInstanceState == null) {
            loadFragment(HomeFragmentDriver())
        }

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
        binding.switchOnline.setOnCheckedChangeListener { _, checked ->
            isOnline = checked
            updateDriverStatus(if (isOnline) "online" else "offline")

            if (isOnline) {
                startLocationUpdates()
                binding.statusText.text = "Status: Online (Siap Menerima Pesanan)"
            } else {
                stopLocationUpdates()
                binding.statusText.text = "Status: Offline"
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun updateDriverStatus(status: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).update("status", status)
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val uid = auth.currentUser?.uid ?: return
                val geoPoint = GeoPoint(location.latitude, location.longitude)

                firestore.collection("users").document(uid)
                    .update("lokasi", geoPoint)
                    .addOnFailureListener { e ->
                        if (isOnline) {
                            Toast.makeText(this@DriverHomeActivity, "Gagal update lokasi: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
        }
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (auth.currentUser != null) {
            updateDriverStatus("offline")
        }
        stopLocationUpdates()
    }
}