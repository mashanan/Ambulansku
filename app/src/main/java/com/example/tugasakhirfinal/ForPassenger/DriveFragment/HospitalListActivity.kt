package com.example.tugasakhirfinal.ForPassenger

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R
import com.google.firebase.firestore.FirebaseFirestore

class HospitalListActivity : AppCompatActivity() {

    private lateinit var rvHospitalList: RecyclerView
    private lateinit var etSearchHospital: EditText
    private lateinit var btnBack: ImageView

    private lateinit var hospitalAdapter: HospitalAdapter
    private val fullHospitalList = ArrayList<Hospital>()

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_hospital_list)

        btnBack = findViewById(R.id.btnBack)
        etSearchHospital = findViewById(R.id.etSearchHospital)
        rvHospitalList = findViewById(R.id.rvHospitalList)

        btnBack.setOnClickListener { finish() }

        // Setup RecyclerView
        rvHospitalList.layoutManager = LinearLayoutManager(this)
        hospitalAdapter = HospitalAdapter(fullHospitalList) { selectedHospital ->
            Toast.makeText(this, "Memilih ${selectedHospital.nama}", Toast.LENGTH_SHORT).show()
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

        loadHospitalsFromFirestore()
    }

    private fun loadHospitalsFromFirestore() {
        firestore.collection("hospitals")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Gagal memuat data RS: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    fullHospitalList.clear()
                    for (doc in snapshot.documents) {
                        val nama = doc.getString("nama") ?: "Tanpa Nama"
                        val alamat = doc.getString("alamat") ?: "-"
                        val telepon = doc.getString("telepon") ?: "-"
                        val igdStatus = doc.getString("igdStatus")
                            ?: doc.getString("IGD status")
                            ?: "Tersedia"

                        // Membaca Tipe Data GeoPoint
                        val geoPoint = doc.getGeoPoint("lokasi")
                        val lat = geoPoint?.latitude ?: 0.0
                        val lng = geoPoint?.longitude ?: 0.0

                        // PERBAIKAN: Sesuaikan nama parameter dengan Hospital.kt
                        val hospital = Hospital(
                            id = doc.id,
                            nama = nama,
                            alamat = alamat,
                            telepon = telepon,
                            igdStatus = igdStatus,
                            latitude = lat,
                            longitude = lng
                        )
                        fullHospitalList.add(hospital)
                    }
                    hospitalAdapter.updateList(fullHospitalList)
                } else {
                    Toast.makeText(this, "Belum ada data rumah sakit", Toast.LENGTH_SHORT).show()
                }
            }
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