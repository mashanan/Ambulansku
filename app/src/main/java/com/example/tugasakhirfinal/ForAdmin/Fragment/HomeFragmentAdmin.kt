package com.example.tugasakhirfinal.ForAdmin.Fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.ForAdmin.ApproveDriverActivity
import com.example.tugasakhirfinal.ForAdmin.ManageFirstAidActivity
import com.example.tugasakhirfinal.ForAdmin.ManageHospitalActivity
import com.example.tugasakhirfinal.ForAdmin.ManageUsersActivity
import com.example.tugasakhirfinal.databinding.FragmentHomeAdminBinding
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragmentAdmin : Fragment() {

    private var _binding: FragmentHomeAdminBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardManageHospital.setOnClickListener {
            startActivity(Intent(requireContext(), ManageHospitalActivity::class.java))
        }
        binding.cardManageUsers.setOnClickListener {
            startActivity(Intent(requireContext(), ManageUsersActivity::class.java))
        }
        binding.cardApproveDriver.setOnClickListener {
            startActivity(Intent(requireContext(), ApproveDriverActivity::class.java))
        }
        binding.cardManageFirstAid.setOnClickListener {
            startActivity(Intent(requireContext(), ManageFirstAidActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadAdminStats()
    }

    // Ringkasan angka sekali lihat - dibaca sekali tiap kali Home dibuka (bukan snapshot listener),
    // konsisten dengan pola baca sekali-jalan yang sudah dipakai di layar Admin lainnya.
    private fun loadAdminStats() {
        firestore.collection("users").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener

            var activeCount = 0
            var pendingDriverCount = 0
            for (doc in snapshot.documents) {
                val role = doc.getString("role") ?: ""
                if (role == "admin") continue

                val isActive = doc.getBoolean("isActive") ?: true
                if (isActive) activeCount++

                if (role == "driver" && doc.getBoolean("isApproved") != true) pendingDriverCount++
            }
            binding.tvStatUserCount.text = activeCount.toString()
            binding.tvStatPendingDriver.text = pendingDriverCount.toString()
        }

        firestore.collection("hospitals").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            binding.tvStatHospitalCount.text = snapshot.size().toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
