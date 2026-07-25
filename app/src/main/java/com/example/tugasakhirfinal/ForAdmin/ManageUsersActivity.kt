package com.example.tugasakhirfinal.ForAdmin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R
import com.google.firebase.firestore.FirebaseFirestore

class ManageUsersActivity : AppCompatActivity() {

    private lateinit var rvUserList: RecyclerView
    private lateinit var etSearchUser: EditText
    private lateinit var btnBack: ImageView

    private lateinit var userAdapter: UserAdminAdapter
    private val fullUserList = ArrayList<AdminUserItem>()

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_manage_users)

        btnBack = findViewById(R.id.btnBack)
        etSearchUser = findViewById(R.id.etSearchUser)
        rvUserList = findViewById(R.id.rvUserList)

        btnBack.setOnClickListener { finish() }

        rvUserList.layoutManager = LinearLayoutManager(this)
        userAdapter = UserAdminAdapter(fullUserList) { user ->
            confirmToggleActive(user)
        }
        rvUserList.adapter = userAdapter

        etSearchUser.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadUsersFromFirestore()
    }

    private fun loadUsersFromFirestore() {
        firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Gagal memuat data pengguna: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                fullUserList.clear()
                if (snapshot != null && !snapshot.isEmpty) {
                    for (doc in snapshot.documents) {
                        val role = doc.getString("role") ?: ""
                        // Akun Admin tidak ditampilkan/dikelola di sini.
                        if (role.equals("admin", ignoreCase = true)) continue

                        fullUserList.add(
                            AdminUserItem(
                                uid = doc.id,
                                nama = doc.getString("nama") ?: "",
                                email = doc.getString("email") ?: "",
                                role = role,
                                isActive = doc.getBoolean("isActive") ?: true
                            )
                        )
                    }
                }
                userAdapter.updateList(fullUserList)
            }
    }

    private fun confirmToggleActive(user: AdminUserItem) {
        val action = if (user.isActive) "menonaktifkan" else "mengaktifkan kembali"
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi")
            .setMessage("Yakin ingin $action akun \"${user.nama}\"?")
            .setPositiveButton("Ya") { _, _ ->
                firestore.collection("users").document(user.uid)
                    .update("isActive", !user.isActive)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Status akun diperbarui", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal memperbarui: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun filterUsers(query: String) {
        val filteredList = if (query.isEmpty()) {
            fullUserList
        } else {
            fullUserList.filter {
                it.nama.contains(query, ignoreCase = true) || it.email.contains(query, ignoreCase = true)
            }
        }
        userAdapter.updateList(filteredList)
    }
}
