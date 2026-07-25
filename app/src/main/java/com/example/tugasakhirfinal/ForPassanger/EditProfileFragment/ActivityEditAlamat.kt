package com.example.tugasakhirfinal.ForPassanger.EditProfileFragment

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ActivityEditAlamat : AppCompatActivity() {

    private lateinit var rvAddressList: RecyclerView
    private lateinit var btnAddNewAddress: Button

    private lateinit var addressAdapter: AddressAdapter
    private val addressList = ArrayList<Address>()

    private val firestore = FirebaseFirestore.getInstance()
    private val firebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_edit_alamat)

        rvAddressList = findViewById(R.id.rvAddressList)
        btnAddNewAddress = findViewById(R.id.btnAddNewAddress)

        rvAddressList.layoutManager = LinearLayoutManager(this)

        addressAdapter = AddressAdapter(
            addressList,
            onFavoriteClick = { address -> toggleFavoriteStatus(address) },
            onEditClick = { address -> openEditAddress(address) },
            onSetPrimaryClick = { address -> setAsPrimaryAddress(address) },
            onDeleteClick = { address -> confirmDeleteAddress(address) } // Menangani klik hapus
        )
        rvAddressList.adapter = addressAdapter

        btnAddNewAddress.setOnClickListener {
            val intent = Intent(this, AddAddressActivity::class.java)
            startActivity(intent)
        }

        loadSavedAddresses()
    }

    private fun loadSavedAddresses() {
        val uid = firebaseAuth.currentUser?.uid ?: return

        firestore.collection("users").document(uid)
            .collection("addresses")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Gagal memuat data: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    addressList.clear()
                    for (doc in snapshot.documents) {
                        val address = doc.toObject(Address::class.java)
                        if (address != null) {
                            address.id = doc.id
                            addressList.add(address)
                        }
                    }
                    // Urutkan Alamat Utama paling atas, disusul Favorit
                    addressList.sortWith(compareByDescending<Address> { it.isPrimary }.thenByDescending { it.isFavorite })
                    addressAdapter.notifyDataSetChanged()
                }
            }
    }

    private fun confirmDeleteAddress(address: Address) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Alamat")
            .setMessage("Apakah Anda yakin ingin menghapus alamat '${address.label}'?")
            .setPositiveButton("Hapus") { _, _ ->
                deleteAddressFromFirestore(address)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteAddressFromFirestore(address: Address) {
        val uid = firebaseAuth.currentUser?.uid ?: return

        firestore.collection("users").document(uid)
            .collection("addresses").document(address.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Alamat '${address.label}' berhasil dihapus", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menghapus alamat: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setAsPrimaryAddress(selectedAddress: Address) {
        val uid = firebaseAuth.currentUser?.uid ?: return

        val batch = firestore.batch()
        val addressesRef = firestore.collection("users").document(uid).collection("addresses")

        for (item in addressList) {
            val docRef = addressesRef.document(item.id)
            if (item.id == selectedAddress.id) {
                batch.update(docRef, "isPrimary", true)
            } else {
                batch.update(docRef, "isPrimary", false)
            }
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "${selectedAddress.label} dijadikan Alamat Utama!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal mengubah alamat utama: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleFavoriteStatus(address: Address) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val newStatus = !address.isFavorite

        firestore.collection("users").document(uid)
            .collection("addresses").document(address.id)
            .update("isFavorite", newStatus)
            .addOnSuccessListener {
                val statusText = if (newStatus) "ditambahkan ke favorit" else "dihapus dari favorit"
                Toast.makeText(this, "Alamat $statusText", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openEditAddress(address: Address) {
        val intent = Intent(this, AddAddressActivity::class.java).apply {
            putExtra("is_edit", true)
            putExtra("address_id", address.id)
            putExtra("label", address.label)
            putExtra("full_address", address.fullAddress)
            putExtra("note", address.note)
            putExtra("latitude", address.latitude)
            putExtra("longitude", address.longitude)
            putExtra("is_favorite", address.isFavorite)
            putExtra("is_primary", address.isPrimary)
        }
        startActivity(intent)
    }
}