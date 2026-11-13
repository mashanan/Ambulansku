package com.example.tugasakhirfinal.ForAdmin

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.ForPassenger.DriveFragment.Hospital
import com.example.tugasakhirfinal.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ManageHospitalActivity : AppCompatActivity() {

    private lateinit var rvHospitalList: RecyclerView
    private lateinit var etSearchHospital: EditText
    private lateinit var btnBack: ImageView
    private lateinit var fabAddHospital: FloatingActionButton

    private lateinit var hospitalAdapter: HospitalAdminAdapter
    private val fullHospitalList = ArrayList<Hospital>()

    private val firestore = FirebaseFirestore.getInstance()
    private var hospitalsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_manage_hospital)

        btnBack = findViewById(R.id.btnBack)
        etSearchHospital = findViewById(R.id.etSearchHospital)
        rvHospitalList = findViewById(R.id.rvHospitalList)
        fabAddHospital = findViewById(R.id.fabAddHospital)

        btnBack.setOnClickListener { finish() }
        fabAddHospital.setOnClickListener {
            startActivity(Intent(this, AddEditHospitalActivity::class.java))
        }

        rvHospitalList.layoutManager = LinearLayoutManager(this)
        hospitalAdapter = HospitalAdminAdapter(
            fullHospitalList,
            onEditClick = { hospital -> openEditHospital(hospital) },
            onDeleteClick = { hospital -> confirmDeleteHospital(hospital) }
        )
        rvHospitalList.adapter = hospitalAdapter

        etSearchHospital.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterHospitals(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        cardImportHospitals = findViewById(R.id.cardImportHospitals)
        tvImportHospitalsMessage = findViewById(R.id.tvImportHospitalsMessage)
        btnImportHospitals = findViewById(R.id.btnImportHospitals)
        btnImportHospitals.setOnClickListener { importBuiltInHospitals() }

        loadHospitalsFromFirestore()
    }

    // ===== Impor data RS bawaan (assets/hospitals_seed.json) =====
    // Data RS besar se-Pulau Jawa di luar Surabaya (Jawa Timur, DKI Jakarta, Banten, Jawa Barat,
    // Jawa Tengah, DI Yogyakarta) dari Google Places, dibangun dengan scripts/fetch_hospitals_*.js
    // + scripts/build_hospital_seed.js. Ditulis Admin sendiri
    // lewat tombol Import (Firestore rules: hanya admin yang boleh menulis "hospitals").
    private lateinit var cardImportHospitals: View
    private lateinit var tvImportHospitalsMessage: android.widget.TextView
    private lateinit var btnImportHospitals: com.google.android.material.button.MaterialButton
    private var latestHospitalDocs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()

    private data class SeedHospital(
        val nama: String, val alamat: String, val telepon: String,
        val latitude: Double, val longitude: Double, val wilayah: String, val provinsi: String, val placeId: String
    )

    private val seedHospitals: List<SeedHospital> by lazy {
        try {
            val json = assets.open("hospitals_seed.json").bufferedReader().use { it.readText() }
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SeedHospital(
                    o.getString("nama"), o.getString("alamat"), o.optString("telepon", "-"),
                    o.getDouble("latitude"), o.getDouble("longitude"),
                    o.optString("wilayah", ""), o.optString("provinsi", ""), o.optString("placeId", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun nameKey(name: String) = name.lowercase().replace(Regex("[^a-z0-9]"), "")

    // RS bawaan yang belum ada di Firestore: dicocokkan lewat placeId, atau nama yang sama
    // (supaya RS yang sudah diinput manual dengan nama persis sama tidak dobel).
    private fun missingSeedHospitals(): List<SeedHospital> {
        val placeIds = latestHospitalDocs.mapNotNull { it.getString("placeId") }.toSet()
        val names = latestHospitalDocs.map { nameKey(it.getString("nama") ?: "") }.toSet()
        return seedHospitals.filter { it.placeId !in placeIds && nameKey(it.nama) !in names }
    }

    // Dokumen RS yang punya lokasi tapi belum punya geohash: tidak akan muncul di pencarian
    // RS terdekat (Maps/NearbyHospitals.kt), jadi perlu dilengkapi sekali lewat kartu ini.
    private fun docsMissingGeohash() = latestHospitalDocs.filter {
        it.getString(com.example.tugasakhirfinal.Maps.NearbyHospitals.FIELD_GEOHASH).isNullOrEmpty() &&
            it.getGeoPoint("lokasi") != null
    }

    private fun updateImportCard() {
        val missing = missingSeedHospitals()
        val unindexed = docsMissingGeohash()
        if (missing.isEmpty() && unindexed.isEmpty()) {
            cardImportHospitals.visibility = View.GONE
            return
        }
        val parts = mutableListOf<String>()
        if (missing.isNotEmpty()) {
            val provinces = missing.map { it.provinsi }.filter { it.isNotEmpty() }.distinct()
            parts += "${missing.size} major hospitals ready to import" +
                if (provinces.isNotEmpty()) " (${provinces.joinToString(", ")})" else ""
        }
        if (unindexed.isNotEmpty()) {
            parts += "${unindexed.size} hospitals need a location index for nearby search"
        }
        tvImportHospitalsMessage.text = parts.joinToString(". ") + "."
        btnImportHospitals.text = if (missing.isNotEmpty()) "Import" else "Update"
        btnImportHospitals.isEnabled = true
        cardImportHospitals.visibility = View.VISIBLE
    }

    // Tulis RS bawaan yang belum ada + lengkapi geohash dokumen lama. Dipecah per 400 operasi
    // karena satu batch Firestore maksimal 500 tulisan.
    private fun importBuiltInHospitals() {
        val missing = missingSeedHospitals()
        val unindexed = docsMissingGeohash()
        if (missing.isEmpty() && unindexed.isEmpty()) return
        val collection = firestore.collection("hospitals")
        val ops = mutableListOf<(com.google.firebase.firestore.WriteBatch) -> Unit>()
        for (doc in unindexed) {
            val gp = doc.getGeoPoint("lokasi") ?: continue
            ops += { b -> b.update(doc.reference, com.example.tugasakhirfinal.Maps.NearbyHospitals.FIELD_GEOHASH,
                com.example.tugasakhirfinal.Maps.NearbyHospitals.geohashFor(gp.latitude, gp.longitude)) }
        }
        for (h in missing) {
            ops += { batch -> batch.set(
                collection.document(),
                mapOf(
                    "nama" to h.nama,
                    "alamat" to h.alamat,
                    "telepon" to h.telepon,
                    "lokasi" to com.google.firebase.firestore.GeoPoint(h.latitude, h.longitude),
                    "wilayah" to h.wilayah,
                    "provinsi" to h.provinsi,
                    "placeId" to h.placeId,
                    "source" to "google_places",
                    com.example.tugasakhirfinal.Maps.NearbyHospitals.FIELD_GEOHASH to
                        com.example.tugasakhirfinal.Maps.NearbyHospitals.geohashFor(h.latitude, h.longitude)
                )
            ) }
        }
        btnImportHospitals.isEnabled = false
        val commits = ops.chunked(400).map { chunk ->
            val batch = firestore.batch()
            chunk.forEach { it(batch) }
            batch.commit()
        }
        com.google.android.gms.tasks.Tasks.whenAll(commits)
            .addOnSuccessListener {
                Toast.makeText(this, "${missing.size} imported, ${unindexed.size} indexed", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                btnImportHospitals.isEnabled = true
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        loadHospitalsFromFirestore()
    }

    override fun onDestroy() {
        super.onDestroy()
        hospitalsListener?.remove()
    }

    private fun loadHospitalsFromFirestore() {
        hospitalsListener?.remove()
        hospitalsListener = firestore.collection("hospitals")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load hospital data: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                fullHospitalList.clear()
                latestHospitalDocs = snapshot?.documents ?: emptyList()
                if (snapshot != null) updateImportCard()
                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        val nama = doc.getString("nama") ?: "Untitled"
                        val alamat = doc.getString("alamat") ?: "-"
                        val telepon = doc.getString("telepon") ?: "-"

                        val geoPoint = doc.getGeoPoint("lokasi")
                        val lat = geoPoint?.latitude ?: 0.0
                        val lng = geoPoint?.longitude ?: 0.0

                        val hospital = Hospital(
                            id = doc.id,
                            nama = nama,
                            alamat = alamat,
                            telepon = telepon,
                            latitude = lat,
                            longitude = lng
                        )
                        fullHospitalList.add(hospital)
                    }
                }
                hospitalAdapter.updateList(fullHospitalList)
            }
    }

    private fun openEditHospital(hospital: Hospital) {
        val intent = Intent(this, AddEditHospitalActivity::class.java).apply {
            putExtra(AddEditHospitalActivity.EXTRA_ID, hospital.id)
            putExtra(AddEditHospitalActivity.EXTRA_NAME, hospital.nama)
            putExtra(AddEditHospitalActivity.EXTRA_ADDRESS, hospital.alamat)
            putExtra(AddEditHospitalActivity.EXTRA_PHONE, hospital.telepon)
            putExtra(AddEditHospitalActivity.EXTRA_LAT, hospital.latitude)
            putExtra(AddEditHospitalActivity.EXTRA_LNG, hospital.longitude)
        }
        startActivity(intent)
    }

    private fun confirmDeleteHospital(hospital: Hospital) {
        AlertDialog.Builder(this)
            .setTitle("Delete Hospital?")
            .setMessage("Delete \"${hospital.nama}\" from the hospital list?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("hospitals").document(hospital.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Hospital deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun filterHospitals(query: String) {
        val filteredList = if (query.isEmpty()) {
            fullHospitalList
        } else {
            fullHospitalList.filter {
                it.nama.contains(query, ignoreCase = true) || it.alamat.contains(query, ignoreCase = true)
            }
        }
        hospitalAdapter.updateList(filteredList)
    }
}
