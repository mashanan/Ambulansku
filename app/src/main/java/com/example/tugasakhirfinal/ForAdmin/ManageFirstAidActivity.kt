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
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.ForPassenger.FirstAid.FirstAidCondition
import com.example.tugasakhirfinal.ForPassenger.FirstAid.FirstAidData
import com.example.tugasakhirfinal.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ManageFirstAidActivity : AppCompatActivity() {

    private lateinit var rvFirstAidList: RecyclerView
    private lateinit var etSearchFirstAid: EditText
    private lateinit var cardImportSeed: MaterialCardView
    private lateinit var btnImportSeed: AppCompatButton
    private lateinit var fabAddFirstAid: FloatingActionButton

    private lateinit var firstAidAdapter: FirstAidAdminAdapter
    private val fullConditionList = ArrayList<FirstAidCondition>()

    private val firestore = FirebaseFirestore.getInstance()
    private var firstAidListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_manage_first_aid)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        etSearchFirstAid = findViewById(R.id.etSearchFirstAid)
        cardImportSeed = findViewById(R.id.cardImportSeed)
        btnImportSeed = findViewById(R.id.btnImportSeed)
        rvFirstAidList = findViewById(R.id.rvFirstAidList)
        fabAddFirstAid = findViewById(R.id.fabAddFirstAid)

        fabAddFirstAid.setOnClickListener {
            startActivity(Intent(this, AddEditFirstAidActivity::class.java))
        }
        btnImportSeed.setOnClickListener { importSeedData() }

        rvFirstAidList.layoutManager = LinearLayoutManager(this)
        firstAidAdapter = FirstAidAdminAdapter(
            fullConditionList,
            onEditClick = { condition -> openEditFirstAid(condition) },
            onDeleteClick = { condition -> confirmDeleteFirstAid(condition) }
        )
        rvFirstAidList.adapter = firstAidAdapter

        etSearchFirstAid.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        loadFirstAidFromFirestore()
    }

    override fun onDestroy() {
        super.onDestroy()
        firstAidListener?.remove()
    }

    private fun loadFirstAidFromFirestore() {
        firstAidListener?.remove()
        firstAidListener = firestore.collection("firstAidConditions")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load data: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                fullConditionList.clear()
                if (snapshot != null) {
                    for (doc in snapshot.documents) {
                        fullConditionList.add(
                            FirstAidCondition(
                                id = doc.id,
                                title = doc.getString("title") ?: "",
                                symptoms = doc.getString("symptoms") ?: "",
                                steps = doc.get("steps") as? List<String> ?: emptyList()
                            )
                        )
                    }
                }
                firstAidAdapter.updateList(fullConditionList)

                // Tombol impor data awal cuma tampil kalau koleksi memang masih kosong, dan
                // otomatis hilang lagi begitu ada data (baik dari impor maupun ditambah manual).
                cardImportSeed.visibility = if (snapshot != null && snapshot.isEmpty) View.VISIBLE else View.GONE
            }
    }

    // Migrasi 9 data statis yang sebelumnya hardcoded di FirstAidData.kt ke Firestore, supaya
    // Admin tidak perlu mengetik ulang semuanya dari nol saat pertama kali memakai fitur ini.
    private fun importSeedData() {
        val batch = firestore.batch()
        val collection = firestore.collection("firstAidConditions")

        for (condition in FirstAidData.getList()) {
            val docRef = collection.document()
            batch.set(
                docRef,
                mapOf(
                    "title" to condition.title,
                    "symptoms" to condition.symptoms,
                    "steps" to condition.steps,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }

        btnImportSeed.isEnabled = false
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "9 initial entries imported successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                btnImportSeed.isEnabled = true
                Toast.makeText(this, "Failed to import data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openEditFirstAid(condition: FirstAidCondition) {
        val intent = Intent(this, AddEditFirstAidActivity::class.java).apply {
            putExtra(AddEditFirstAidActivity.EXTRA_ID, condition.id)
            putExtra(AddEditFirstAidActivity.EXTRA_TITLE, condition.title)
            putExtra(AddEditFirstAidActivity.EXTRA_SYMPTOMS, condition.symptoms)
            putExtra(AddEditFirstAidActivity.EXTRA_STEPS, condition.steps.joinToString("\n"))
        }
        startActivity(intent)
    }

    private fun confirmDeleteFirstAid(condition: FirstAidCondition) {
        AlertDialog.Builder(this)
            .setTitle("Delete Condition?")
            .setMessage("Delete \"${condition.title}\" from the First Aid list?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("firstAidConditions").document(condition.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Condition deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun filterList(query: String) {
        val filteredList = if (query.isEmpty()) {
            fullConditionList
        } else {
            fullConditionList.filter {
                it.title.contains(query, ignoreCase = true) || it.symptoms.contains(query, ignoreCase = true)
            }
        }
        firstAidAdapter.updateList(filteredList)
    }
}
