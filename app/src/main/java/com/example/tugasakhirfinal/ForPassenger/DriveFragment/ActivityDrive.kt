package com.example.tugasakhirfinal.ForPassenger.DriveFragment

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.tugasakhirfinal.ForDriver.Welcome.DriverQualificationActivity
import com.example.tugasakhirfinal.ForPassenger.ChatPassengerActivity
import com.example.tugasakhirfinal.Maps.RouteGeometry
import com.example.tugasakhirfinal.Maps.RouteStyle
import com.example.tugasakhirfinal.Maps.SmoothMarkerAnimator
import com.example.tugasakhirfinal.Maps.VehicleMarkerIcon
import com.example.tugasakhirfinal.Maps.toLatLngs
import com.example.tugasakhirfinal.Maps.toRoutePoint
import com.example.tugasakhirfinal.Maps.toRoutePoints
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.ActivityDriveBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
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
import java.util.Locale
import kotlin.math.roundToInt

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

    // --- Pelacakan rute selama trip berjalan (lihat updateTrackedRoute) ---
    // Rute diambil dari Directions API SEKALI per etape, lalu dipotong lokal tiap lokasi driver
    // berubah. Versi lama memanggil API tiap ~3 detik: boros kuota, dan karena responsnya bisa
    // datang tidak berurutan, rute dari posisi lama sering menimpa yang baru - itulah sebabnya
    // garis di belakang ambulans kadang muncul lagi.
    private var trackedRouteLegKey: String? = null // "mode:lat,lng" tujuan etape yang sedang dilacak
    private var trackedRoutePath: List<LatLng> = emptyList()
    private var trackedLegDistanceMeters: Double? = null
    private var trackedLegDurationSeconds: Double? = null
    private var lastTrackedRouteRequestAt = 0L

    // Nomor urut permintaan rute - respons yang bukan dari permintaan terbaru diabaikan.
    private var routeRequestSeq = 0

    private val driverMarkerAnimator = SmoothMarkerAnimator()

    private var roadOverlay: Polyline? = null
    private var hospitalMarkers = mutableListOf<Marker>()
    private var driverMarker: Marker? = null

    private var selectedHospitalName: String? = null
    private var isManualHospitalSelected = false
    private var driverPhoneNumber: String? = null

    // Kategori ambulans yang dipilih passenger sebelum pesan - dipakai memfilter pesanan supaya
    // hanya diteruskan ke driver yang kategorinya sesuai. Default ke kategori pertama (Ambulans Biasa).
    private var selectedAmbulanceCategory: String = DriverQualificationActivity.AMBULANCE_CATEGORIES[0]

    // State Pelacakan
    private var searchDialog: AlertDialog? = null
    private var searchDialogView: View? = null
    private var searchCountDownTimer: CountDownTimer? = null
    private var bookingListenerRegistration: ListenerRegistration? = null
    private var driverLocationListener: ListenerRegistration? = null

    companion object {
        private const val MAX_SEARCH_SECONDS = 30
        private const val CHAT_CHANNEL_ID = "chat_message_channel"
        private const val TRIP_CHANNEL_ID = "trip_status_channel"
        private const val ARRIVAL_THRESHOLD_METERS = 100f

        // Level zoom Google Maps yang kira-kira menampilkan radius ~200m di sekitar driver saat
        // kamera mengikuti dari dekat (bukan menampilkan seluruh rute lagi).
        private const val DRIVER_FOLLOW_ZOOM = 17f

        // Driver menyimpang lebih dari ini dari garis rute -> minta rute baru dari posisinya.
        private const val OFF_ROUTE_METERS = 50.0

        // Jeda minimum antar permintaan Directions API dalam satu etape (lokasi masuk tiap ~3 detik).
        private const val MIN_ROUTE_REQUEST_INTERVAL_MS = 10_000L

        // Ukuran marker rumah sakit - sama dengan pov driver (ActivityDriveFragmentDriver).
        private const val TARGET_MARKER_SIZE_DP = 32
    }

    private var hasNotifiedArrival = false
    private var lastNotifiedStatus: String? = null

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

        // Panel info pesanan tampil penuh secara default (sama seperti sebelumnya), tapi
        // sekarang bisa ditarik turun oleh user kapan saja untuk memaksimalkan tampilan peta.
        BottomSheetBehavior.from(binding.cardOrderInfo).state = BottomSheetBehavior.STATE_EXPANDED

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

        binding.btnCallDriver.setOnClickListener {
            val phone = driverPhoneNumber
            if (!phone.isNullOrBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(this, "Driver phone number is not available yet", Toast.LENGTH_SHORT).show()
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    private fun updateHeaderTitle() {
        if (isManualHospitalSelected) {
            binding.tvDestinationTitle.text = "Destination Hospital"
        } else {
            binding.tvDestinationTitle.text = "Nearest Hospital"
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
            // Ganti tujuan RS hanya boleh sebelum memesan, dan hanya lewat marker rumah sakit.
            // Sebelumnya, mengetuk marker ambulans di tengah trip ikut dianggap "pilih RS" dan
            // menggambar rute ke posisi ambulans.
            if (currentBookingStatus != "standby" || marker !in hospitalMarkers) return@setOnMarkerClickListener false
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

    // Dialog sistem "Nyalakan lokasi?" (dari SettingsClient). Kalau user menyalakan GPS lewat
    // dialog itu, langsung lanjut ambil lokasi; kalau ditolak, tampilkan pesan di bottom sheet.
    private val locationSettingsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) fetchUserLocation()
        else showLocationProblem("Location is turned off. Turn on GPS so we can find the nearest hospital.")
    }

    private fun setupUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        mMap.isMyLocationEnabled = true

        // Sebelumnya langsung pakai lastLocation: kalau GPS HP mati (atau belum pernah dapat fix),
        // hasilnya null dan layar diam di "Detecting hospital location..." tanpa penjelasan.
        // Sekarang cek dulu pengaturan lokasi HP dan minta user menyalakannya kalau mati.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build()
        val settingsRequest = LocationSettingsRequest.Builder().addLocationRequest(request).build()
        LocationServices.getSettingsClient(this).checkLocationSettings(settingsRequest)
            .addOnSuccessListener { fetchUserLocation() }
            .addOnFailureListener { e ->
                if (e is com.google.android.gms.common.api.ResolvableApiException) {
                    try {
                        locationSettingsLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(e.resolution).build()
                        )
                    } catch (ex: Exception) {
                        showLocationProblem("Location is turned off. Turn on GPS so we can find the nearest hospital.")
                    }
                } else {
                    // Pengaturan tidak bisa dicek (mis. Play Services bermasalah) - tetap coba ambil lokasi.
                    fetchUserLocation()
                }
            }
    }

    // Pakai lokasi terakhir kalau ada; kalau null (GPS baru dinyalakan, belum ada cache),
    // minta lokasi baru sekali lewat getCurrentLocation.
    private fun fetchUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        binding.tvDestinationAddress.text = "Getting your location..."
        fusedLocationClient.lastLocation.addOnSuccessListener { last ->
            if (last != null) {
                onUserLocationReady(last)
                return@addOnSuccessListener
            }
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                com.google.android.gms.tasks.CancellationTokenSource().token
            ).addOnSuccessListener { current ->
                if (current != null) onUserLocationReady(current)
                else showLocationProblem("Couldn't get your location. Make sure GPS is on, then reopen this screen.")
            }.addOnFailureListener {
                showLocationProblem("Couldn't get your location. Make sure GPS is on, then reopen this screen.")
            }
        }.addOnFailureListener {
            showLocationProblem("Couldn't get your location. Make sure GPS is on, then reopen this screen.")
        }
    }

    private fun showLocationProblem(message: String) {
        binding.tvDestinationAddress.text = "Location unavailable"
        binding.tvDestinationEstimation.text = message
    }

    private fun onUserLocationReady(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)
        lastKnownLocation = currentLatLng
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

        // Kalau passenger sudah punya booking aktif (baru saja back dari layar ini di
        // tengah penjemputan), reconnect ke trip itu dulu - jangan biarkan masuk ke
        // mode standby/pilih rumah sakit yang bisa berujung bikin booking baru.
        checkForActiveBooking { hasActive ->
            if (hasActive) return@checkForActiveBooking
            if (isManualHospitalSelected && destinationHospitalLoc != null) {
                drawRouteLine(currentLatLng, destinationHospitalLoc!!, "antar")
                displaySelectedHospitalMarker()
            } else {
                loadHospitalData()
            }
        }
    }

    // Sebelumnya tidak ada handler ini: setelah user memberi izin lokasi pertama kali, lokasi
    // tidak langsung diambil (harus keluar-masuk layar dulu).
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 100) return
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::mMap.isInitialized) setupUserLocation()
        } else {
            showLocationProblem("Location permission is needed to find the nearest hospital and pick you up.")
        }
    }

    // Cek apakah passenger yang sedang login masih punya booking yang berjalan (belum
    // completed/CANCELLED). Kalau ada, resume tampilan ke trip itu alih-alih mode standby.
    private fun checkForActiveBooking(onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            onResult(false)
            return
        }
        firestore.collection("bookings")
            .whereEqualTo("passengerId", uid)
            .whereIn("status", listOf("SEARCHING", "accepted", "picked_up"))
            .get()
            .addOnSuccessListener { snapshot ->
                val now = Timestamp.now()
                val activeDoc = snapshot.documents
                    .filterNot { doc ->
                        // Booking SEARCHING yang sudah lewat batas 3 hari (expiresAt) dianggap
                        // basi/ditinggalkan - jangan sampai salah "dilanjutkan" begitu passenger
                        // buka app lagi. accepted/picked_up TIDAK disaring expiresAt karena itu
                        // trip yang sedang berjalan, bukan pesanan yang menunggu driver.
                        doc.getString("status") == "SEARCHING" &&
                            doc.getTimestamp("expiresAt")?.let { it <= now } == true
                    }
                    .sortedByDescending { it.getTimestamp("createdAt") }
                    .firstOrNull()
                if (activeDoc != null) {
                    resumeActiveBooking(activeDoc)
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
            .addOnFailureListener { onResult(false) }
    }

    private fun resumeActiveBooking(doc: DocumentSnapshot) {
        val bookingId = doc.id
        currentBookingId = bookingId
        selectedHospitalName = doc.getString("hospitalName")
        isManualHospitalSelected = true
        updateHeaderTitle()

        val hLat = doc.getDouble("hospitalLat")
        val hLng = doc.getDouble("hospitalLng")
        if (hLat != null && hLng != null) {
            destinationHospitalLoc = LatLng(hLat, hLng)
            displaySelectedHospitalMarker()
            lastKnownLocation?.let { drawRouteLine(it, destinationHospitalLoc!!, "antar") }
        }

        val status = doc.getString("status") ?: "SEARCHING"
        if (status == "SEARCHING") {
            showSearchingDriverDialog()
            startSearchTimer()
        }

        listenDriverResponse(bookingId)
    }

    private fun loadHospitalData() {
        binding.tvDestinationAddress.text = "Detecting nearest hospital..."

        // Hanya RS di sekitar passenger (radius 30 km, diperluas otomatis bila kosong), bukan
        // seluruh koleksi - hemat pembacaan Firestore walau data RS mencakup seluruh Indonesia.
        val center = lastKnownLocation ?: return
        com.example.tugasakhirfinal.Maps.NearbyHospitals.load(firestore, center, onResult = { nearby, _ ->
            hospitalMarkers.forEach { it.remove() }
            hospitalMarkers.clear()
            hospitalList.clear()
            hospitalList.addAll(nearby)

            displayHospitalMarkers()

            lastKnownLocation?.let { userLoc ->
                if (!isManualHospitalSelected) {
                    findNearestHospitalFast(userLoc)
                }
            }
        }, onError = {
            binding.tvDestinationAddress.text = "Couldn't load hospitals. Check your connection."
        })
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

    // fitFullBounds=true (default) menampilkan seluruh rute (titik awal+akhir+jalur) - dipakai
    // sekali di AWAL tiap etape supaya user dapat gambaran penuh perjalanan. Untuk update lokasi
    // driver selanjutnya SELAMA etape yang sama, panggil dengan fitFullBounds=false supaya kamera
    // cuma mengikuti dari dekat (radius ~200m) alih-alih re-fit ke seluruh rute tiap kali - kalau
    // jaraknya jauh, belokan kecil di dekat driver jadi susah terlihat kalau selalu di-zoom out.
    private fun drawRouteLine(
        start: LatLng,
        end: LatLng,
        mode: String,
        fitFullBounds: Boolean = true,
        onRouteReady: ((path: List<LatLng>, distanceMeters: Double?, durationSeconds: Double?) -> Unit)? = null
    ) {
        val requestSeq = ++routeRequestSeq
        val apiKey = getString(R.string.google_maps_key)
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${start.latitude},${start.longitude}" +
                "&destination=${end.latitude},${end.longitude}" +
                // departure_time=now -> Google ikut menghitung kondisi lalu lintas saat ini dan
                // mengembalikan duration_in_traffic (lihat parsing di bawah).
                "&mode=driving&departure_time=now&key=$apiKey"

        CoroutineScope(Dispatchers.IO).launch {
            var durationText = ""
            var distanceText = ""
            var path: List<LatLng> = emptyList()
            var isApiSuccess = false
            var distanceMeters: Double? = null
            var durationSeconds: Double? = null

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

                            // Estimasi memperhitungkan macet kalau Google punya datanya; kalau tidak
                            // (mis. jalan kecil tanpa data traffic), pakai durasi normal.
                            val duration = legs.optJSONObject("duration_in_traffic") ?: legs.getJSONObject("duration")
                            durationText = duration.getString("text")
                            distanceText = legs.getJSONObject("distance").getString("text")
                            distanceMeters = legs.getJSONObject("distance").getDouble("value")
                            durationSeconds = duration.getDouble("value")
                            path = decodePolyline(overviewPolyline)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MAPS_FALLBACK", "Directions API Error: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                // Respons basi: sudah ada permintaan rute yang lebih baru - abaikan supaya rute
                // dari posisi lama tidak menimpa rute terbaru.
                if (requestSeq != routeRequestSeq) return@withContext
                roadOverlay?.remove()

                if (isApiSuccess && path.isNotEmpty()) {
                    roadOverlay = mMap.addPolyline(
                        PolylineOptions()
                            .addAll(path)
                            .width(14f)
                            .color(RouteStyle.colorFor(mode))
                            .jointType(JointType.ROUND)
                            .startCap(RoundCap())
                            .endCap(RoundCap())
                    )

                    if (mode == "jemput") {
                        binding.tvDestinationAddress.text = "Ambulance Picking You Up"
                        binding.tvDestinationEstimation.text = "$distanceText • $durationText"
                        binding.tvEstimationInfo.text = "Arriving in: $durationText ($distanceText)"
                    } else if (mode == "antar") {
                        val rsName = selectedHospitalName ?: "Hospital"
                        binding.tvDestinationAddress.text = "Heading to $rsName"
                        binding.tvDestinationEstimation.text = "$distanceText • $durationText"
                        binding.tvEstimationInfo.text = "Travel Time: $durationText ($distanceText)"
                    }
                } else {
                    roadOverlay = mMap.addPolyline(
                        PolylineOptions().add(start, end).width(10f)
                            .color(RouteStyle.colorFor(mode))
                            .pattern(listOf(Dash(20f), Gap(10f)))
                    )
                    binding.tvDestinationEstimation.text = "Distance & travel time not available"
                }

                onRouteReady?.invoke(if (isApiSuccess) path else emptyList(), distanceMeters, durationSeconds)

                if (fitFullBounds) {
                    val boundsBuilder = LatLngBounds.Builder().include(start).include(end)
                    if (path.isNotEmpty()) {
                        for (point in path) boundsBuilder.include(point)
                    }
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 150))
                } else {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(start, DRIVER_FOLLOW_ZOOM))
                }
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
        showAmbulanceCategoryDialog()
    }

    // Ditanyakan SEBELUM booking dibuat (jadi sebelum notifikasi terkirim ke driver manapun) -
    // hasilnya dipakai untuk menyaring supaya pesanan hanya diteruskan ke driver dengan kategori
    // ambulans yang sama (lihat filter di HomeFragmentDriver.kt & DriverOnlineService.kt).
    private fun showAmbulanceCategoryDialog() {
        val categories = DriverQualificationActivity.AMBULANCE_CATEGORIES
        val descriptions = DriverQualificationActivity.AMBULANCE_CATEGORY_DESCRIPTIONS
        val displayItems = categories.map { "$it\n${descriptions[it]}" }.toTypedArray()
        var chosenIndex = categories.indexOf(selectedAmbulanceCategory).let { if (it >= 0) it else 0 }

        AlertDialog.Builder(this)
            .setTitle("Select the Ambulance Type You Need")
            .setSingleChoiceItems(displayItems, chosenIndex) { _, which -> chosenIndex = which }
            .setPositiveButton("Book Now") { _, _ ->
                selectedAmbulanceCategory = categories[chosenIndex]
                showSearchingDriverDialog()
                createBookingRequest()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createBookingRequest() {
        val pickupLatLng = lastKnownLocation!!
        val pickupGeoPoint = GeoPoint(pickupLatLng.latitude, pickupLatLng.longitude)
        val passengerName = auth.currentUser?.displayName ?: "Ambulance Patient"

        // 🚀 Hitung waktu kedaluwarsa 3 hari ke depan
        val calendar = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 3)
        }
        val expiryTimestamp = Timestamp(calendar.time)

        val bookingData = hashMapOf(
            "passengerId" to (auth.currentUser?.uid ?: ""),
            "passengerName" to passengerName,
            "hospitalName" to (selectedHospitalName ?: "Hospital"),
            "locationName" to "Detecting address...",
            "pickupLocation" to pickupGeoPoint,
            "hospitalLat" to destinationHospitalLoc?.latitude,
            "hospitalLng" to destinationHospitalLoc?.longitude,
            "status" to "SEARCHING",
            "driverId" to "",
            "ambulanceCategory" to selectedAmbulanceCategory,
            "createdAt" to Timestamp.now(),
            "expiresAt" to expiryTimestamp // 🚀 Field batas kedaluwarsa 3 hari
        )

        // Kirim booking SEGERA agar langsung terlihat oleh driver, jangan ditunda oleh proses lain.
        firestore.collection("bookings").add(bookingData)
            .addOnSuccessListener { docRef ->
                currentBookingId = docRef.id
                listenDriverResponse(docRef.id)
                startSearchTimer()
                resolveAndUpdateAddress(docRef.id, pickupLatLng)
            }
    }

    // Reverse-geocode berjalan di belakang layar SETELAH booking terkirim, supaya proses yang bisa
    // lambat/menggantung di beberapa perangkat ini tidak menunda notifikasi pesanan ke driver.
    // Hasilnya diupdate ke field locationName dan otomatis muncul di kartu Driver via listener real-time.
    private fun resolveAndUpdateAddress(bookingId: String, latLng: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
            val addressLabel = reverseGeocodeAddress(latLng)
            withContext(Dispatchers.Main) {
                firestore.collection("bookings").document(bookingId).update("locationName", addressLabel)
            }
        }
    }

    private fun reverseGeocodeAddress(latLng: LatLng): String {
        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(this, Locale.getDefault())
                .getFromLocation(latLng.latitude, latLng.longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: "Pickup Location"
        } catch (e: Exception) {
            Log.e("GEOCODER", "Reverse geocode failed: ${e.message}")
            "Pickup Location"
        }
    }

    // 🚀 Batas maksimal pencarian driver: 30 detik, dengan hitungan detik berjalan.
    // Kalau habis waktu dan belum ada driver yang menerima, tampilkan tombol "Cari Ulang".
    private fun startSearchTimer() {
        searchCountDownTimer?.cancel()
        searchCountDownTimer = object : CountDownTimer(MAX_SEARCH_SECONDS * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsed = MAX_SEARCH_SECONDS - (millisUntilFinished / 1000L).toInt()
                searchDialogView?.findViewById<TextView>(R.id.tvElapsedTime)?.text = "$elapsed seconds elapsed"
            }

            override fun onFinish() {
                searchDialogView?.let { view ->
                    view.findViewById<TextView>(R.id.tvElapsedTime)?.text = "$MAX_SEARCH_SECONDS seconds elapsed"
                    view.findViewById<ProgressBar>(R.id.pbSearching)?.visibility = View.GONE
                    view.findViewById<TextView>(R.id.tvSearchingDescription)?.text =
                        "No driver found within $MAX_SEARCH_SECONDS seconds."
                    view.findViewById<Button>(R.id.btnRefreshSearch)?.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun retrySearch() {
        searchCountDownTimer?.cancel()
        bookingListenerRegistration?.remove()

        // Batalkan booking lama yang belum ada driver-nya, lalu buat permintaan baru
        currentBookingId?.let { id -> firestore.collection("bookings").document(id).update("status", "CANCELLED") }
        currentBookingId = null

        resetSearchDialogState()
        createBookingRequest()
    }

    private fun resetSearchDialogState() {
        searchDialogView?.let { view ->
            view.findViewById<ProgressBar>(R.id.pbSearching)?.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvSearchingDescription)?.text =
                "Please wait a moment, the system is connecting you with the nearest driver."
            view.findViewById<TextView>(R.id.tvElapsedTime)?.text = "0 seconds elapsed"
            view.findViewById<Button>(R.id.btnRefreshSearch)?.visibility = View.GONE
        }
    }

    private fun listenDriverResponse(bookingId: String) {
        bookingListenerRegistration = firestore.collection("bookings").document(bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val status = snapshot.getString("status") ?: ""
                val driverId = snapshot.getString("driverId") ?: ""

                if (status == "accepted") {
                    // Passenger mungkin sedang di Home (pencarian jalan di background) - beri tahu
                    // lewat notifikasi sekali saat driver pertama kali menerima.
                    // (searchDialog != null = baru saja berpindah dari SEARCHING, bukan resume trip lama)
                    if (currentBookingStatus == "standby" && searchDialog != null) {
                        showTripNotification("Driver found", "An ambulance has accepted your booking and is heading to you.")
                    }
                    dismissSearchingDriverDialog()
                    currentBookingStatus = "accepted"
                    binding.btnPesanAmbulans.visibility = View.GONE
                    binding.layoutDriverOnTheWay.visibility = View.VISIBLE
                    binding.tvDriverStatus.text = "Ambulance Is Heading to Your Location"
                    setupChatButton(bookingId)
                    updateTripStepper(1)

                    if (driverId.isNotEmpty() && driverId != trackedDriverId) {
                        trackedDriverId = driverId
                        startTrackingDriverLocation(driverId)
                        fetchDriverProfile(driverId)
                    }
                }
                else if (status == "picked_up") {
                    currentBookingStatus = "picked_up"
                    binding.tvDriverStatus.text = "Taking Patient to Hospital"
                    updateTripStepper(2)

                    // Deteksi driver mengalihkan tujuan ke RS lain di tengah perjalanan (kondisi
                    // darurat, butuh fasilitas lebih lengkap) - update tujuan lokal & beri tahu
                    // passenger begitu terdeteksi berubah dari yang sudah diketahui sebelumnya.
                    val newHospitalName = snapshot.getString("hospitalName")
                    val newHospitalLat = snapshot.getDouble("hospitalLat")
                    val newHospitalLng = snapshot.getDouble("hospitalLng")
                    if (!newHospitalName.isNullOrEmpty() && newHospitalLat != null && newHospitalLng != null) {
                        val newLoc = LatLng(newHospitalLat, newHospitalLng)
                        val hospitalChanged = newHospitalName != selectedHospitalName || destinationHospitalLoc != newLoc
                        selectedHospitalName = newHospitalName
                        destinationHospitalLoc = newLoc
                        if (hospitalChanged) {
                            displaySelectedHospitalMarker()
                            Toast.makeText(this, "The driver has redirected to $newHospitalName due to an emergency.", Toast.LENGTH_LONG).show()
                            showTripNotification("Destination Hospital Changed", "The driver has redirected to $newHospitalName for more complete care.")
                        }
                    }

                    val driverLoc = lastKnownDriverLocation
                    val hospitalLoc = destinationHospitalLoc
                    if (driverLoc != null && hospitalLoc != null) {
                        // Etape baru (atau RS tujuan berubah) -> updateTrackedRoute otomatis
                        // mengambil rute penuh sekali; kalau etapenya sama, cukup dipotong lokal.
                        updateTrackedRoute(driverLoc, hospitalLoc, "antar")
                    } else if (lastKnownLocation != null && hospitalLoc != null) {
                        drawRouteLine(lastKnownLocation!!, hospitalLoc, "antar")
                    }

                    if (driverId.isNotEmpty() && driverId != trackedDriverId) {
                        trackedDriverId = driverId
                        startTrackingDriverLocation(driverId)
                    }

                    if (lastNotifiedStatus != "picked_up") {
                        lastNotifiedStatus = "picked_up"
                        showTripNotification("You Have Been Picked Up", "You are on your way to the hospital.")
                    }
                }
                else if (status == "completed") {
                    updateTripStepper(3)
                    if (lastNotifiedStatus != "completed") {
                        lastNotifiedStatus = "completed"
                        showTripNotification("Arrived at Hospital", "The ambulance has arrived at the destination hospital. Get well soon.")
                    }
                    Toast.makeText(this, "Trip Completed! Get well soon.", Toast.LENGTH_LONG).show()
                    resetToStandbyMode()
                    finish()
                }
                // Dibatalkan driver di TENGAH perjalanan (kecelakaan, ban bocor, dll) - beda dari
                // pembatalan oleh passenger sendiri (lewat tombol batal cari driver), yang tidak
                // pernah membawa field cancelledBy="driver" ini.
                else if (status == "CANCELLED" && snapshot.getString("cancelledBy") == "driver") {
                    if (lastNotifiedStatus != "cancelled_by_driver") {
                        lastNotifiedStatus = "cancelled_by_driver"
                        showDriverCancelledDialog()
                    }
                }
            }
    }

    private fun showDriverCancelledDialog() {
        // Bersihkan notifikasi tahap perjalanan terakhir sebelum dibatalkan (mis. "Ambulans
        // Tiba") - resetToStandbyMode() sengaja TIDAK menyentuh notifikasi trip supaya
        // notifikasi "Perjalanan Selesai" tetap ada sebagai bukti untuk kasus selesai normal,
        // tapi untuk kasus dibatalkan tidak ada notifikasi pengganti yang menandakan trip usai.
        currentBookingId?.let { NotificationManagerCompat.from(this).cancel(tripNotificationId(it)) }
        resetToStandbyMode()
        AlertDialog.Builder(this)
            .setTitle("Booking Cancelled by Driver")
            .setMessage("We're sorry, the driver cancelled the booking due to an issue on the way (e.g. an accident or a flat tire). You can search for another ambulance now with the same destination.")
            .setCancelable(false)
            .setPositiveButton("Find Another Ambulance") { _, _ ->
                showSearchingDriverDialog()
                createBookingRequest()
            }
            .setNegativeButton("Not Now", null)
            .show()
    }

    // Tandai tahap yang sudah lewat & tahap aktif (coral v2), tahap yang belum (abu-abu).
    // stage: 1=Dijemput, 2=Menuju RS, 3=Selesai.
    private fun updateTripStepper(activeStage: Int) {
        val green = ContextCompat.getColor(this, R.color.v2_brand)          // tahap yang sudah lewat
        val gray = ContextCompat.getColor(this, R.color.v2_bg_subtle)       // tahap yang belum
        val pink = ContextCompat.getColor(this, R.color.v2_brand_accent)    // tahap aktif

        val circles = listOf(binding.stepCircle1, binding.stepCircle2, binding.stepCircle3)
        for ((index, circle) in circles.withIndex()) {
            val stageNum = index + 1
            val color = when {
                stageNum < activeStage -> green
                stageNum == activeStage -> pink
                else -> gray
            }
            circle.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }

        val connectors = listOf(binding.stepConnector1, binding.stepConnector2)
        for ((index, connector) in connectors.withIndex()) {
            val stageNum = index + 1
            connector.setBackgroundColor(if (stageNum < activeStage) green else gray)
        }
    }

    // Ambil sekali (bukan tiap update lokasi) profil driver yang menerima pesanan, supaya
    // passenger bisa konfirmasi identitas driver saat bertemu (mirip Gojek/Uber).
    private fun fetchDriverProfile(driverId: String) {
        firestore.collection("users").document(driverId).get()
            .addOnSuccessListener { doc ->
                if (doc == null || !doc.exists()) return@addOnSuccessListener

                binding.tvDriverName.text = doc.getString("nama") ?: "Ambulance Driver"
                val plate = doc.getString("ambulancePlateNumber")
                binding.tvDriverPlate.text = "Plate: ${if (plate.isNullOrBlank()) "-" else plate}"
                driverPhoneNumber = doc.getString("phoneNumber")

                Glide.with(this)
                    .load(doc.getString("profileImageUrl")?.ifEmpty { null } ?: R.drawable.foto_profil)
                    .placeholder(R.drawable.foto_profil)
                    .circleCrop()
                    .into(binding.ivDriverPhoto)
            }
    }

    private fun startTrackingDriverLocation(driverId: String) {
        driverLocationListener?.remove()

        driverLocationListener = firestore.collection("users").document(driverId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val geo = snapshot.getGeoPoint("currentLocation") ?: return@addSnapshotListener
                val driverLatLng = LatLng(geo.latitude, geo.longitude)
                val previousDriverLatLng = lastKnownDriverLocation
                lastKnownDriverLocation = driverLatLng

                updateDriverMarker(driverLatLng, previousDriverLatLng)

                if (currentBookingStatus == "accepted" && lastKnownLocation != null) {
                    updateTrackedRoute(driverLatLng, lastKnownLocation!!, "jemput")

                    val results = FloatArray(1)
                    Location.distanceBetween(
                        driverLatLng.latitude, driverLatLng.longitude,
                        lastKnownLocation!!.latitude, lastKnownLocation!!.longitude, results
                    )
                    checkArrivalProximity(results[0])
                }
                else if (currentBookingStatus == "picked_up" && destinationHospitalLoc != null) {
                    updateTrackedRoute(driverLatLng, destinationHospitalLoc!!, "antar")
                }
            }
    }

    // Menjaga garis rute selama trip berjalan, dipanggil tiap lokasi driver berubah:
    // - etape baru / tujuan berubah -> ambil rute penuh sekali & tampilkan seluruhnya (fit bounds)
    // - etape sama -> potong lokal di posisi ambulans (RouteGeometry.trim), kamera mengikuti driver
    // - driver keluar jalur > OFF_ROUTE_METERS, atau rute belum ada -> ambil ulang (dibatasi jeda)
    private fun updateTrackedRoute(driverPos: LatLng, target: LatLng, mode: String) {
        val legKey = "$mode:${target.latitude},${target.longitude}"
        if (legKey != trackedRouteLegKey) {
            trackedRouteLegKey = legKey
            trackedRoutePath = emptyList()
            requestTrackedRoute(driverPos, target, mode, fitFullBounds = true)
            return
        }

        val trim = if (trackedRoutePath.size >= 2) {
            RouteGeometry.trim(trackedRoutePath.toRoutePoints(), driverPos.toRoutePoint())
        } else null

        if (trim == null || trim.distanceFromRouteMeters > OFF_ROUTE_METERS) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastTrackedRouteRequestAt >= MIN_ROUTE_REQUEST_INTERVAL_MS) {
                requestTrackedRoute(driverPos, target, mode, fitFullBounds = false)
            } else {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverPos, DRIVER_FOLLOW_ZOOM))
            }
            return
        }

        val remaining = trim.remaining.toLatLngs()
        trackedRoutePath = remaining
        // Ganti titik polyline yang ada (bukan hapus & gambar ulang) supaya garis tidak berkedip.
        roadOverlay?.points = remaining
        updateTrackedEta(mode, RouteGeometry.lengthMeters(trim.remaining))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverPos, DRIVER_FOLLOW_ZOOM))
    }

    private fun requestTrackedRoute(driverPos: LatLng, target: LatLng, mode: String, fitFullBounds: Boolean) {
        lastTrackedRouteRequestAt = SystemClock.elapsedRealtime()
        val legKey = trackedRouteLegKey
        drawRouteLine(driverPos, target, mode, fitFullBounds) { path, distanceMeters, durationSeconds ->
            if (legKey != trackedRouteLegKey) return@drawRouteLine
            trackedRoutePath = path
            trackedLegDistanceMeters = distanceMeters
            trackedLegDurationSeconds = durationSeconds
        }
    }

    // Estimasi sisa waktu tanpa memanggil API lagi: durasi awal dari Directions API diskalakan
    // sebanding dengan sisa jarak sepanjang jalur.
    private fun updateTrackedEta(mode: String, remainingMeters: Double) {
        val refDistance = trackedLegDistanceMeters ?: return
        val refDuration = trackedLegDurationSeconds ?: return
        if (refDistance <= 0) return

        val minutes = ((remainingMeters / refDistance) * refDuration / 60).roundToInt().coerceAtLeast(1)
        val distanceText = String.format(Locale.US, "%.1f km", remainingMeters / 1000)
        val durationText = "$minutes min"

        binding.tvDestinationEstimation.text = "$distanceText • $durationText"
        binding.tvEstimationInfo.text = if (mode == "jemput") {
            "Arriving in: $durationText ($distanceText)"
        } else {
            "Travel Time: $durationText ($distanceText)"
        }
    }

    // 🚀 Deteksi otomatis "sudah sampai" berdasarkan jarak (bukan tombol) — hanya untuk etape
    // jemput (driver menuju passenger). Sekali per booking, dikirim ke passenger sendiri.
    private fun checkArrivalProximity(distanceMeters: Float) {
        if (hasNotifiedArrival || distanceMeters > ARRIVAL_THRESHOLD_METERS) return
        hasNotifiedArrival = true
        showTripNotification("Ambulance Arrived", "The ambulance has arrived at your location as requested.")
    }

    // ID notifikasi tetap PER BOOKING (bukan per teks pesan) supaya tiap tahap perjalanan
    // ("Ambulans Tiba" -> "Anda Telah Dijemput" -> "Sudah Sampai di Rumah Sakit") MENGGANTI
    // notifikasi tahap sebelumnya di tray, bukan menumpuk jadi banyak notifikasi terpisah yang
    // tidak pernah hilang sendiri kalau tidak di-swipe manual satu-satu.
    private fun tripNotificationId(bookingId: String) = ("trip_$bookingId").hashCode()

    private fun showTripNotification(title: String, message: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val bookingId = currentBookingId ?: return

        createTripNotificationChannel()

        val notification = NotificationCompat.Builder(this, TRIP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            // Ketuk notifikasi -> kembali ke layar trip yang sama (bukan membuat instance baru)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, tripNotificationId(bookingId),
                    Intent(this, ActivityDrive::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()

        NotificationManagerCompat.from(this).notify(tripNotificationId(bookingId), notification)
    }

    private fun createTripNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(TRIP_CHANNEL_ID, "Trip Status", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifications for key trip milestones (arrived, picked up, completed)"
        }
        manager.createNotificationChannel(channel)
    }

    // Marker ambulans meluncur halus ke posisi baru dan berputar mengikuti arah gerak - sama
    // seperti ikon mobil di Gojek/Grab - alih-alih "loncat" tiap update lokasi (~3 detik).
    private fun updateDriverMarker(pos: LatLng, previousPos: LatLng? = null) {
        val marker = driverMarker
        if (marker == null) {
            driverMarker = mMap.addMarker(
                MarkerOptions().position(pos).title("Ambulance")
                    .icon(VehicleMarkerIcon.create(this, R.drawable.ic_ambulance_topview))
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .zIndex(1f)
            )
            return
        }

        // Rotasi cuma diperbarui kalau memang bergerak cukup jauh - kalau diam, arah dari noise
        // GPS kecil bisa bikin ikon "gemetar" berputar-putar sendiri di tempat.
        val newRotation = previousPos
            ?.takeIf { RouteGeometry.distanceMeters(it.toRoutePoint(), pos.toRoutePoint()) > 3.0 }
            ?.let { RouteGeometry.bearingDegrees(it.toRoutePoint(), pos.toRoutePoint()) }
        driverMarkerAnimator.animateTo(marker, pos, newRotation)
    }

    // 🚀 Fungsi untuk menghitung badge notifikasi + tampilkan notifikasi gaya WhatsApp
    private fun listenUnreadMessages(bookingId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        unreadListener?.remove()

        unreadListener = firestore.collection("bookings").document(bookingId)
            .collection("messages")
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) return@addSnapshotListener

                // Diurutkan di client (bukan .orderBy di query) supaya tidak butuh composite index Firestore.
                val unreadFromDriver = snapshot.documents
                    .filter { it.getString("senderId") != currentUserId }
                    .sortedBy { it.getTimestamp("timestamp") }

                if (unreadFromDriver.isNotEmpty()) {
                    binding.tvUnreadBadge.text = unreadFromDriver.size.toString()
                    binding.tvUnreadBadge.visibility = View.VISIBLE
                    showChatNotification(bookingId, unreadFromDriver)
                } else {
                    binding.tvUnreadBadge.visibility = View.GONE
                    cancelChatNotification(bookingId)
                }
            }
    }

    // Notifikasi gaya WhatsApp: pesan-pesan yang belum dibaca ditumpuk (terbaru di bawah),
    // pesan panjang dipotong. Tidak ditampilkan kalau user memang sedang buka chat ini.
    private fun showChatNotification(bookingId: String, unreadMessages: List<DocumentSnapshot>) {
        if (ChatPassengerActivity.openBookingId == bookingId) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        createChatNotificationChannel()

        val driverName = binding.tvDriverName.text?.toString()
            ?.takeIf { it.isNotBlank() && it != "Driver Name" } ?: "Driver"
        val sender = Person.Builder().setName(driverName).build()

        val messagingStyle = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
        for (doc in unreadMessages.takeLast(5)) {
            val rawMessage = doc.getString("message") ?: ""
            val imageUrl = doc.getString("imageUrl") ?: ""
            if (rawMessage.isEmpty() && imageUrl.isEmpty()) continue
            val text = if (rawMessage.isEmpty() && imageUrl.isNotEmpty()) "📷 Photo" else rawMessage
            val truncated = if (text.length > 100) text.take(100) + "..." else text
            val timeMillis = doc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
            messagingStyle.addMessage(truncated, timeMillis, sender)
        }

        val chatIntent = Intent(this, ChatPassengerActivity::class.java).apply {
            putExtra("BOOKING_ID", bookingId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, bookingId.hashCode(), chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(bookingId.hashCode(), notification)
    }

    private fun cancelChatNotification(bookingId: String) {
        NotificationManagerCompat.from(this).cancel(bookingId.hashCode())
    }

    private fun createChatNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHAT_CHANNEL_ID, "Chat Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifications for new chat messages during an active booking"
        }
        manager.createNotificationChannel(channel)
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
        roadOverlay = null
        driverMarkerAnimator.cancel()
        driverMarker?.remove()
        driverMarker = null

        bookingListenerRegistration?.remove()
        driverLocationListener?.remove()
        unreadListener?.remove() // Hapus Listener Badge
        currentBookingId?.let { cancelChatNotification(it) }

        currentBookingId = null
        trackedDriverId = null
        lastKnownDriverLocation = null
        trackedRouteLegKey = null
        trackedRoutePath = emptyList()
        trackedLegDistanceMeters = null
        trackedLegDurationSeconds = null
        lastTrackedRouteRequestAt = 0L
        hasNotifiedArrival = false
        lastNotifiedStatus = null

        binding.tvDestinationAddress.text = "Ready to Serve"
        binding.tvEstimationInfo.text = "Select a Hospital or Call an Ambulance"
        binding.btnPesanAmbulans.visibility = View.VISIBLE
        binding.layoutDriverOnTheWay.visibility = View.GONE

        if (lastKnownLocation != null && destinationHospitalLoc != null) {
            drawRouteLine(lastKnownLocation!!, destinationHospitalLoc!!, "antar")
        }
    }

    private fun showSearchingDriverDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_searching_driver, null)
        searchDialogView = dialogView
        searchDialog = AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create()
        searchDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        searchDialog?.show()
        searchDialog?.window?.setGravity(Gravity.CENTER)

        dialogView.findViewById<Button>(R.id.btnCancelSearch)?.setOnClickListener {
            dismissSearchingDriverDialog()
            currentBookingId?.let { id -> firestore.collection("bookings").document(id).update("status", "CANCELLED") }
            resetToStandbyMode()
        }

        dialogView.findViewById<Button>(R.id.btnRefreshSearch)?.setOnClickListener {
            retrySearch()
        }

        // Kembali ke Home TANPA menghentikan pencarian: activity ini tidak di-finish, cuma
        // ditaruh di belakang, jadi listener booking & timer tetap jalan. Tombol back HP
        // diperlakukan sama (sebelumnya back tidak melakukan apa-apa karena dialog non-cancelable).
        dialogView.findViewById<Button>(R.id.btnMinimizeSearch)?.setOnClickListener {
            goHomeKeepSearching()
        }
        searchDialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                goHomeKeepSearching()
                true
            } else false
        }
    }

    private fun goHomeKeepSearching() {
        Toast.makeText(this, "Still searching for a driver. We'll notify you.", Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, com.example.tugasakhirfinal.ForPassenger.PassengerHomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    private fun dismissSearchingDriverDialog() {
        searchCountDownTimer?.cancel()
        searchDialog?.dismiss()
        searchDialog = null
        searchDialogView = null
    }

    // Menampilkan satu marker untuk rumah sakit yang dipilih manual dari HospitalListActivity
    private fun displaySelectedHospitalMarker() {
        val pos = destinationHospitalLoc ?: return
        hospitalMarkers.forEach { it.remove() }
        hospitalMarkers.clear()

        val icon = getResizedBitmapDescriptor(R.drawable.ic_hospital)
        val m = mMap.addMarker(MarkerOptions().position(pos).title(selectedHospitalName ?: "Hospital").apply { if (icon != null) icon(icon) })
        m?.let { hospitalMarkers.add(it) }
    }

    private fun displayHospitalMarkers() {
        for (hospital in hospitalList) {
            val pos = hospital.getLatLng() ?: continue
            val icon = getResizedBitmapDescriptor(R.drawable.ic_hospital)
            val m = mMap.addMarker(MarkerOptions().position(pos).title(hospital.nama).apply { if(icon!=null) icon(icon) })
            m?.let { hospitalMarkers.add(it) }
        }
    }

    // Ukuran dalam dp (bukan pixel mentah) supaya ukuran fisik ikon sama di semua kepadatan layar.
    private fun getResizedBitmapDescriptor(drawableId: Int, sizeDp: Int = TARGET_MARKER_SIZE_DP): BitmapDescriptor? {
        val sizePx = (sizeDp * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
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
        searchCountDownTimer?.cancel()
        driverMarkerAnimator.cancel()
        bookingListenerRegistration?.remove()
        driverLocationListener?.remove()
        unreadListener?.remove() // Pembersihan tambahan
    }
}