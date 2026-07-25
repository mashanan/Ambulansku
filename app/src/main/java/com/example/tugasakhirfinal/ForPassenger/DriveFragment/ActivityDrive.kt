package com.example.tugasakhirfinal.ForPassenger

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.ActivityDriveBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ActivityDrive : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityDriveBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Data Peta & Lokasi
    private var hospitalList = mutableListOf<Hospital>()
    private var lastKnownLocation: LatLng? = null
    private var destinationHospitalLoc: LatLng? = null
    private var lastKnownDriverLocation: LatLng? = null

    private var roadOverlay: Polyline? = null
    private var hospitalMarkers = mutableListOf<Marker>()
    private var driverMarker: Marker? = null

    private var selectedHospitalName: String? = null
    private var isManualHospitalSelected = false

    // State Pelacakan
    private var searchDialog: AlertDialog? = null
    private var bookingListenerRegistration: ListenerRegistration? = null
    private var driverLocationListener: ListenerRegistration? = null

    // 🚀 Listener untuk Notifikasi Pesan Belum Dibaca
    private var unreadListener: ListenerRegistration? = null

    private var currentBookingId: String? = null
    private var currentBookingStatus: String = "standby" // "standby", "accepted", "picked_up"
    private var trackedDriverId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        binding = ActivityDriveBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.hasExtra("SELECTED_HOSPITAL_LAT")) {
            isManualHospitalSelected = true
            selectedHospitalName = intent.getStringExtra("SELECTED_HOSPITAL_NAME")
            val lat = intent.getDoubleExtra("SELECTED_HOSPITAL_LAT", 0.0)
            val lng = intent.getDoubleExtra("SELECTED_HOSPITAL_LNG", 0.0)

            if (lat != 0.0 && lng != 0.0) {
                destinationHospitalLoc = LatLng(lat, lng)
            }
        }

        updateHeaderTitle()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapview) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.btnPesanAmbulans.setOnClickListener { prosesPemesananAmbulans() }
    }

    private fun updateHeaderTitle() {
        if (isManualHospitalSelected) {
            binding.tvDestinationTitle.text = "Rumah Sakit Tujuan"
        } else {
            binding.tvDestinationTitle.text = "Rumah Sakit Terdekat"
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.isTrafficEnabled = true
        mMap.uiSettings.apply {
            isZoomControlsEnabled = false
            isMapToolbarEnabled = false
        }

        mMap.setOnMarkerClickListener { marker ->
            val selectedHospitalLoc = marker.position
            if (selectedHospitalLoc.latitude != 0.0 && selectedHospitalLoc.longitude != 0.0) {
                destinationHospitalLoc = selectedHospitalLoc
                selectedHospitalName = marker.title
                isManualHospitalSelected = true
                updateHeaderTitle()

                lastKnownLocation?.let { userLoc ->
                    drawRouteLine(userLoc, destinationHospitalLoc!!, "antar")
                }
                marker.showInfoWindow()
                return@setOnMarkerClickListener true
            }
            false
        }

        if (checkLocationPermission()) setupUserLocation()
        else requestLocationPermission()
    }

    private fun setupUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    lastKnownLocation = currentLatLng
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                    if (isManualHospitalSelected && destinationHospitalLoc != null) {
                        drawRouteLine(currentLatLng, destinationHospitalLoc!!, "antar")
                        displaySelectedHospitalMarker()
                    } else {
                        loadHospitalData()
                    }
                }
            }
        }
    }

    private fun loadHospitalData() {
        binding.tvDestinationAddress.text = "Mendeteksi rumah sakit terdekat..."

        firestore.collection("hospitals").get().addOnSuccessListener { result ->
            hospitalMarkers.forEach { it.remove() }
            hospitalMarkers.clear()
            hospitalList.clear()

            for (document in result) {
                val name = document.getString("nama") ?: "Tanpa Nama"
                val address = document.getString("alamat") ?: "-"
                val phone = document.getString("telepon") ?: "-"
                val igdStatus = document.getString("igdStatus") ?: "Tersedia"
                val geoPoint = document.getGeoPoint("lokasi")
                val lat = geoPoint?.latitude ?: document.getDouble("latitude") ?: 0.0
                val lng = geoPoint?.longitude ?: document.getDouble("longitude") ?: 0.0

                val hospital = Hospital(document.id, name, address, phone, igdStatus, lat, lng, geoPoint)
                hospitalList.add(hospital)
            }

            displayHospitalMarkers()

            lastKnownLocation?.let { userLoc ->
                if (!isManualHospitalSelected) {
                    findNearestHospitalFast(userLoc)
                }
            }
        }
    }

    private fun findNearestHospitalFast(userLoc: LatLng) {
        if (hospitalList.isEmpty()) return
        var nearestHospital: Hospital? = null
        var minDistance = Float.MAX_VALUE

        for (hospital in hospitalList) {
            val hLoc = hospital.getLatLng() ?: continue
            val results = FloatArray(1)
            Location.distanceBetween(userLoc.latitude, userLoc.longitude, hLoc.latitude, hLoc.longitude, results)
            if (results[0] < minDistance) {
                minDistance = results[0]
                nearestHospital = hospital
            }
        }

        nearestHospital?.let { target ->
            destinationHospitalLoc = target.getLatLng()
            selectedHospitalName = target.nama
            destinationHospitalLoc?.let { loc ->
                drawRouteLine(userLoc, loc, "antar")
            }
        }
    }

    private fun drawRouteLine(start: LatLng, end: LatLng, mode: String) {
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
                Log.e("MAPS_FALLBACK", "Directions API Error: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                roadOverlay?.remove()

                if (isApiSuccess && path.isNotEmpty()) {
                    roadOverlay = mMap.addPolyline(
                        PolylineOptions()
                            .addAll(path)
                            .width(14f)
                            .color(if (mode == "jemput") Color.RED else Color.BLUE)
                            .jointType(JointType.ROUND)
                            .startCap(RoundCap())
                            .endCap(RoundCap())
                    )

                    if (mode == "jemput") {
                        binding.tvDestinationAddress.text = "Ambulans Menjemput Anda"
                        binding.tvEstimationInfo.text = "Tiba dalam: $durationText ($distanceText)"
                    } else if (mode == "antar") {
                        val rsName = selectedHospitalName ?: "Rumah Sakit"
                        binding.tvDestinationAddress.text = "Menuju $rsName"
                        binding.tvEstimationInfo.text = "Waktu Tempuh: $durationText ($distanceText)"
                    }
                } else {
                    roadOverlay = mMap.addPolyline(
                        PolylineOptions().add(start, end).width(10f)
                            .color(if (mode == "jemput") Color.RED else Color.BLUE)
                            .pattern(listOf(Dash(20f), Gap(10f)))
                    )
                }

                val boundsBuilder = LatLngBounds.Builder().include(start).include(end)
                if (path.isNotEmpty()) {
                    for (point in path) boundsBuilder.include(point)
                }
                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150))
            }
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0; var lng = 0
        while (index < len) {
            var b: Int; var shift = 0; var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0; result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            poly.add(LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5))
        }
        return poly
    }

    private fun prosesPemesananAmbulans() {
        if (destinationHospitalLoc == null || lastKnownLocation == null) return
        showSearchingDriverDialog()

        val pickupGeoPoint = GeoPoint(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
        val passengerName = auth.currentUser?.displayName ?: "Pasien Ambulans"

        // 🚀 Hitung waktu kedaluwarsa 3 hari ke depan
        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 3)
        }
        val expiryTimestamp = Timestamp(calendar.time)

        val bookingData = hashMapOf(
            "passengerId" to (auth.currentUser?.uid ?: ""),
            "passengerName" to passengerName,
            "hospitalName" to (selectedHospitalName ?: "Rumah Sakit"),
            "pickupLocation" to pickupGeoPoint,
            "hospitalLat" to destinationHospitalLoc?.latitude,
            "hospitalLng" to destinationHospitalLoc?.longitude,
            "status" to "SEARCHING",
            "driverId" to "",
            "createdAt" to Timestamp.now(),
            "expiresAt" to expiryTimestamp // 🚀 Field batas kedaluwarsa 3 hari
        )

        firestore.collection("bookings").add(bookingData)
            .addOnSuccessListener { docRef ->
                currentBookingId = docRef.id
                listenDriverResponse(docRef.id)
            }
    }

    private fun listenDriverResponse(bookingId: String) {
        bookingListenerRegistration = firestore.collection("bookings").document(bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val status = snapshot.getString("status") ?: ""
                val driverId = snapshot.getString("driverId") ?: ""

                if (status == "accepted") {
                    dismissSearchingDriverDialog()
                    currentBookingStatus = "accepted"
                    binding.btnPesanAmbulans.visibility = View.GONE
                    binding.layoutDriverOnTheWay.visibility = View.VISIBLE
                    binding.tvDriverStatus.text = "Ambulans Sedang Menuju Lokasi Anda"
                    setupChatButton(bookingId)

                    if (driverId.isNotEmpty() && driverId != trackedDriverId) {
                        trackedDriverId = driverId
                        startTrackingDriverLocation(driverId)
                    }
                }
                else if (status == "picked_up") {
                    currentBookingStatus = "picked_up"
                    binding.tvDriverStatus.text = "Mengantar Pasien ke Rumah Sakit"

                    val startLoc = lastKnownDriverLocation ?: lastKnownLocation
                    if (startLoc != null && destinationHospitalLoc != null) {
                        drawRouteLine(startLoc, destinationHospitalLoc!!, "antar")
                    }

                    if (driverId.isNotEmpty() && driverId != trackedDriverId) {
                        trackedDriverId = driverId
                        startTrackingDriverLocation(driverId)
                    }
                }
                else if (status == "completed") {
                    Toast.makeText(this, "Perjalanan Selesai! Semoga lekas sembuh.", Toast.LENGTH_LONG).show()
                    resetToStandbyMode()
                    finish()
                }
            }
    }

    private fun startTrackingDriverLocation(driverId: String) {
        driverLocationListener?.remove()

        driverLocationListener = firestore.collection("users").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val geo = snapshot.getGeoPoint("currentLocation") ?: return@addSnapshotListener
                val driverLatLng = LatLng(geo.latitude, geo.longitude)
                lastKnownDriverLocation = driverLatLng

                updateDriverMarker(driverLatLng)

                if (currentBookingStatus == "accepted" && lastKnownLocation != null) {
                    drawRouteLine(driverLatLng, lastKnownLocation!!, "jemput")
                }
                else if (currentBookingStatus == "picked_up" && destinationHospitalLoc != null) {
                    drawRouteLine(driverLatLng, destinationHospitalLoc!!, "antar")
                }
            }
    }

    private fun updateDriverMarker(pos: LatLng) {
        if (driverMarker == null) {
            driverMarker = mMap.addMarker(
                MarkerOptions().position(pos).title("Ambulans")
                    .icon(getResizedBitmapDescriptor(R.drawable.ic_small_car, 85))
            )
        } else {
            driverMarker?.position = pos
        }
    }

    // 🚀 Fungsi untuk menghitung badge notifikasi
    private fun listenUnreadMessages(bookingId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        unreadListener?.remove()

        // 1. Ganti 'db' menjadi 'firestore'
        unreadListener = firestore.collection("bookings").document(bookingId)
            .collection("messages")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                var unreadCount = 0
                for (doc in snapshot.documents) {
                    val senderId = doc.getString("senderId")
                    if (senderId != currentUserId) {
                        unreadCount++
                    }
                }

                // 2. Hapus pengecekan '_binding', langsung panggil 'binding'
                if (unreadCount > 0) {
                    binding.tvUnreadBadge.text = unreadCount.toString()
                    binding.tvUnreadBadge.visibility = View.VISIBLE
                } else {
                    binding.tvUnreadBadge.visibility = View.GONE
                }
            }
    }

    private fun setupChatButton(bookingId: String) {
        binding.btnChatDriver.visibility = View.VISIBLE
        listenUnreadMessages(bookingId) // 🚀 Aktifkan listener badge notifikasi

        binding.btnChatDriver.setOnClickListener {
            val intent = Intent(this, ChatPassengerActivity::class.java)
            intent.putExtra("BOOKING_ID", bookingId)
            startActivity(intent)
        }
    }

    private fun resetToStandbyMode() {
        currentBookingStatus = "standby"

        binding.btnChatDriver.visibility = View.GONE
        binding.tvUnreadBadge.visibility = View.GONE // Reset Badge

        roadOverlay?.remove()
        driverMarker?.remove()
        driverMarker = null

        bookingListenerRegistration?.remove()
        driverLocationListener?.remove()
        unreadListener?.remove() // Hapus Listener Badge

        currentBookingId = null
        trackedDriverId = null
        lastKnownDriverLocation = null

        binding.tvDestinationAddress.text = "Siap Melayani"
        binding.tvEstimationInfo.text = "Pilih Rumah Sakit atau Panggil Ambulans"
        binding.btnPesanAmbulans.visibility = View.VISIBLE
        binding.layoutDriverOnTheWay.visibility = View.GONE

        if (lastKnownLocation != null && destinationHospitalLoc != null) {
            drawRouteLine(lastKnownLocation!!, destinationHospitalLoc!!, "antar")
        }
    }

    private fun showSearchingDriverDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_searching_driver, null)
        searchDialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
        searchDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        searchDialog?.show()
        searchDialog?.window?.setGravity(Gravity.CENTER)

        dialogView.findViewById<Button>(R.id.btnCancelSearch)?.setOnClickListener {
            dismissSearchingDriverDialog()
            currentBookingId?.let { id -> firestore.collection("bookings").document(id).update("status", "CANCELLED") }
            resetToStandbyMode()
        }
    }

    private fun dismissSearchingDriverDialog() { searchDialog?.dismiss(); searchDialog = null }

    // Menampilkan satu marker untuk rumah sakit yang dipilih manual dari HospitalListActivity
    private fun displaySelectedHospitalMarker() {
        val pos = destinationHospitalLoc ?: return
        hospitalMarkers.forEach { it.remove() }
        hospitalMarkers.clear()

        val icon = getResizedBitmapDescriptor(R.drawable.ic_hospital, 80)
        val m = mMap.addMarker(MarkerOptions().position(pos).title(selectedHospitalName ?: "Rumah Sakit").apply { if (icon != null) icon(icon) })
        m?.let { hospitalMarkers.add(it) }
    }

    private fun displayHospitalMarkers() {
        for (hospital in hospitalList) {
            val pos = hospital.getLatLng() ?: continue
            val icon = getResizedBitmapDescriptor(R.drawable.ic_hospital, 80)
            val m = mMap.addMarker(MarkerOptions().position(pos).title(hospital.nama).apply { if(icon!=null) icon(icon) })
            m?.let { hospitalMarkers.add(it) }
        }
    }

    private fun getResizedBitmapDescriptor(drawableId: Int, sizePx: Int): BitmapDescriptor? {
        val drawable = ContextCompat.getDrawable(this, drawableId) ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun checkLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun requestLocationPermission() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)

    override fun onDestroy() {
        super.onDestroy()
        bookingListenerRegistration?.remove()
        driverLocationListener?.remove()
        unreadListener?.remove() // Pembersihan tambahan
    }
}