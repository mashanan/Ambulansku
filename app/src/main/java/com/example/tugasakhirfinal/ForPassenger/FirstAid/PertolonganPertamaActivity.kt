package com.example.tugasakhirfinal.ForPassenger.FirstAid

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R

class PertolonganPertamaActivity : AppCompatActivity() {

    private lateinit var rvFirstAid: RecyclerView
    private lateinit var tvEmptyFirstAid: TextView
    private lateinit var firstAidAdapter: FirstAidAdapter
    private val fullList = FirstAidData.getList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_pertolongan_pertama)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        tvEmptyFirstAid = findViewById(R.id.tvEmptyFirstAid)
        rvFirstAid = findViewById(R.id.rvFirstAid)
        rvFirstAid.layoutManager = LinearLayoutManager(this)
        firstAidAdapter = FirstAidAdapter(fullList)
        rvFirstAid.adapter = firstAidAdapter

        findViewById<EditText>(R.id.etSearchFirstAid).addTextChangedListener { text ->
            filterList(text?.toString() ?: "")
        }
    }

    // Cari berdasarkan nama kondisi ATAU gejala, supaya passenger bisa cari lewat keluhan yang
    // dirasakan (mis. "sesak napas") tanpa harus tahu persis nama kondisi medisnya.
    private fun filterList(query: String) {
        val filtered = if (query.isBlank()) {
            fullList
        } else {
            fullList.filter {
                it.title.contains(query, ignoreCase = true) || it.symptoms.contains(query, ignoreCase = true)
            }
        }
        firstAidAdapter.updateList(filtered)
        tvEmptyFirstAid.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        rvFirstAid.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }
}
