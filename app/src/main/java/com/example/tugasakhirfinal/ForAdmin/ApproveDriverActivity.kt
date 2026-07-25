package com.example.tugasakhirfinal.ForAdmin

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tugasakhirfinal.R
import com.google.firebase.firestore.FirebaseFirestore

class ApproveDriverActivity : AppCompatActivity() {

    private lateinit var rvPendingDrivers: RecyclerView
    private lateinit var tvEmptyPending: TextView
    private lateinit var driverAdapter: PendingDriverAdapter
    private val pendingList = ArrayList<PendingDriverItem>()

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_approve_driver)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        tvEmptyPending = findViewById(R.id.tvEmptyPending)
        rvPendingDrivers = findViewById(R.id.rvPendingDrivers)
        rvPendingDrivers.layoutManager = LinearLayoutManager(this)
        driverAdapter = PendingDriverAdapter(
            pendingList,
            onApproveClick = { driver -> approveDriver(driver) },
            onRejectClick = { driver -> confirmRejectDriver(driver) },
            onPhotoClick = { url -> showPhotoDialog(url) }
        )
        rvPendingDrivers.adapter = driverAdapter

        loadPendingDrivers()
    }

    private fun loadPendingDrivers() {
        firestore.collection("users")
            .whereEqualTo("role", "driver")
            .whereEqualTo("isApproved", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Gagal memuat data: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                pendingList.clear()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        pendingList.add(
                            PendingDriverItem(
                                uid = doc.id,
                                nama = doc.getString("nama") ?: "",
                                email = doc.getString("email") ?: "",
                                licenseNumber = doc.getString("licenseNumber") ?: "",
                                ambulancePlateNumber = doc.getString("ambulancePlateNumber") ?: "",
                                licensePhotoUrl = doc.getString("licensePhotoUrl") ?: "",
                                ambulancePhotoUrl = doc.getString("ambulancePhotoUrl") ?: ""
                            )
                        )
                    }
                }
                driverAdapter.updateList(pendingList)
                tvEmptyPending.visibility = if (pendingList.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun approveDriver(driver: PendingDriverItem) {
        firestore.collection("users").document(driver.uid)
            .update("isApproved", true)
            .addOnSuccessListener {
                Toast.makeText(this, "Driver ${driver.nama} disetujui", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menyetujui: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmRejectDriver(driver: PendingDriverItem) {
        AlertDialog.Builder(this)
            .setTitle("Tolak Driver?")
            .setMessage(
                "Akun \"${driver.nama}\" akan dinonaktifkan dan tidak bisa mengemudi. " +
                    "Bisa diaktifkan lagi nanti lewat Kelola Pengguna."
            )
            .setPositiveButton("Tolak") { _, _ ->
                firestore.collection("users").document(driver.uid)
                    .update("isActive", false)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Driver ${driver.nama} ditolak", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal menolak: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showPhotoDialog(url: String) {
        val imageView = ImageView(this).apply {
            adjustViewBounds = true
        }
        Glide.with(this).load(url).into(imageView)

        AlertDialog.Builder(this)
            .setView(imageView)
            .setPositiveButton("Tutup", null)
            .show()
    }
}
