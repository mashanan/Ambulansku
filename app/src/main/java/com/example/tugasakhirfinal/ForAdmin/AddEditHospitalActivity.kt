package com.example.tugasakhirfinal.ForAdmin

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
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
        const val EXTRA_IGD_STATUS = "HOSPITAL_IGD_STATUS"
        const val EXTRA_LAT = "HOSPITAL_LAT"
        const val EXTRA_LNG = "HOSPITAL_LNG"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_add_edit_hospital)

        editingHospitalId = intent.getStringExtra(EXTRA_ID)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val spinner = findViewById<Spinner>(R.id.spinnerIgdStatus)
        val statusOptions = listOf("Tersedia", "Ramai", "Penuh")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statusOptions)

        val saveButton = findViewById<AppCompatButton>(R.id.btnSaveHospital)

        if (editingHospitalId != null) {
            findViewById<TextView>(R.id.tvFormTitle).text = "Edit Rumah Sakit"
            saveButton.text = "Simpan Perubahan"

            findViewById<TextInputEditText>(R.id.etHospitalName).setText(intent.getStringExtra(EXTRA_NAME))
            findViewById<TextInputEditText>(R.id.etHospitalAddress).setText(intent.getStringExtra(EXTRA_ADDRESS))
            findViewById<TextInputEditText>(R.id.etHospitalPhone).setText(intent.getStringExtra(EXTRA_PHONE))

            val currentStatus = intent.getStringExtra(EXTRA_IGD_STATUS)
            val statusIndex = statusOptions.indexOf(currentStatus)
            if (statusIndex >= 0) spinner.setSelection(statusIndex)

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
            mMap.addMarker(MarkerOptions().position(existingLatLng).title("Lokasi Rumah Sakit"))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(existingLatLng, 15f))
        } else {
            // Default kamera ke Surabaya, mengikuti cakupan data rumah sakit yang sudah ada.
            val surabaya = LatLng(-7.2575, 112.7521)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(surabaya, 12f))
        }

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

        val hospitalId = editingHospitalId
        val task = if (hospitalId != null) {
            firestore.collection("hospitals").document(hospitalId).update(hospitalData as Map<String, Any>)
        } else {
            firestore.collection("hospitals").add(hospitalData)
        }

        task.addOnSuccessListener {
            val message = if (hospitalId != null) "Rumah sakit berhasil diperbarui" else "Rumah sakit berhasil ditambahkan"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
