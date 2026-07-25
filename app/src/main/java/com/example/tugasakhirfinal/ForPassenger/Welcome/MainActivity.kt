package com.example.tugasakhirfinal.ForPassenger.Welcome

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import com.example.tugasakhirfinal.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_passenger)

        // Tombol untuk navigasi ke SignInActivity
        val redbuttonsignin = findViewById<Button>(R.id.buttonsigninpassenger)
        redbuttonsignin.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
        }

        // Tombol untuk navigasi ke SignUpActivity
        val buttonsignup = findViewById<Button>(R.id.buttonsignuppassenger)
        buttonsignup.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        // Menghilangkan Action Bar
        supportActionBar?.hide()
    }
}