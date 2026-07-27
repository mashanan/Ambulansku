package com.example.tugasakhirfinal.ForPassenger.Fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.ForPassenger.ActivityDrive
import com.example.tugasakhirfinal.ForPassenger.HospitalListActivity
import com.example.tugasakhirfinal.databinding.FragmentDriveActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ActivityDriveFragment : Fragment() {

    private var _binding: FragmentDriveActivityMainBinding? = null
    private val binding get() = _binding!!

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDriveActivityMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Tombol ke Daftar Rumah Sakit (Bisa untuk melihat alamat RS terdekat)
        binding.cardMiniLeft.setOnClickListener {
            val intent = Intent(requireContext(), HospitalListActivity::class.java)
            startActivity(intent)
        }

        // Tombol Utama: Membuka Google Maps untuk Panggil Ambulans
        binding.cardMiniRight.setOnClickListener {
            val intent = Intent(requireContext(), ActivityDrive::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        loadLastTrip()
    }

    // Kartu "Trip Terakhir Anda" - dipindahkan dari Home ke sini. Hanya tampil kalau passenger
    // sudah pernah punya trip completed. Diurutkan di client (bukan .orderBy di query) supaya
    // tidak butuh composite index Firestore, pola yang sama dipakai di seluruh app ini.
    private fun loadLastTrip() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("bookings")
            .whereEqualTo("passengerId", uid)
            .whereEqualTo("status", "completed")
            .get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener

                val lastDoc = snapshot.documents
                    .sortedByDescending { it.getTimestamp("completedAt") ?: it.getTimestamp("createdAt") }
                    .firstOrNull()

                if (lastDoc == null) {
                    binding.cardLastTrip.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val hospitalName = lastDoc.getString("hospitalName") ?: "Rumah Sakit"
                val completedAt = lastDoc.getTimestamp("completedAt") ?: lastDoc.getTimestamp("createdAt")
                val dateText = completedAt?.let {
                    java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID")).format(it.toDate())
                } ?: "-"

                binding.tvLastTripHospital.text = hospitalName
                binding.tvLastTripDate.text = dateText
                binding.cardLastTrip.visibility = View.VISIBLE
            }
            .addOnFailureListener {
                if (_binding != null) binding.cardLastTrip.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}