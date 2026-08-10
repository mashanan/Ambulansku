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
import com.google.firebase.firestore.ListenerRegistration

class ManageUsersActivity : AppCompatActivity() {

    private lateinit var rvUserList: RecyclerView
    private lateinit var etSearchUser: EditText
    private lateinit var btnBack: ImageView

    private lateinit var userAdapter: UserAdminAdapter
    private val fullUserList = ArrayList<AdminUserItem>()

    private val firestore = FirebaseFirestore.getInstance()
    private var usersListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_manage_users)

        btnBack = findViewById(R.id.btnBack)
        etSearchUser = findViewById(R.id.etSearchUser)
        rvUserList = findViewById(R.id.rvUserList)

        btnBack.setOnClickListener { finish() }

        rvUserList.layoutManager = LinearLayoutManager(this)
        userAdapter = UserAdminAdapter(
            fullUserList,
            onToggleActiveClick = { user -> confirmToggleActive(user) },
            onDeleteClick = { user -> confirmDeleteUser(user) }
        )
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

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.remove()
    }

    private fun loadUsersFromFirestore() {
        usersListener?.remove()
        usersListener = firestore.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load user data: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
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
        val action = if (user.isActive) "deactivate" else "reactivate"
        AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage("Are you sure you want to $action the account \"${user.nama}\"?")
            .setPositiveButton("Yes") { _, _ ->
                firestore.collection("users").document(user.uid)
                    .update("isActive", !user.isActive)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Account status updated", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Hanya menghapus dokumen Firestore (users/{uid}), TIDAK menyentuh akun Firebase Auth -
    // dipakai admin untuk membersihkan data pengguna yang akun Auth-nya sudah dihapus duluan
    // lewat Firebase Console (data "hantu" yang tetap nyangkut & masih bisa dinonaktifkan
    // walau akunnya sendiri sudah tidak ada).
    private fun confirmDeleteUser(user: AdminUserItem) {
        AlertDialog.Builder(this)
            .setTitle("Permanently Delete Account?")
            .setMessage(
                "This will delete \"${user.nama}\"'s (${user.email}) data from the app database. " +
                    "Use this ONLY if this account has already been deleted from Firebase Authentication. " +
                    "This action cannot be undone."
            )
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("users").document(user.uid).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Account data deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
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
