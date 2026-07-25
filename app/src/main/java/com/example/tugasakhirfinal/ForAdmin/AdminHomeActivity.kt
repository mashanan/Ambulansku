package com.example.tugasakhirfinal.ForAdmin

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.ForAdmin.Fragment.HomeFragmentAdmin
import com.example.tugasakhirfinal.ForAdmin.Fragment.ProfileFragmentAdmin
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.ActivityAdminHomeBinding

class AdminHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        if (savedInstanceState == null) {
            loadFragment(HomeFragmentAdmin())
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragmentAdmin())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragmentAdmin())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
