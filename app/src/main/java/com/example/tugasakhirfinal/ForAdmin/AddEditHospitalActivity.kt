package com.example.tugasakhirfinal.ForAdmin

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.example.tugasakhirfinal.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint

// Admin memilih lokasi rumah sakit dengan tap langsung di peta (bukan input manual lat/lng).
// Dipakai untuk Tambah (tanpa extra HOSPITAL_ID) maupun Edit (dengan extra HOSPITAL_ID +
// data lama) — terutama untuk melengkapi rumah sakit lama yang belum punya koordinat.
class AddEditHospitalActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var selectedLatLng: LatLng? = null
    private var editingHospitalId: String? = null

    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        const val EXTRA_ID = "HOSPITAL_ID"
        const val EXTRA_NAME = "HOSPITAL_NAME"
        const val EXTRA_ADDRESS = "HOSPITAL_ADDRESS"
        const val EXTRA_PHONE = "HOSPITAL_PHONE"
        const val EXTRA_LAT = "HOSPITAL_LAT"
        const val EXTRA_LNG = "HOSPITAL_LNG"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_add_edit_hospital)

        editingHospitalId = intent.getStringExtra(EXTRA_ID)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val saveButton = findViewById<AppCompatButton>(R.id.btnSaveHospital)

        if (editingHospitalId != null) {
            findViewById<TextView>(R.id.tvFormTitle).text = "Edit Hospital"
            saveButton.text = "Save Changes"

            findViewById<TextInputEditText>(R.id.etHospitalName).setText(intent.getStringExtra(EXTRA_NAME))
            findViewById<TextInputEditText>(R.id.etHospitalAddress).setText(intent.getStringExtra(EXTRA_ADDRESS))
            findViewById<TextInputEditText>(R.id.etHospitalPhone).setText(intent.getStringExtra(EXTRA_PHONE))

            val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
            val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
            if (lat != 0.0 || lng != 0.0) {
                selectedLatLng = LatLng(lat, lng)
            }
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapPicker) as SupportMapFragment
        mapFragment.getMapAsync(this)

        saveButton.setOnClickListener { saveHospital() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val existingLatLng = selectedLatLng
        if (existingLatLng != null) {
            // Sedang edit & sudah punya koordinat lama — tampilkan langsung markernya.
            mMap.addMarker(MarkerOptions().position(existingLatLng).title("Hospital Location"))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(existingLatLng, 15f))
        } else {
            // Default kamera ke Surabaya, mengikuti cakupan data rumah sakit yang sudah ada.
            val surabaya = LatLng(-7.2575, 112.7521)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(surabaya, 12f))
        }

        mMap.setOnMapClickListener { latLng ->
            selectedLatLng = latLng
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(latLng).title("Hospital Location"))
        }
    }

    private fun saveHospital() {
        val nama = findViewById<TextInputEditText>(R.id.etHospitalName).text.toString().trim()
        val alamat = findViewById<TextInputEditText>(R.id.etHospitalAddress).text.toString().trim()
        val telepon = findViewById<TextInputEditText>(R.id.etHospitalPhone).text.toString().trim()
        val latLng = selectedLatLng

        if (nama.isEmpty() || alamat.isEmpty() || telepon.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
            return
        }
        if (latLng == null) {
            Toast.makeText(this, "Tap the map to select the hospital location", Toast.LENGTH_SHORT).show()
            return
        }

        val hospitalData = hashMapOf(
            "nama" to nama,
            "alamat" to alamat,
            "telepon" to telepon,
            "lokasi" to GeoPoint(latLng.latitude, latLng.longitude),
            // Kunci untuk query RS terdekat (radius), lihat Maps/NearbyHospitals.kt
            "geohash" to com.example.tugasakhirfinal.Maps.NearbyHospitals.geohashFor(latLng.latitude, latLng.longitude)
        )

        val hospitalId = editingHospitalId
        val task = if (hospitalId != null) {
            firestore.collection("hospitals").document(hospitalId).update(hospitalData as Map<String, Any>)
        } else {
            firestore.collection("hospitals").add(hospitalData)
        }

        task.addOnSuccessListener {
            val message = if (hospitalId != null) "Hospital updated successfully" else "Hospital added successfully"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
