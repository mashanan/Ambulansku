package com.example.tugasakhirfinal.ForDriver.Fragment

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.FragmentDriveDriverBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ActivityDriveFragmentDriver : Fragment(), OnMapReadyCallback {

    companion object {
        private const val CHAT_CHANNEL_ID = "chat_message_channel"
        private const val TRIP_CHANNEL_ID = "trip_status_channel"
        private const val ARRIVAL_THRESHOLD_METERS = 100f
    }

    private var _binding: FragmentDriveDriverBinding? = null
    private val binding get() = _binding!!
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var currentBookingId: String? = null
    private var roadOverlay: Polyline? = null
    private var driverMarker: Marker? = null
    private var targetMarker: Marker? = null
    private var targetLocation: LatLng? = null
    private var currentMode = "jemput"
    private var isFirstLocation = true
    private var lastKnownDriverPosition: LatLng? = null

    private var isRouteZoomed = false
    private var hasNotifiedArrival = false

    private var lastFetchedPassengerId: String? = null
    private var passengerPhoneNumber: String? = null

    // 🚀 Listener untuk Notifikasi Pesan Belum Dibaca
    private var unreadListener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDriveDriverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        binding.btnSelesai.setOnClickListener {
            if (currentMode == "jemput") konfirmasiJemput() else selesaikanTugas()
        }

        binding.btnCallPassenger.setOnClickListener {
            val phone = passengerPhoneNumber
            if (!phone.isNullOrBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(requireContext(), "Nomor telepon penumpang belum tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChatPassenger?.visibility = View.GONE
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = false

        setupLocationUpdates()
        fetchActiveBooking()
    }

    private fun getResizedBitmapDescriptor(drawableId: Int, sizePx: Int): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(requireContext(), drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun setupLocationUpdates() {
        val request = LocationRequest.create().apply {
            interval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (_binding == null) return
                val loc = result.lastLocation ?: return
                val currentPos = LatLng(loc.latitude, loc.longitude)
                lastKnownDriverPosition = currentPos

                auth.currentUser?.uid?.let { uid ->
                    db.collection("users").document(uid)
                        .update("currentLocation", GeoPoint(loc.latitude, loc.longitude))
                }

                if (driverMarker == null) {
                    driverMarker = mMap.addMarker(MarkerOptions()
                        .position(currentPos)
                        .icon(getResizedBitmapDescriptor(R.drawable.ic_small_car, 85))
                        .anchor(0.5f, 0.5f))
                } else {
                    driverMarker?.position = currentPos
                }

                if (isFirstLocation) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPos, 17f))
                    isFirstLocation = false
                }

                targetLocation?.let { target ->
                    if (!isRouteZoomed) {
                        drawRouteWithDirections(currentPos, target, currentMode)
                    } else {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(currentPos.latitude, currentPos.longitude, target.latitude, target.longitude, results)
                        val statusStr = if (currentMode == "jemput") "Menjemput Pasien" else "Menuju RS"
                        binding.tvCurrentStatus.text = statusStr
                        binding.tvEstimationInfo.text = "Sisa Jarak: ${String.format("%.1f", results[0]/1000)} km"

                        // 🚀 Kamera tetap disesuaikan tiap update lokasi (bukan cuma sekali di awal),
                        // supaya driver & tujuan selalu terlihat utuh di peta walau jaraknya berubah
                        // seiring driver bergerak. Directions API TIDAK dipanggil ulang di sini (hemat kuota).
                        fitCameraToRoute(currentPos, target)

                        checkArrivalProximity(results[0])
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, android.os.Looper.getMainLooper())
        }
    }

    private fun fetchActiveBooking() {
        val driverId = auth.currentUser?.uid ?: return
        db.collection("bookings")
            .whereEqualTo("driverId", driverId)
            .whereIn("status", listOf("accepted", "picked_up"))
            .addSnapshotListener { snapshot, e ->
                if (_binding == null || e != null) return@addSnapshotListener

                if (snapshot == null || snapshot.isEmpty) {
                    resetToStandbyMode()
                    return@addSnapshotListener
                }

                val doc = snapshot.documents[0]
                currentBookingId = doc.id
                val status = doc.getString("status")

                setupChatButton(doc.id)

                val passengerId = doc.getString("passengerId")
                if (!passengerId.isNullOrEmpty() && passengerId != lastFetchedPassengerId) {
                    lastFetchedPassengerId = passengerId
                    fetchPassengerProfile(passengerId, doc.getString("passengerName"))
                }

                if (status == "accepted") {
                    if (currentMode != "jemput") {
                        isRouteZoomed = false
                        hasNotifiedArrival = false
                    }
                    currentMode = "jemput"

                    val geo = doc.getGeoPoint("pickupLocation")
                    geo?.let {
                        targetLocation = LatLng(it.latitude, it.longitude)
                        binding.btnSelesai.text = "Konfirmasi Jemput (Sampai di Pasien)"
                        updateTargetMarker(targetLocation!!, "pasien")
                    }
                } else if (status == "picked_up") {
                    if (currentMode != "antar") isRouteZoomed = false
                    currentMode = "antar"

                    val lat = doc.getDouble("hospitalLat") ?: 0.0
                    val lng = doc.getDouble("hospitalLng") ?: 0.0
                    if (lat != 0.0 && lng != 0.0) {
                        targetLocation = LatLng(lat, lng)
                        binding.btnSelesai.text = "Selesaikan Perjalanan (Sampai di RS)"
                        updateTargetMarker(targetLocation!!, "rs")
                    }
                }

                lastKnownDriverPosition?.let { pos ->
                    targetLocation?.let { target ->
                        if (!isRouteZoomed) drawRouteWithDirections(pos, target, currentMode)
                    }
                }
            }
    }

    // Ambil sekali (bukan tiap update) profil penumpang aktif, supaya driver bisa konfirmasi
    // identitas penumpang saat bertemu (mirip Gojek/Uber). Nama diambil dari bookingData
    // (sudah tersimpan di sana), foto & telepon diambil dari dokumen users/{passengerId}.
    private fun fetchPassengerProfile(passengerId: String, fallbackName: String?) {
        if (_binding == null) return
        binding.layoutPassengerInfo.visibility = View.VISIBLE
        binding.tvPassengerName.text = fallbackName ?: "Penumpang"

        db.collection("users").document(passengerId).get()
            .addOnSuccessListener { doc ->
                if (_binding == null || doc == null || !doc.exists()) return@addOnSuccessListener

                binding.tvPassengerName.text = doc.getString("nama") ?: fallbackName ?: "Penumpang"
                val phone = doc.getString("phoneNumber")
                binding.tvPassengerPhone.text = if (phone.isNullOrBlank()) "-" else phone
                passengerPhoneNumber = phone

                Glide.with(this)
                    .load(doc.getString("profileImageUrl")?.ifEmpty { null } ?: R.drawable.foto_profil)
                    .placeholder(R.drawable.foto_profil)
                    .circleCrop()
                    .into(binding.ivPassengerPhoto)
            }
    }

    private fun updateTargetMarker(pos: LatLng, type: String) {
        if (_binding == null) return
        binding.btnSelesai.visibility = View.VISIBLE

        targetMarker?.remove()

        val iconRes = if (type == "rs") R.drawable.ic_hospital else R.drawable.ic_person_marker
        val titleStr = if (type == "rs") "Rumah Sakit Tujuan" else "Lokasi Penjemputan"

        targetMarker = mMap.addMarker(MarkerOptions()
            .position(pos)
            .title(titleStr)
            .icon(getResizedBitmapDescriptor(iconRes, 90)))
    }

    private fun drawRouteWithDirections(start: LatLng, end: LatLng, mode: String) {
        val apiKey = getString(R.string.google_maps_key)
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${start.latitude},${start.longitude}" +
                "&destination=${end.latitude},${end.longitude}" +
                "&mode=driving&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            var durationText = ""
            var distanceText = ""
            var path: List<LatLng> = emptyList()
            var isApiSuccess = false

            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "GET"
                }

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    if (jsonResponse.getString("status") == "OK") {
                        isApiSuccess = true
                        val routes = jsonResponse.getJSONArray("routes")
                        if (routes.length() > 0) {
                            val route = routes.getJSONObject(0)
                            val overviewPolyline = route.getJSONObject("overview_polyline").getString("points")
                            val legs = route.getJSONArray("legs").getJSONObject(0)

                            durationText = legs.getJSONObject("duration").getString("text")
                            distanceText = legs.getJSONObject("distance").getString("text")
                            path = decodePolyline(overviewPolyline)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DRIVER_MAPS", "Directions API Error: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                roadOverlay?.remove()

                val lineColor = if (mode == "jemput") Color.RED else Color.BLUE

                if (isApiSuccess && path.isNotEmpty()) {
                    roadOverlay = mMap.addPolyline(
                        PolylineOptions().addAll(path).width(14f).color(lineColor)
                            .jointType(JointType.ROUND).startCap(RoundCap()).endCap(RoundCap())
                    )

                    val statusStr = if (mode == "jemput") "Menjemput Pasien" else "Menuju RS"
                    binding.tvCurrentStatus.text = statusStr
                    binding.tvEstimationInfo.text = "Estimasi: $durationText ($distanceText)"

                    // isRouteZoomed cuma menahan supaya Directions API tidak dipanggil ulang tiap 3 detik
                    // (boros kuota) — kamera sendiri tetap disesuaikan tiap update lewat fitCameraToRoute()
                    // di setupLocationUpdates(), bukan cuma sekali di sini.
                    isRouteZoomed = true
                    fitCameraToRoute(start, end, path)
                } else {
                    roadOverlay = mMap.addPolyline(PolylineOptions().add(start, end).width(10f).color(lineColor).pattern(listOf(Dash(20f), Gap(10f))))
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
                    val statusStr = if (mode == "jemput") "Menjemput Pasien" else "Menuju RS"
                    binding.tvCurrentStatus.text = statusStr
                    binding.tvEstimationInfo.text = "Sisa Jarak: ${String.format("%.1f", results[0]/1000)} km"
                    fitCameraToRoute(start, end)
                }
            }
        }
    }

    // Sesuaikan kamera supaya kedua titik (dan rute di antaranya, kalau ada) selalu terlihat utuh,
    // bukan cuma sekali di awal. Dipanggil setiap ada update lokasi baru, bukan hanya saat rute
    // pertama kali digambar via Directions API.
    private fun fitCameraToRoute(start: LatLng, end: LatLng, path: List<LatLng> = emptyList()) {
        if (_binding == null) return
        try {
            val boundsBuilder = LatLngBounds.Builder().include(start).include(end)
            for (point in path) boundsBuilder.include(point)
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150))
        } catch (e: Exception) {
            Log.e("ZOOM_ERROR", "Error bounds maps: ${e.message}")
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0; var lng = 0
        while (index < len) {
            var b: Int; var shift = 0; var result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1; lat += dlat
            shift = 0; result = 0
            do { b = encoded[index++].code - 63; result = result or (b and 0x1f shl shift); shift += 5 } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1; lng += dlng
            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    // 🚀 Fungsi untuk menghitung badge notifikasi + tampilkan notifikasi gaya WhatsApp
    private fun listenUnreadMessages(bookingId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        unreadListener?.remove()

        unreadListener = db.collection("bookings").document(bookingId)
            .collection("messages")
            .whereEqualTo("isRead", false) // Hanya filter pesan yang belum dibaca
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener
                if (_binding == null) return@addSnapshotListener

                // Diurutkan di client (bukan .orderBy di query) supaya tidak butuh composite index Firestore.
                val unreadFromPassenger = snapshot.documents
                    .filter { it.getString("senderId") != currentUserId }
                    .sortedBy { it.getTimestamp("timestamp") }

                if (unreadFromPassenger.isNotEmpty()) {
                    binding.tvUnreadBadge.text = unreadFromPassenger.size.toString()
                    binding.tvUnreadBadge.visibility = View.VISIBLE
                    showChatNotification(bookingId, unreadFromPassenger)
                } else {
                    binding.tvUnreadBadge.visibility = View.GONE
                    cancelChatNotification(bookingId)
                }
            }
    }

    // Notifikasi gaya WhatsApp: pesan-pesan yang belum dibaca ditumpuk (terbaru di bawah),
    // pesan panjang dipotong. Tidak ditampilkan kalau driver memang sedang buka chat ini.
    private fun showChatNotification(bookingId: String, unreadMessages: List<DocumentSnapshot>) {
        if (_binding == null) return
        if (com.example.tugasakhirfinal.ForDriver.Chat.ChatDriverActivity.openBookingId == bookingId) return
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        createChatNotificationChannel()

        val passengerName = binding.tvPassengerName.text?.toString()
            ?.takeIf { it.isNotBlank() && it != "Nama Penumpang" } ?: "Penumpang"
        val sender = Person.Builder().setName(passengerName).build()

        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("Anda").build())
        for (doc in unreadMessages.takeLast(5)) {
            val text = doc.getString("message") ?: continue
            val truncated = if (text.length > 100) text.take(100) + "..." else text
            val timeMillis = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
            messagingStyle.addMessage(truncated, timeMillis, sender)
        }

        val chatIntent = Intent(requireContext(), com.example.tugasakhirfinal.ForDriver.Chat.ChatDriverActivity::class.java).apply {
            putExtra("BOOKING_ID", bookingId)
        }
        val pendingIntent = PendingIntent.getActivity(
            requireContext(), bookingId.hashCode(), chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(requireContext(), CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(requireContext()).notify(bookingId.hashCode(), notification)
    }

    private fun cancelChatNotification(bookingId: String) {
        NotificationManagerCompat.from(requireContext()).cancel(bookingId.hashCode())
    }

    private fun createChatNotificationChannel() {
        val manager = requireContext().getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHAT_CHANNEL_ID, "Pesan Chat", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifikasi pesan chat baru selama pesanan aktif"
        }
        manager.createNotificationChannel(channel)
    }

    private fun setupChatButton(bookingId: String) {
        binding.btnChatPassenger?.visibility = View.VISIBLE
        listenUnreadMessages(bookingId) // 🚀 Aktifkan listener badge notifikasi

        binding.btnChatPassenger?.setOnClickListener {
            val intent = Intent(requireContext(), com.example.tugasakhirfinal.ForDriver.Chat.ChatDriverActivity::class.java)
            intent.putExtra("BOOKING_ID", bookingId)
            startActivity(intent)
        }
    }

    // 🚀 Deteksi otomatis "sudah sampai" berdasarkan jarak (bukan tombol) — hanya untuk etape
    // jemput (menuju pasien). Sekali per etape, dikirim ke driver sendiri.
    private fun checkArrivalProximity(distanceMeters: Float) {
        if (currentMode != "jemput" || hasNotifiedArrival) return
        if (distanceMeters > ARRIVAL_THRESHOLD_METERS) return

        hasNotifiedArrival = true
        showTripNotification("Sudah Sampai", "Anda sudah sampai di lokasi penjemputan pasien.")
    }

    private fun showTripNotification(title: String, message: String) {
        if (_binding == null) return
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        createTripNotificationChannel()

        val notification = NotificationCompat.Builder(requireContext(), TRIP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(requireContext())
            .notify((title + message).hashCode(), notification)
    }

    private fun createTripNotificationChannel() {
        val manager = requireContext().getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(TRIP_CHANNEL_ID, "Status Perjalanan", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifikasi tahapan penting perjalanan (sudah sampai, jemput, selesai)"
        }
        manager.createNotificationChannel(channel)
    }

    private fun konfirmasiJemput() {
        currentBookingId?.let { id ->
            binding.btnSelesai.isEnabled = false
            db.collection("bookings").document(id).update("status", "picked_up")
                .addOnSuccessListener {
                    binding.btnSelesai.isEnabled = true
                    Toast.makeText(requireContext(), "Pasien dijemput. Menuju Rumah Sakit!", Toast.LENGTH_SHORT).show()
                    showTripNotification("Pasien Dijemput", "Pasien berhasil dijemput. Menuju rumah sakit.")
                }
        }
    }

    private fun selesaikanTugas() {
        currentBookingId?.let { id ->
            binding.btnSelesai.isEnabled = false
            // completedAt dicatat supaya riwayat chat bisa disembunyikan otomatis 3 hari
            // setelah trip ini selesai (lihat filter di ChatFragment.kt/ChatFragmentDriver.kt).
            db.collection("bookings").document(id).update(
                mapOf(
                    "status" to "completed",
                    "completedAt" to com.google.firebase.Timestamp.now()
                )
            )
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Tugas Selesai! Kembali online.", Toast.LENGTH_LONG).show()
                    showTripNotification("Perjalanan Selesai", "Tugas selesai. Anda kembali online.")
                    resetToStandbyMode()

                    try {
                        val switchOnline = requireActivity().findViewById<SwitchCompat>(R.id.switchOnline)
                        val statusText = requireActivity().findViewById<TextView>(R.id.statusText)
                        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)

                        switchOnline?.isChecked = true
                        statusText?.text = "Status: Online"
                        bottomNav?.selectedItemId = R.id.nav_home
                    } catch (e: Exception) {
                        Log.e("ALUR_PINTAR", "Gagal auto-online: ${e.message}")
                    }
                }
        }
    }

    private fun resetToStandbyMode() {
        roadOverlay?.remove()
        targetMarker?.remove()
        targetLocation = null
        currentBookingId?.let { cancelChatNotification(it) }
        currentBookingId = null
        isRouteZoomed = false
        hasNotifiedArrival = false
        lastFetchedPassengerId = null
        passengerPhoneNumber = null

        binding.btnSelesai.visibility = View.GONE
        binding.btnChatPassenger?.visibility = View.GONE
        binding.tvUnreadBadge.visibility = View.GONE // Reset Badge
        binding.layoutPassengerInfo.visibility = View.GONE
        binding.tvCurrentStatus.text = "Siap menerima pesanan..."
        binding.tvEstimationInfo.text = ""

        unreadListener?.remove() // Hapus Listener Badge
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        unreadListener?.remove() // Pembersihan tambahan
        _binding = null
    }
}