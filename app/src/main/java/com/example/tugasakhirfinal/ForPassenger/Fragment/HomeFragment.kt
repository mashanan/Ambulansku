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
import com.example.tugasakhirfinal.ForPassenger.DriveFragment.ActivityDrive
import com.example.tugasakhirfinal.ForPassenger.FirstAid.PertolonganPertamaActivity
import com.example.tugasakhirfinal.ForPassenger.DriveFragment.HospitalListActivity
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

        // REORDER_TO_FRONT: kalau layar peta masih hidup di belakang (pencarian driver sedang
        // berjalan), bawa layar itu ke depan - jangan buat instance kedua untuk booking yang sama.
        binding.cardMiniRight.setOnClickListener { openDriveScreen() }
        binding.cardActiveBooking.setOnClickListener { openDriveScreen() }

        // Sebelumnya kartu ini belum ada listener sama sekali (tidak berfungsi saat ditekan) -
        // disamakan dengan ActivityDriveFragment (fragment Layanan) yang sudah benar.
        binding.cardMiniLeft.setOnClickListener {
            val intent = Intent(requireContext(), HospitalListActivity::class.java)
            startActivity(intent)
        }

        binding.cardMenu.setOnClickListener {
            val intent = Intent(requireContext(), PertolonganPertamaActivity::class.java)
            startActivity(intent)
        }

        binding.textView2.text = getGreeting()

        return binding.root
    }

    // Sapaan sesuai jam device saat app dibuka, menggantikan teks statis "Welcome to app".
    private fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..10 -> "Good Morning,"
            in 11..14 -> "Good Afternoon,"
            in 15..17 -> "Good Evening,"
            else -> "Good Night,"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadUserData()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
        loadActiveBooking()
    }

    private fun openDriveScreen() {
        startActivity(
            Intent(requireContext(), ActivityDrive::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    // Kartu "booking sedang berjalan" di atas Home: muncul saat passenger punya booking
    // SEARCHING/accepted/picked_up (kriteria sama dengan ActivityDrive.checkForActiveBooking),
    // supaya setelah kembali ke Home saat mencari driver, user tetap tahu & bisa balik ke peta.
    private fun loadActiveBooking() {
        val uid = firebaseAuth.currentUser?.uid ?: return
        firestore.collection("bookings")
            .whereEqualTo("passengerId", uid)
            .whereIn("status", listOf("SEARCHING", "accepted", "picked_up"))
            .get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null || !isAdded) return@addOnSuccessListener
                val now = com.google.firebase.Timestamp.now()
                val active = snapshot.documents
                    .filterNot { doc ->
                        doc.getString("status") == "SEARCHING" &&
                            doc.getTimestamp("expiresAt")?.let { it <= now } == true
                    }
                    .sortedByDescending { it.getTimestamp("createdAt") }
                    .firstOrNull()

                if (active == null) {
                    binding.cardActiveBooking.visibility = View.GONE
                    return@addOnSuccessListener
                }
                val hospital = active.getString("hospitalName") ?: "hospital"
                binding.tvActiveBookingTitle.text = when (active.getString("status")) {
                    "accepted" -> "Ambulance is on the way"
                    "picked_up" -> "On the way to $hospital"
                    else -> "Searching for a driver…"
                }
                binding.tvActiveBookingSubtitle.text = "To $hospital · Tap to view"
                binding.cardActiveBooking.visibility = View.VISIBLE
            }
            .addOnFailureListener {
                if (_binding != null) binding.cardActiveBooking.visibility = View.GONE
            }
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
        // v2: skala mengikuti TextAppearance.V2.H1 (22sp) di header coral
        val textSize = when {
            name.length > 20 -> 18f
            name.length > 18 -> 20f
            else -> 22f
        }
        binding.textView3.textSize = textSize
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}