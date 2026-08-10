package com.example.tugasakhirfinal.ForAdmin

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tugasakhirfinal.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ApproveDriverActivity : AppCompatActivity() {

    private lateinit var rvPendingDrivers: RecyclerView
    private lateinit var layoutEmptyPending: View
    private lateinit var driverAdapter: PendingDriverAdapter
    private val pendingList = ArrayList<PendingDriverItem>()

    private val firestore = FirebaseFirestore.getInstance()
    private var pendingDriversListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_approve_driver)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        layoutEmptyPending = findViewById(R.id.layoutEmptyPending)
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

    override fun onDestroy() {
        super.onDestroy()
        pendingDriversListener?.remove()
    }

    private fun loadPendingDrivers() {
        // Query hanya berdasar role="driver" (bukan sekaligus isApproved==false) supaya akun driver
        // lama yang belum punya field isApproved sama sekali (dibuat sebelum fitur ini ada) tetap
        // ikut tersaring & muncul di sini, bukan hilang begitu saja. Filter isApproved dilakukan
        // di client agar mencakup baik isApproved==false maupun field yang belum ada (null).
        pendingDriversListener?.remove()
        pendingDriversListener = firestore.collection("users")
            .whereEqualTo("role", "driver")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load data: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                pendingList.clear()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        if (doc.getBoolean("isApproved") == true) continue

                        @Suppress("UNCHECKED_CAST")
                        pendingList.add(
                            PendingDriverItem(
                                uid = doc.id,
                                nama = doc.getString("nama") ?: "",
                                email = doc.getString("email") ?: "",
                                licenseNumber = doc.getString("licenseNumber") ?: "",
                                ambulancePlateNumber = doc.getString("ambulancePlateNumber") ?: "",
                                ambulanceCategory = doc.getString("ambulanceCategory") ?: "Ambulans Biasa",
                                licensePhotoUrl = doc.getString("licensePhotoUrl") ?: "",
                                stnkPhotoUrl = doc.getString("stnkPhotoUrl") ?: "",
                                ambulancePhotoUrls = doc.get("ambulancePhotoUrls") as? List<String> ?: emptyList(),
                                aedCertificateUrl = doc.getString("aedCertificateUrl") ?: "",
                                medicalCertificateUrl = doc.getString("medicalCertificateUrl") ?: "",
                                sopSirineChecked = doc.getBoolean("sopSirineChecked") ?: false,
                                sopEquipmentChecked = doc.getBoolean("sopEquipmentChecked") ?: false,
                                sopApdChecked = doc.getBoolean("sopApdChecked") ?: false,
                                sopHealthCheckChecked = doc.getBoolean("sopHealthCheckChecked") ?: false
                            )
                        )
                    }
                }
                driverAdapter.updateList(pendingList)
                layoutEmptyPending.visibility = if (pendingList.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun approveDriver(driver: PendingDriverItem) {
        firestore.collection("users").document(driver.uid)
            .update("isApproved", true)
            .addOnSuccessListener {
                Toast.makeText(this, "Driver ${driver.nama} approved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to approve: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmRejectDriver(driver: PendingDriverItem) {
        AlertDialog.Builder(this)
            .setTitle("Reject Driver?")
            .setMessage(
                "The account \"${driver.nama}\" will be deactivated and won't be able to drive. " +
                    "It can be reactivated later from Manage Users."
            )
            .setPositiveButton("Reject") { _, _ ->
                firestore.collection("users").document(driver.uid)
                    .update("isActive", false)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Driver ${driver.nama} rejected", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to reject: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Viewer foto full-screen (bukan popup kecil) - tombol tutup berbentuk X ditumpuk di pojok
    // kanan atas foto, latar hitam penuh layar supaya foto terlihat sejelas mungkin.
    private fun showPhotoDialog(url: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_full_image)

        val ivFullImage = dialog.findViewById<ImageView>(R.id.ivFullImage)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseFullImage)

        Glide.with(this)
            .load(url)
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e("ApproveDriver", "Gagal memuat foto $url: ${e?.message}", e)
                    Toast.makeText(this@ApproveDriverActivity, "Failed to load photo: ${e?.rootCauses?.firstOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    return false
                }
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean
                ): Boolean = false
            })
            .into(ivFullImage)

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}
