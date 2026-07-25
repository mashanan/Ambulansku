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

class ActivityDriveFragment : Fragment() {

    private var _binding: FragmentDriveActivityMainBinding? = null
    private val binding get() = _binding!!

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}