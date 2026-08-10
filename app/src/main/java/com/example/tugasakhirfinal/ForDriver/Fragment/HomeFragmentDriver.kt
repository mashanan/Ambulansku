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
import com.example.tugasakhirfinal.ForDriver.BookingAdapter
import com.example.tugasakhirfinal.ForDriver.Booking
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.FragmentHomeDriverBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration

class HomeFragmentDriver : Fragment() {

    private var _binding: FragmentHomeDriverBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val bookingList = mutableListOf<Booking>()
    private lateinit var bookingAdapter: BookingAdapter
    private var bookingListener: ListenerRegistration? = null

    // Lokasi driver sendiri (dibaca dari users/{uid}.lokasi, sudah diperbarui berkala saat Online
    // oleh DriverHomeActivity), dipakai untuk mengurutkan pesanan masuk dari yang terdekat.
    private var driverLocation: GeoPoint? = null
    private var driverLocationListener: ListenerRegistration? = null

    // Variabel UI dari Activity Induk
    private var switchOnline: SwitchCompat? = null
    private var statusText: TextView? = null

    // Kategori ambulans driver ini (didaftarkan saat kualifikasi) - dipakai memfilter supaya
    // cuma pesanan dengan kategori yang sama yang ditampilkan/diteruskan ke driver ini.
    private var driverAmbulanceCategory: String = "Ambulans Biasa"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeDriverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchOnline = requireActivity().findViewById(R.id.switchOnline)
        statusText = requireActivity().findViewById(R.id.statusText)

