package com.example.tugasakhirfinal.ForPassanger.Fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.tugasakhirfinal.ForPassanger.EditProfileFragment.ActivityEditEmail
import com.example.tugasakhirfinal.ForPassanger.EditProfileFragment.ActivityEditNama
import com.example.tugasakhirfinal.ForPassanger.EditProfileFragment.ActivityEditNoTlp
import com.example.tugasakhirfinal.ForPassanger.EditProfileFragment.ActivityEditFotoProfile
import com.example.tugasakhirfinal.ForPassanger.EditProfileFragment.ActivityEditAlamat
import com.example.tugasakhirfinal.RoleSelectionActivity
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var firebaseAuth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    // Listener untuk pembaruan alamat utama secara real-time
    private var primaryAddressListener: ListenerRegistration? = null

    private val sharedPrefs by lazy {
        requireActivity().getSharedPreferences("user_cache", Context.MODE_PRIVATE)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firebaseAuth = FirebaseAuth.getInstance()

        setupListeners()
        setupLogoutButton()

        binding.profileImage.setOnClickListener {
            startActivity(Intent(requireContext(), ActivityEditFotoProfile::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfileFromFirestore()
        loadPrimaryAddressFromFirestore()
    }

    private fun loadProfileFromFirestore() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (_binding == null) return

        // 1. Load dari Cache (Instan)
        binding.textNama.text = sharedPrefs.getString("name_$uid", "Klik untuk atur nama")
        binding.noTlp.text = sharedPrefs.getString("phone_$uid", "Klik untuk atur telepon")
        binding.email.text = sharedPrefs.getString("email_$uid", "-")
        binding.alamat.text = sharedPrefs.getString("primary_address_label_$uid", "Pilih Alamat Utama")
        loadProfileImage(sharedPrefs.getString("img_$uid", null))

        // 2. Load Profil dasar (Nama, No Tlp, Email, Foto) dari Firestore
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (_binding != null && isAdded) {
                    val name = doc.getString("nama") ?: "Klik untuk atur nama"
                    val phone = doc.getString("phoneNumber") ?: "Klik untuk atur telepon"
                    val email = doc.getString("email") ?: "-"
                    val imageUrl = doc.getString("profileImageUrl")

                    // Update UI Profil
                    binding.textNama.text = name
                    binding.noTlp.text = phone
                    binding.email.text = email
                    loadProfileImage(imageUrl)

                    // Simpan ke Cache
                    sharedPrefs.edit().apply {
                        putString("name_$uid", name)
                        putString("phone_$uid", phone)
                        putString("email_$uid", email)
                        putString("img_$uid", imageUrl)
                        apply()
                    }
                }
            }
    }

    /**
     * Membaca Alamat Utama (isPrimary = true) dari sub-koleksi addresses secara Real-Time
     */
    private fun loadPrimaryAddressFromFirestore() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        if (_binding == null) return

        // Hentikan listener lama jika ada sebelum membuat baru
        primaryAddressListener?.remove()

        primaryAddressListener = firestore.collection("users").document(uid)
            .collection("addresses")
            .whereEqualTo("isPrimary", true)
            .limit(1) // Mengambil 1 alamat utama saja
            .addSnapshotListener { snapshot, error ->
                if (_binding == null || !isAdded) return@addSnapshotListener

                if (error != null) {
                    binding.alamat.text = "Atur Alamat Utama"
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    // Hanya mengambil label alamat saja (tanpa fullAddress)
                    val label = doc.getString("label") ?: "Alamat Utama"

                    // Tampilkan hanya nama labelnya agar tampilan Profile ringkas
                    binding.alamat.text = label

                    // Simpan Nama Label Utama ke Cache
                    sharedPrefs.edit().putString("primary_address_label_$uid", label).apply()
                } else {
                    // Jika belum ada alamat yang di-set sebagai Utama
                    binding.alamat.text = "Klik untuk memilih Alamat Utama"
                    sharedPrefs.edit().putString("primary_address_label_$uid", "Klik untuk memilih Alamat Utama").apply()
                }
            }
    }

    private fun loadProfileImage(url: String?) {
        if (_binding == null) return
        Glide.with(this)
            .load(url ?: R.drawable.foto_profil)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.foto_profil)
            .circleCrop()
            .into(binding.profileImage)
    }

    private fun setupListeners() {
        binding.frameNama.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditNama::class.java)
            intent.putExtra("current_name", binding.textNama.text.toString())
            startActivity(intent)
        }
        binding.frameEmail.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditEmail::class.java)
            intent.putExtra("current_email", binding.email.text.toString())
            startActivity(intent)
        }
        binding.frameNoTlp.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditNoTlp::class.java)
            intent.putExtra("current_no_tlp", binding.noTlp.text.toString())
            startActivity(intent)
        }
        // Listener untuk Membuka Daftar Alamat
        binding.frameAlamat.setOnClickListener {
            val intent = Intent(requireContext(), ActivityEditAlamat::class.java)
            startActivity(intent)
        }
    }

    private fun setupLogoutButton() {
        binding.logoutButton.setOnClickListener {
            firebaseAuth.signOut()
            sharedPrefs.edit().clear().apply()
            startActivity(Intent(requireContext(), RoleSelectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
    }

    override fun onStop() {
        super.onStop()
        // Hentikan listener Firestore saat fragment tidak aktif untuk menghemat memory
        primaryAddressListener?.remove()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}