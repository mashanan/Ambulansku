package com.example.tugasakhirfinal.ForPassenger.DriveFragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore

class HospitalListActivity : AppCompatActivity() {

    companion object {
        // Dipakai driver (ActivityDriveFragmentDriver) untuk membuka layar ini sebagai "picker" -
        // memilih RS lain di tengah perjalanan (kondisi darurat butuh fasilitas lebih lengkap),
        // hasilnya dikembalikan lewat setResult() alih-alih langsung membuka ActivityDrive.
        const val EXTRA_PICKER_MODE = "PICKER_MODE"
        const val EXTRA_SELECTED_HOSPITAL_NAME = "SELECTED_HOSPITAL_NAME"
        const val EXTRA_SELECTED_HOSPITAL_LAT = "SELECTED_HOSPITAL_LAT"
        const val EXTRA_SELECTED_HOSPITAL_LNG = "SELECTED_HOSPITAL_LNG"
    }

    private lateinit var rvHospitalList: RecyclerView
    private lateinit var etSearchHospital: EditText
    private lateinit var btnBack: ImageView
    private lateinit var tvTitle: TextView

    private lateinit var hospitalAdapter: HospitalAdapter
    private val fullHospitalList = ArrayList<Hospital>()
    private var isPickerMode = false

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLocation: Location? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) fetchUserLocation() else loadAllHospitals()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_hospital_list)

        btnBack = findViewById(R.id.btnBack)
        etSearchHospital = findViewById(R.id.etSearchHospital)
        rvHospitalList = findViewById(R.id.rvHospitalList)
        tvTitle = findViewById(R.id.tvHospitalListTitle)

        isPickerMode = intent.getBooleanExtra(EXTRA_PICKER_MODE, false)
        if (isPickerMode) {
            tvTitle.text = "Select New Destination Hospital"
        }

        btnBack.setOnClickListener { finish() }

        // Setup RecyclerView
        rvHospitalList.layoutManager = LinearLayoutManager(this)
        hospitalAdapter = HospitalAdapter(fullHospitalList) { selectedHospital ->
            if (isPickerMode) returnPickedHospital(selectedHospital) else openHospitalOnMap(selectedHospital)
        }
        rvHospitalList.adapter = hospitalAdapter

        // Live Search
        etSearchHospital.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterHospitals(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchUserLocation()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // Data RS dimuat SETELAH lokasi didapat (fetchUserLocation -> loadNearbyHospitals), supaya
        // yang dibaca dari Firestore cuma RS di sekitar passenger, bukan seluruh koleksi.
    }

    // Lokasi passenger dipakai untuk menghitung jarak & estimasi waktu ke tiap RS (garis lurus,
    // bukan rute jalan - cukup untuk mengurutkan & memberi gambaran kasar di daftar). Kalau izin
    // ditolak atau lokasi tidak tersedia, daftar tetap tampil tanpa info jarak.
    private fun fetchUserLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    onLocationReady(location)
                    return@addOnSuccessListener
                }
                fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    com.google.android.gms.tasks.CancellationTokenSource().token
                ).addOnSuccessListener { current ->
                    if (current != null) onLocationReady(current) else loadAllHospitals()
                }.addOnFailureListener { loadAllHospitals() }
            }.addOnFailureListener { loadAllHospitals() }
        } catch (e: SecurityException) {
            loadAllHospitals()
        }
    }

    private fun onLocationReady(location: Location) {
        userLocation = location
        loadNearbyHospitals(location)
    }

    // RS terdekat saja (radius 30 km, diperluas otomatis), lihat Maps/NearbyHospitals.kt.
    private fun loadNearbyHospitals(location: Location) {
        com.example.tugasakhirfinal.Maps.NearbyHospitals.load(
            firestore,
            com.google.android.gms.maps.model.LatLng(location.latitude, location.longitude),
            onResult = { nearby, _ ->
                fullHospitalList.clear()
                fullHospitalList.addAll(nearby)
                if (fullHospitalList.isEmpty()) {
                    Toast.makeText(this, "No hospital data available yet", Toast.LENGTH_SHORT).show()
                }
                applyDistancesAndSort()
            },
            onError = { e ->
                Toast.makeText(this, "Failed to load hospital data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Cadangan kalau lokasi tidak tersedia / izin ditolak: baca semua RS sekali (tanpa listener).
    private fun loadAllHospitals() {
        firestore.collection("hospitals").get()
            .addOnSuccessListener { snap ->
                fullHospitalList.clear()
                fullHospitalList.addAll(snap.documents.map(com.example.tugasakhirfinal.Maps.NearbyHospitals::fromDocument))
                applyDistancesAndSort()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load hospital data: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    // Estimasi kasar waktu tempuh (asumsi kecepatan rata-rata ambulans di kota ~30 km/jam), sama
    // dengan yang dipakai di sisi Driver untuk mengurutkan pesanan masuk dari yang terdekat.
    private fun estimateMinutes(distanceMeters: Float): Int {
        val averageSpeedKmh = 30.0
        val distanceKm = distanceMeters / 1000.0
        return ((distanceKm / averageSpeedKmh) * 60).toInt().coerceAtLeast(1)
    }

    // Selalu memperbarui adapter (biar daftar tetap tampil walau lokasi belum/tidak tersedia) -
    // jarak & urutan cuma dihitung ulang kalau lokasi passenger sudah didapat.
    private fun applyDistancesAndSort() {
        val myLocation = userLocation
        if (myLocation != null) {
            for (hospital in fullHospitalList) {
                val hospitalLatLng = hospital.getLatLng() ?: continue
                val results = FloatArray(1)
                Location.distanceBetween(
                    myLocation.latitude, myLocation.longitude,
                    hospitalLatLng.latitude, hospitalLatLng.longitude, results
                )
                hospital.distanceMeters = results[0]
                hospital.estimatedMinutes = estimateMinutes(results[0])
            }
            fullHospitalList.sortBy { if (it.distanceMeters >= 0) it.distanceMeters else Float.MAX_VALUE }
        }
        hospitalAdapter.updateList(fullHospitalList)
    }

    // Buka rumah sakit yang dipilih di peta (ActivityDrive), lengkap dengan rute & estimasi waktu/jarak.
    // Alur pemesanan setelahnya sama persis dengan "Panggil Ambulans" (ActivityDrive sudah menangani
    // kedua kasus lewat extra SELECTED_HOSPITAL_*), bedanya hanya tujuan rumah sakit sudah ditentukan.
    private fun openHospitalOnMap(hospital: Hospital) {
        val latLng = hospital.getLatLng()
        if (latLng == null) {
            Toast.makeText(this, "This hospital's location is not available yet", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ActivityDrive::class.java).apply {
            putExtra("SELECTED_HOSPITAL_NAME", hospital.nama)
            putExtra("SELECTED_HOSPITAL_LAT", latLng.latitude)
            putExtra("SELECTED_HOSPITAL_LNG", latLng.longitude)
        }
        startActivity(intent)
    }

    // Mode picker (dipakai driver ganti tujuan RS di tengah perjalanan) - kembalikan RS yang
    // dipilih ke pemanggil lewat setResult(), tidak membuka ActivityDrive.
    private fun returnPickedHospital(hospital: Hospital) {
        val latLng = hospital.getLatLng()
        if (latLng == null) {
            Toast.makeText(this, "This hospital's location is not available yet", Toast.LENGTH_SHORT).show()
            return
        }

        val resultIntent = Intent().apply {
            putExtra(EXTRA_SELECTED_HOSPITAL_NAME, hospital.nama)
            putExtra(EXTRA_SELECTED_HOSPITAL_LAT, latLng.latitude)
            putExtra(EXTRA_SELECTED_HOSPITAL_LNG, latLng.longitude)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun filterHospitals(query: String) {
        val filteredList = if (query.isEmpty()) {
            fullHospitalList
        } else {
            fullHospitalList.filter {
                it.nama.contains(query, ignoreCase = true) ||
                        it.alamat.contains(query, ignoreCase = true)
            }
        }
        hospitalAdapter.updateList(filteredList)
    }
}