        setupRecyclerView()
        listenToDriverStatusAndLocation()
        loadDriverTripStats()
    }

    // Total trip yang sudah diselesaikan driver ini - dibaca sekali saat Home dibuka (bukan
    // listener baru), murni informasional, tidak menyentuh alur booking/status sama sekali.
    private fun loadDriverTripStats() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("bookings")
            .whereEqualTo("driverId", uid)
            .whereEqualTo("status", "completed")
            .get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                binding.tvDriverTripCount.text = snapshot.size().toString()
            }
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

    // 🛠️ PENTING: JANGAN pasang switchOnline.setOnCheckedChangeListener di sini.
    // DriverHomeActivity sudah memasang listener-nya sendiri di switch yang sama untuk
    // menjalankan startLocationUpdates()/updateDriverStatus(). Satu View cuma bisa punya SATU
    // OnCheckedChangeListener aktif — memasang listener kedua di sini akan MENIMPA punya
    // DriverHomeActivity, sehingga lokasi driver tidak pernah terkirim ke Firestore.
    // Makanya status Online/Offline & lokasi driver di sini dibaca langsung dari Firestore
    // (satu-satunya sumber kebenaran, sudah pasti sinkron dengan apa yang ditulis DriverHomeActivity).
    private fun listenToDriverStatusAndLocation() {
        val uid = auth.currentUser?.uid ?: return
        driverLocationListener?.remove()
        driverLocationListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, e ->
                if (_binding == null || e != null) return@addSnapshotListener

                driverLocation = snapshot?.getGeoPoint("lokasi")
                val newCategory = snapshot?.getString("ambulanceCategory") ?: "Ambulans Biasa"
                val categoryChanged = newCategory != driverAmbulanceCategory
                driverAmbulanceCategory = newCategory
                val isOnline = (snapshot?.getString("status") ?: "offline").equals("online", ignoreCase = true)

                if (isOnline && (bookingListener == null || categoryChanged)) {
                    binding.tvEmpty.text = "Searching for ambulance bookings..."
                    listenToBookings()
                } else if (!isOnline && bookingListener != null) {
                    stopListening()
                } else {
                    // Statusnya belum berubah kategori, tapi lokasi bisa saja baru update
                    applyDistanceSortingAndRefresh()
                }
            }
    }

    private fun listenToBookings() {
        bookingListener?.remove()

        bookingListener = db.collection("bookings")
            .whereEqualTo("status", "SEARCHING")
            .whereEqualTo("ambulanceCategory", driverAmbulanceCategory)
            .addSnapshotListener { snapshots, e ->
                if (_binding == null) return@addSnapshotListener
                if (e != null) {
                    Log.e("FIRESTORE_ERROR", e.message.toString())
                    return@addSnapshotListener
                }

                bookingList.clear()
                // Booking SEARCHING yang sudah lewat batas 3 hari (expiresAt) dianggap basi/
                // ditinggalkan passenger - disaring di client supaya tidak nyangkut selamanya
                // di daftar pesanan masuk (tidak ada job/Cloud Function yang membersihkannya).
                val now = Timestamp.now()
                val activeDocs = snapshots?.documents?.filter { doc ->
                    val expiresAt = doc.getTimestamp("expiresAt")
                    expiresAt == null || expiresAt > now
                } ?: emptyList()

                if (activeDocs.isNotEmpty()) {
                    // Urutan awal berdasar createdAt; nanti disempurnakan ke jarak terdekat oleh
                    // applyDistanceSortingAndRefresh() begitu lokasi driver sudah/tetap tersedia.
                    val sortedDocs = activeDocs.sortedBy { it.getTimestamp("createdAt") }

                    for (doc in sortedDocs) {
                        val booking = doc.toObject(Booking::class.java)
                        if (booking != null) {
                            booking.bookingId = doc.id
                            bookingList.add(booking)
                        }
                    }
                    binding.layoutEmpty.visibility = View.GONE
                } else {
                    binding.tvEmpty.text = "No bookings yet..."
                    binding.layoutEmpty.visibility = View.VISIBLE
                }
                applyDistanceSortingAndRefresh()
            }
    }

    // 🚀 ALUR PINTAR: Kalau lokasi driver sudah diketahui, urutkan & isi jarak/estimasi pesanan
    // dari yang PALING DEKAT dengan driver. Dipanggil ulang setiap kali daftar pesanan ATAU lokasi
    // driver berubah, supaya keduanya selalu sinkron (tidak ada yang nyangkut di "-").
    private fun applyDistanceSortingAndRefresh() {
        if (_binding == null) return
        val myLocation = driverLocation
        if (myLocation != null) {
            for (booking in bookingList) {
                booking.pickupLocation?.let { pickup ->
                    val dist = distanceMeters(myLocation, pickup)
                    booking.distanceMeters = dist
                    booking.estimatedMinutes = estimateMinutes(dist)
                }
            }
            bookingList.sortBy { if (it.distanceMeters >= 0) it.distanceMeters else Float.MAX_VALUE }
        }
        bookingAdapter.notifyDataSetChanged()
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, results)
        return results[0]
    }

    // Estimasi kasar waktu tempuh (asumsi kecepatan rata-rata ambulans di kota ~30 km/jam),
    // dipakai di daftar pesanan supaya driver punya gambaran cepat tanpa memanggil Directions API
    // berulang kali untuk setiap pesanan yang masuk.
    private fun estimateMinutes(distanceMeters: Float): Int {
        val averageSpeedKmh = 30.0
        val distanceKm = distanceMeters / 1000.0
        return ((distanceKm / averageSpeedKmh) * 60).toInt().coerceAtLeast(1)
    }

    // Hanya menghentikan listener daftar pesanan. driverLocationListener (status+lokasi) SENGAJA
    // tidak ikut dihentikan di sini karena dialah yang mendeteksi ketika driver kembali Online.
    private fun stopListening() {
        bookingListener?.remove()
        bookingListener = null

        bookingList.clear()
        bookingAdapter.notifyDataSetChanged()

        binding.tvEmpty.text = "You're currently Offline/Busy. Turn on Online mode to search for bookings."
        binding.layoutEmpty.visibility = View.VISIBLE
    }

    private fun terimaPesanan(booking: Booking) {
        val driverUid = auth.currentUser?.uid ?: return
        if (booking.bookingId.isEmpty()) return

        val progressDialog = ProgressDialog(requireContext())
        progressDialog.setMessage("Accepting booking...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        val bookingRef = db.collection("bookings").document(booking.bookingId)

        // Transaksi supaya cuma driver PERTAMA yang berhasil kalau ada 2 driver menekan "Terima"
        // hampir bersamaan pada booking yang sama - baca ulang status di detik terakhir di dalam
        // transaksi, tolak kalau ternyata sudah bukan SEARCHING lagi (sudah diambil driver lain
        // atau dibatalkan), bukan sekadar menimpa .update() begitu saja.
        db.runTransaction { transaction ->
            val snapshot = transaction.get(bookingRef)
            if (snapshot.getString("status") != "SEARCHING") {
                throw FirebaseFirestoreException(
                    "Booking is no longer available",
                    FirebaseFirestoreException.Code.ABORTED
                )
            }
            transaction.update(bookingRef, mapOf("status" to "accepted", "driverId" to driverUid))
        }
            .addOnSuccessListener {
                if (isAdded) {
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Booking accepted!", Toast.LENGTH_SHORT).show()

                    // 🚀 ALUR PINTAR: Otomatis matikan online karena sedang bertugas
                    switchOnline?.isChecked = false
                    statusText?.text = "Status: Picking Up Patient"

                    navigateToDriveTab()
                }
            }
            .addOnFailureListener { e ->
                if (isAdded) {
                    progressDialog.dismiss()
                    val message = if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.ABORTED) {
                        "Booking has already been taken by another driver."
                    } else {
                        "Failed: ${e.message}"
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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
        stopListening()
        driverLocationListener?.remove()
        driverLocationListener = null
        _binding = null
    }
}