package com.example.tugasakhirfinal.ForAdmin

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
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
class AddEditHospitalActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var selectedLatLng: LatLng? = null

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_add_edit_hospital)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val spinner = findViewById<Spinner>(R.id.spinnerIgdStatus)
        spinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Tersedia", "Ramai", "Penuh")
        )

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapPicker) as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<AppCompatButton>(R.id.btnSaveHospital).setOnClickListener { saveHospital() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Default kamera ke Surabaya, mengikuti cakupan data rumah sakit yang sudah ada.
        val surabaya = LatLng(-7.2575, 112.7521)
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(surabaya, 12f))

        mMap.setOnMapClickListener { latLng ->
            selectedLatLng = latLng
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(latLng).title("Lokasi Rumah Sakit"))
        }
    }

    private fun saveHospital() {
        val nama = findViewById<TextInputEditText>(R.id.etHospitalName).text.toString().trim()
        val alamat = findViewById<TextInputEditText>(R.id.etHospitalAddress).text.toString().trim()
        val telepon = findViewById<TextInputEditText>(R.id.etHospitalPhone).text.toString().trim()
        val igdStatus = findViewById<Spinner>(R.id.spinnerIgdStatus).selectedItem.toString()
        val latLng = selectedLatLng

        if (nama.isEmpty() || alamat.isEmpty() || telepon.isEmpty()) {
            Toast.makeText(this, "Semua kolom wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }
        if (latLng == null) {
            Toast.makeText(this, "Ketuk peta untuk memilih lokasi rumah sakit", Toast.LENGTH_SHORT).show()
            return
        }

        val hospitalData = hashMapOf(
            "nama" to nama,
            "alamat" to alamat,
            "telepon" to telepon,
            "igdStatus" to igdStatus,
            "lokasi" to GeoPoint(latLng.latitude, latLng.longitude)
        )

        firestore.collection("hospitals").add(hospitalData)
            .addOnSuccessListener {
                Toast.makeText(this, "Rumah sakit berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
