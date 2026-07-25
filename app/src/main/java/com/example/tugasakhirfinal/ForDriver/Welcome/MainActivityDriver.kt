package com.example.tugasakhirfinal.ForDriver.Welcome

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tugasakhirfinal.ForDriver.SignUpActivityDriver
import com.example.tugasakhirfinal.databinding.ActivityMainDriverBinding

class MainActivityDriver : AppCompatActivity() {

    private lateinit var binding: ActivityMainDriverBinding
    private var selectedRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainDriverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // Menangkap role yang dikirim dari RoleSelectionActivity
        selectedRole = intent.getStringExtra("USER_ROLE") ?: "driver"

        // Tombol menuju halaman Login khusus Driver
        binding.buttonsignindriver.setOnClickListener {
            val intent = Intent(this, SignInActivityDriver::class.java).apply {
                putExtra("USER_ROLE", selectedRole)
            }
            startActivity(intent)
        }

        // Tombol menuju halaman Register khusus Driver
        binding.buttonsignupdriver.setOnClickListener {
            val intent = Intent(this, SignUpActivityDriver::class.java).apply {
                putExtra("USER_ROLE", selectedRole)
            }
            startActivity(intent)
        }
    }
}