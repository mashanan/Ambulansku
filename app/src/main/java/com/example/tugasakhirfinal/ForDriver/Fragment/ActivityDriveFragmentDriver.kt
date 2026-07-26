package com.example.tugasakhirfinal.ForDriver.Fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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

                if (status == "accepted") {
                    if (currentMode != "jemput") isRouteZoomed = false
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

    // 🚀 Fungsi untuk menghitung badge notifikasi
    private fun listenUnreadMessages(bookingId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        unreadListener?.remove()

        unreadListener = db.collection("bookings").document(bookingId)
            .collection("messages")
            .whereEqualTo("isRead", false) // Hanya filter pesan yang belum dibaca
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                var unreadCount = 0
                // Filter senderId manual di Kotlin
                for (doc in snapshot.documents) {
                    val senderId = doc.getString("senderId")
                    if (senderId != currentUserId) {
                        unreadCount++
                    }
                }

                if (_binding != null) {
                    if (unreadCount > 0) {
                        binding.tvUnreadBadge.text = unreadCount.toString()
                        binding.tvUnreadBadge.visibility = View.VISIBLE
                    } else {
                        binding.tvUnreadBadge.visibility = View.GONE
                    }
                }
            }
    }

    private fun setupChatButton(bookingId: String) {
        binding.btnChatPassenger?.visibility = View.VISIBLE
        listenUnreadMessages(bookingId) // 🚀 Aktifkan listener badge notifikasi

        binding.btnChatPassenger?.setOnClickListener {
            val intent = Intent(requireContext(), com.example.tugasakhirfinal.ForPassenger.ChatPassengerActivity::class.java)
            intent.putExtra("BOOKING_ID", bookingId)
            startActivity(intent)
        }
    }

    private fun konfirmasiJemput() {
        currentBookingId?.let { id ->
            binding.btnSelesai.isEnabled = false
            db.collection("bookings").document(id).update("status", "picked_up")
                .addOnSuccessListener {
                    binding.btnSelesai.isEnabled = true
                    Toast.makeText(requireContext(), "Pasien dijemput. Menuju Rumah Sakit!", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun selesaikanTugas() {
        currentBookingId?.let { id ->
            binding.btnSelesai.isEnabled = false
            db.collection("bookings").document(id).update("status", "completed")
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Tugas Selesai! Kembali online.", Toast.LENGTH_LONG).show()
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
        currentBookingId = null
        isRouteZoomed = false

        binding.btnSelesai.visibility = View.GONE
        binding.btnChatPassenger?.visibility = View.GONE
        binding.tvUnreadBadge.visibility = View.GONE // Reset Badge
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