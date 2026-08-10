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

        loadHospitalsFromFirestore()
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
