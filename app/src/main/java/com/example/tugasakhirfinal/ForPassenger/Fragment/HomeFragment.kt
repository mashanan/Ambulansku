package com.example.tugasakhirfinal.ForPassenger.Fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.tugasakhirfinal.ForPassenger.ActivityDrive
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    // Cache khusus untuk data Home
    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("user_cache", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        firebaseAuth = FirebaseAuth.getInstance()

        binding.cardMiniRight.setOnClickListener {
            val intent = Intent(requireContext(), ActivityDrive::class.java)
            startActivity(intent)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    private fun loadUserData() {
        val uid = firebaseAuth.currentUser?.uid ?: return

        // 1. Tampilkan dari Cache (Instan)
        val cachedName = sharedPrefs.getString("name_$uid", "User")
        val cachedImg = sharedPrefs.getString("img_$uid", null)

        binding.textView3.text = cachedName
        loadProfileImage(cachedImg)
        adjustTextSize(cachedName ?: "User")

        // 2. Ambil dari Firestore (Background Update)
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (_binding != null && isAdded) {
                    val name = doc.getString("nama") ?: "User"
                    val imageUrl = doc.getString("profileImageUrl")

                    // Update UI jika ada perubahan data di server
                    binding.textView3.text = name
                    loadProfileImage(imageUrl)
                    adjustTextSize(name)

                    // 3. Simpan data terbaru ke Cache
                    sharedPrefs.edit().apply {
                        putString("name_$uid", name)
                        putString("img_$uid", imageUrl)
                        apply()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Firestore Error: ${e.message}")
            }
    }

    private fun loadProfileImage(url: String?) {
        if (_binding == null) return
        Glide.with(this)
            .load(url ?: R.drawable.foto_profil)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.foto_profil)
            .error(R.drawable.foto_profil)
            .circleCrop()
            .into(binding.profileImage)
    }

    private fun adjustTextSize(name: String) {
        val textSize = when {
            name.length > 20 -> 21f
            name.length > 18 -> 23f
            else -> 25f
        }
        binding.textView3.textSize = textSize
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}