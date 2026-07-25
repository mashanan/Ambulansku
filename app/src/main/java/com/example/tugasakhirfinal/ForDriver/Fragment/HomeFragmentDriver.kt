package com.example.tugasakhirfinal.ForDriver.Fragment

import android.app.ProgressDialog
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tugasakhirfinal.Adapter.BookingAdapter
import com.example.tugasakhirfinal.Model.Booking
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.FragmentHomeDriverBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeFragmentDriver : Fragment() {

    private var _binding: FragmentHomeDriverBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val bookingList = mutableListOf<Booking>()
    private lateinit var bookingAdapter: BookingAdapter
    private var bookingListener: ListenerRegistration? = null

    // Variabel UI dari Activity Induk
    private var switchOnline: SwitchCompat? = null
    private var statusText: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeDriverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchOnline = requireActivity().findViewById(R.id.switchOnline)
        statusText = requireActivity().findViewById(R.id.statusText)

        setupRecyclerView()
        setupToggleStatus()
    }

    private fun setupRecyclerView() {
        bookingAdapter = BookingAdapter(bookingList) { booking ->
            terimaPesanan(booking)
        }

        binding.rvOrders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bookingAdapter
            itemAnimator = DefaultItemAnimator()
        }
    }

    private fun setupToggleStatus() {
        if (switchOnline == null || statusText == null) return

        // 💡 ALUR PINTAR: Baca posisi switch saat ini (Jangan paksa false)
        val isCurrentlyOnline = switchOnline?.isChecked == true

        if (isCurrentlyOnline) {
            statusText?.text = "Status: Online"
            binding.tvEmpty.text = "Mencari pesanan ambulans..."
            listenToBookings()
        } else {
            statusText?.text = "Status: Offline/Sibuk"
            stopListening()
        }

        // Listener saat switch digeser manual oleh driver
        switchOnline?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                statusText?.text = "Status: Online"
                binding.tvEmpty.text = "Mencari pesanan ambulans..."
                listenToBookings()
                Toast.makeText(requireContext(), "Anda sedang Online", Toast.LENGTH_SHORT).show()
            } else {
                statusText?.text = "Status: Offline"
                stopListening()
                Toast.makeText(requireContext(), "Anda sedang Offline", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun listenToBookings() {
        bookingListener?.remove()

        bookingListener = db.collection("bookings")
            .whereEqualTo("status", "SEARCHING")
            .addSnapshotListener { snapshots, e ->
                if (_binding == null) return@addSnapshotListener
                if (e != null) {
                    Log.e("FIRESTORE_ERROR", e.message.toString())
                    return@addSnapshotListener
                }

                bookingList.clear()
                if (snapshots != null && !snapshots.isEmpty) {

                    // 🚀 ALUR PINTAR: URUTKAN BERDASARKAN WAKTU PESAN (createdAt) TERLAMA
                    // sortedBy mengurutkan dari waktu yang paling kecil/lama ke yang terbaru.
                    val sortedDocs = snapshots.documents.sortedBy {
                        it.getTimestamp("createdAt")
                    }

                    for (doc in sortedDocs) {
                        val booking = doc.toObject(Booking::class.java)
                        if (booking != null) {
                            booking.bookingId = doc.id
                            bookingList.add(booking)
                        }
                    }
                    binding.tvEmpty.visibility = View.GONE
                } else {
                    binding.tvEmpty.text = "Belum ada pesanan masuk..."
                    binding.tvEmpty.visibility = View.VISIBLE
                }
                bookingAdapter.notifyDataSetChanged()
            }
    }

    private fun stopListening() {
        bookingListener?.remove()
        bookingListener = null

        bookingList.clear()
        bookingAdapter.notifyDataSetChanged()

        binding.tvEmpty.text = "Anda sedang Offline/Sibuk. Aktifkan mode Online untuk mencari pesanan."
        binding.tvEmpty.visibility = View.VISIBLE
    }

    private fun terimaPesanan(booking: Booking) {
        val driverUid = auth.currentUser?.uid ?: return
        if (booking.bookingId.isEmpty()) return

        val progressDialog = ProgressDialog(requireContext())
        progressDialog.setMessage("Menerima pesanan...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        db.collection("bookings").document(booking.bookingId)
            .update(
                "status", "accepted",
                "driverId", driverUid
            )
            .addOnSuccessListener {
                if (isAdded) {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Pesanan diterima!", Toast.LENGTH_SHORT).show()

                    // 🚀 ALUR PINTAR: Otomatis matikan online karena sedang bertugas
                    switchOnline?.isChecked = false
                    statusText?.text = "Status: Menjemput Pasien"

                    navigateToDriveTab()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun navigateToDriveTab() {
        try {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNav?.selectedItemId = R.id.nav_drive
        } catch (e: Exception) {
            Log.e("NAV_ERROR", "Crash navigasi: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        switchOnline?.setOnCheckedChangeListener(null)
        stopListening()
        _binding = null
    }
}