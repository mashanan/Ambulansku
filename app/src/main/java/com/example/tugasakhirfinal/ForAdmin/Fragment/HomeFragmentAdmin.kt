package com.example.tugasakhirfinal.ForAdmin.Fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.tugasakhirfinal.ForAdmin.ApproveDriverActivity
import com.example.tugasakhirfinal.ForAdmin.ManageHospitalActivity
import com.example.tugasakhirfinal.ForAdmin.ManageUsersActivity
import com.example.tugasakhirfinal.databinding.FragmentHomeAdminBinding

class HomeFragmentAdmin : Fragment() {

    private var _binding: FragmentHomeAdminBinding? = null
    private val binding get() = _binding!!

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
