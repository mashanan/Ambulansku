package com.example.tugasakhirfinal.ForPassenger.FirstAid

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R

class PertolonganPertamaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_pertolongan_pertama)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val rvFirstAid = findViewById<RecyclerView>(R.id.rvFirstAid)
        rvFirstAid.layoutManager = LinearLayoutManager(this)
        rvFirstAid.adapter = FirstAidAdapter(FirstAidData.getList())
    }
}
