package com.example.tugasakhirfinal.ForPassenger.FirstAid

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PertolonganPertamaActivity : AppCompatActivity() {

    private lateinit var rvFirstAid: RecyclerView
    private lateinit var layoutEmptyFirstAid: View
    private lateinit var firstAidAdapter: FirstAidAdapter

    private val firestore = FirebaseFirestore.getInstance()
    private var fullList: List<FirstAidCondition> = emptyList()
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_pertolongan_pertama)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        layoutEmptyFirstAid = findViewById(R.id.layoutEmptyFirstAid)
        rvFirstAid = findViewById(R.id.rvFirstAid)
        rvFirstAid.layoutManager = LinearLayoutManager(this)
        firstAidAdapter = FirstAidAdapter(fullList)
        rvFirstAid.adapter = firstAidAdapter

        findViewById<EditText>(R.id.etSearchFirstAid).addTextChangedListener { text ->
            currentQuery = text?.toString() ?: ""
            applyFilter()
        }

        loadFirstAidConditions()
    }

    // Data dikelola Admin lewat Firestore (bukan lagi statis di FirstAidData.kt), supaya bisa
    // ditambah/diedit/dihapus tanpa perlu update aplikasi.
    private fun loadFirstAidConditions() {
        firestore.collection("firstAidConditions")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                fullList = snapshot.documents.map { doc ->
                    FirstAidCondition(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        symptoms = doc.getString("symptoms") ?: "",
                        steps = doc.get("steps") as? List<String> ?: emptyList()
                    )
                }
                applyFilter()
            }
    }

    // Cari berdasarkan nama kondisi ATAU gejala, supaya passenger bisa cari lewat keluhan yang
    // dirasakan (mis. "sesak napas") tanpa harus tahu persis nama kondisi medisnya.
    private fun applyFilter() {
        val filtered = if (currentQuery.isBlank()) {
            fullList
        } else {
            fullList.filter {
                it.title.contains(currentQuery, ignoreCase = true) || it.symptoms.contains(currentQuery, ignoreCase = true)
            }
        }
        firstAidAdapter.updateList(filtered)
        layoutEmptyFirstAid.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvFirstAid.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }
}
