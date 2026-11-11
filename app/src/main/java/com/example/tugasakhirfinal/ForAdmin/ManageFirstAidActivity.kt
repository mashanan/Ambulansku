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
    private lateinit var tvImportSeedMessage: android.widget.TextView
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
        tvImportSeedMessage = findViewById(R.id.tvImportSeedMessage)
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
                latestDocs = snapshot?.documents ?: emptyList()
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

                // Kartu impor tampil kalau koleksi masih kosong ATAU ada panduan bawaan
                // (FirstAidData) yang belum ada / masih versi lama di Firestore.
                if (snapshot == null) {
                    cardImportSeed.visibility = View.GONE
                } else {
                    val (newOnes, outdated) = computeSeedChanges(latestDocs)
                    if (newOnes.isEmpty() && outdated.isEmpty()) {
                        cardImportSeed.visibility = View.GONE
                    } else {
                        tvImportSeedMessage.text = if (snapshot.isEmpty) {
                            "No data yet. Import ${newOnes.size} built-in First Aid guides?"
                        } else {
                            "Built-in guide update available: ${newOnes.size} new, ${outdated.size} updated. Guides you edited yourself are not changed."
                        }
                        btnImportSeed.text = if (snapshot.isEmpty) "Import" else "Update"
                        btnImportSeed.isEnabled = true
                        cardImportSeed.visibility = View.VISIBLE
                    }
                }
            }
    }

    private var latestDocs: List<com.google.firebase.firestore.DocumentSnapshot> = emptyList()

    private fun titleKey(title: String) = title.trim().lowercase()

    // Bandingkan panduan bawaan (FirstAidData) dengan isi Firestore, dicocokkan lewat judul:
    //  - newOnes  : judul bawaan yang belum ada sama sekali di Firestore
    //  - outdated : dokumen dengan judul bawaan yang versinya < SEED_VERSION dan belum pernah
    //               diedit Admin (field "customized" != true)
    // Entri buatan Admin sendiri (judul tidak ada di daftar bawaan) tidak pernah disentuh.
    private fun computeSeedChanges(
        docs: List<com.google.firebase.firestore.DocumentSnapshot>
    ): Pair<List<FirstAidCondition>, List<Pair<com.google.firebase.firestore.DocumentSnapshot, FirstAidCondition>>> {
        val docsByTitle = docs.associateBy { titleKey(it.getString("title") ?: "") }
        val newOnes = mutableListOf<FirstAidCondition>()
        val outdated = mutableListOf<Pair<com.google.firebase.firestore.DocumentSnapshot, FirstAidCondition>>()
        for (seed in FirstAidData.getList()) {
            val doc = docsByTitle[titleKey(seed.title)]
            if (doc == null) {
                newOnes.add(seed)
            } else {
                val version = doc.getLong("builtInVersion") ?: 0L
                val customized = doc.getBoolean("customized") == true
                if (version < FirstAidData.SEED_VERSION && !customized) outdated.add(doc to seed)
            }
        }
        return newOnes to outdated
    }

    // Impor panduan bawaan yang belum ada + perbarui panduan bawaan versi lama, dalam satu batch.
    private fun importSeedData() {
        val (newOnes, outdated) = computeSeedChanges(latestDocs)
        if (newOnes.isEmpty() && outdated.isEmpty()) return

        val batch = firestore.batch()
        val collection = firestore.collection("firstAidConditions")

        for (condition in newOnes) {
            batch.set(
                collection.document(),
                mapOf(
                    "title" to condition.title,
                    "symptoms" to condition.symptoms,
                    "steps" to condition.steps,
                    "builtInVersion" to FirstAidData.SEED_VERSION,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }
        for ((doc, condition) in outdated) {
            batch.update(
                doc.reference,
                mapOf(
                    "symptoms" to condition.symptoms,
                    "steps" to condition.steps,
                    "builtInVersion" to FirstAidData.SEED_VERSION
                )
            )
        }

        btnImportSeed.isEnabled = false
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "${newOnes.size} guides added, ${outdated.size} updated", Toast.LENGTH_SHORT).show()
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
