package com.example.tugasakhirfinal.ForPassenger.EditProfileFragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.tugasakhirfinal.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddAddressActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var etLabel: TextInputEditText
    private lateinit var etFullAddress: TextInputEditText
    private lateinit var etNote: TextInputEditText
    private lateinit var btnSaveAddress: Button

    private var centerMarker: Marker? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()

    // Variabel Mode Edit
    private var isEditMode = false
    private var addressIdToEdit: String? = null
    private var existingLat: Double = -7.2575
    private var existingLng: Double = 112.7521
    private var isFavoriteExisting = false

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_add_address)

        etLabel = findViewById(R.id.etLabel)
        etFullAddress = findViewById(R.id.etFullAddress)
        etNote = findViewById(R.id.etNote)
        btnSaveAddress = findViewById(R.id.btnSaveAddress)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Cek Apakah Mode Edit
        isEditMode = intent.getBooleanExtra("is_edit", false)
        if (isEditMode) {
            addressIdToEdit = intent.getStringExtra("address_id")
            etLabel.setText(intent.getStringExtra("label"))
            etFullAddress.setText(intent.getStringExtra("full_address"))
            etNote.setText(intent.getStringExtra("note"))
            existingLat = intent.getDoubleExtra("latitude", -7.2575)
            existingLng = intent.getDoubleExtra("longitude", 112.7521)
            isFavoriteExisting = intent.getBooleanExtra("is_favorite", false)

            btnSaveAddress.text = "Update Address"
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        btnSaveAddress.setOnClickListener {
            saveAddressToFirestore()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true

        val startLocation = LatLng(existingLat, existingLng)
        val markerOptions = MarkerOptions()
            .position(startLocation)
            .title("Selected Location")
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))

        centerMarker = mMap.addMarker(markerOptions)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLocation, 16f))

        mMap.setOnCameraMoveListener {
            centerMarker?.position = mMap.cameraPosition.target
        }

        mMap.setOnCameraIdleListener {
            centerMarker?.position = mMap.cameraPosition.target
        }

        // Ambil GPS Realtime hanya jika Mode Tambah Baru
        if (!isEditMode) {
            getCurrentUserLocation()
        }
    }

    private fun getCurrentUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        mMap.isMyLocationEnabled = true
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null && !isEditMode) {
                val userLatLng = LatLng(location.latitude, location.longitude)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 17f))
                centerMarker?.position = userLatLng
            }
        }
    }

    private fun saveAddressToFirestore() {
        val label = etLabel.text.toString().trim()
        val fullAddress = etFullAddress.text.toString().trim()
        val note = etNote.text.toString().trim()

        if (label.isEmpty()) {
            etLabel.error = "Label name is required"
            return
        }
        if (fullAddress.isEmpty()) {
            etFullAddress.error = "Full address is required"
            return
        }

        val uid = firebaseAuth.currentUser?.uid ?: return
        val selectedLatLng: LatLng = centerMarker?.position ?: mMap.cameraPosition.target

        val addressData = hashMapOf<String, Any>(
            "label" to label,
            "fullAddress" to fullAddress,
            "note" to note,
            "latitude" to selectedLatLng.latitude,
            "longitude" to selectedLatLng.longitude,
            "isFavorite" to isFavoriteExisting
        )

        val userAddressesRef = firestore.collection("users").document(uid).collection("addresses")

        if (isEditMode && addressIdToEdit != null) {
            // Update Data Alamat Lama
            userAddressesRef.document(addressIdToEdit!!)
                .update(addressData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Address updated successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Tambah Alamat Baru
            addressData["createdAt"] = System.currentTimeMillis()
            userAddressesRef.add(addressData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Address saved successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}