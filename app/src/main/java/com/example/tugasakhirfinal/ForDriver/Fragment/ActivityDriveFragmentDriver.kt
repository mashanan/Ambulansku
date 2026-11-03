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
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.tugasakhirfinal.ForPassenger.DriveFragment.HospitalListActivity
import com.example.tugasakhirfinal.Maps.RouteGeometry
import com.example.tugasakhirfinal.Maps.SmoothMarkerAnimator
import com.example.tugasakhirfinal.Maps.VehicleMarkerIcon
import com.example.tugasakhirfinal.Maps.toLatLngs
import com.example.tugasakhirfinal.Maps.toRoutePoint
import com.example.tugasakhirfinal.Maps.toRoutePoints
import com.example.tugasakhirfinal.R
import com.example.tugasakhirfinal.databinding.FragmentDriveDriverBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class ActivityDriveFragmentDriver : Fragment(), OnMapReadyCallback {

    companion object {
        private const val CHAT_CHANNEL_ID = "chat_message_channel"
        private const val TRIP_CHANNEL_ID = "trip_status_channel"
        private const val ARRIVAL_THRESHOLD_METERS = 100f

        // Level zoom Google Maps yang kira-kira menampilkan radius ~200m di sekitar driver saat
        // kamera mengikuti dari dekat (bukan menampilkan seluruh rute lagi).
        private const val DRIVER_FOLLOW_ZOOM = 17f

        // Kalau driver menyimpang lebih dari ini dari garis rute (salah belok / ambil jalan lain),
        // rute diminta ulang ke Directions API dari posisi sekarang.
        private const val OFF_ROUTE_METERS = 50.0

        // Jeda minimum antar permintaan Directions API selama satu etape. Lokasi masuk tiap 3
        // detik; tanpa jeda ini, saat API lambat/gagal aplikasi bisa memanggilnya tiap 3 detik.
        private const val MIN_ROUTE_REQUEST_INTERVAL_MS = 10_000L

        // Ukuran marker tujuan (RS / lokasi pasien) - sama dengan pov passenger.
        private const val TARGET_MARKER_SIZE_DP = 32
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

    // Jarak & durasi (dalam meter/detik) dari respons Directions API terakhir - dipakai untuk
    // memperkirakan ulang ETA di tiap update lokasi TANPA memanggil Directions API lagi (irit
    // kuota), dengan menyesuaikan durasi awal secara proporsional terhadap sisa jarak saat ini.
    private var lastLegDistanceMeters: Double? = null
    private var lastLegDurationSeconds: Double? = null

    // Titik-titik jalur hasil decode Directions API dari leg saat ini - dipakai untuk memotong
    // (trim) polyline secara lokal tiap update lokasi TANPA panggil Directions API lagi, supaya
    // bagian jalur yang sudah dilalui driver menghilang dari peta (sama seperti pov passenger),
    // bukan tetap utuh menampilkan seluruh rute dari titik awal.
    private var currentRoutePath: List<LatLng> = emptyList()

    // Nomor urut permintaan rute. Respons Directions API bisa datang tidak berurutan; respons
    // yang nomornya bukan yang terbaru diabaikan supaya rute lama tidak menimpa rute baru.
    private var routeRequestSeq = 0
    private var lastRouteRequestAt = 0L

    private val driverMarkerAnimator = SmoothMarkerAnimator()

    private var lastFetchedPassengerId: String? = null
    private var passengerPhoneNumber: String? = null

    // 🚀 Listener untuk Notifikasi Pesan Belum Dibaca
    private var unreadListener: ListenerRegistration? = null

    // Hasil pilih RS pengganti dari HospitalListActivity (mode picker) - lihat confirmChangeHospital()
    private val hospitalPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val name = data.getStringExtra(HospitalListActivity.EXTRA_SELECTED_HOSPITAL_NAME)
            val lat = data.getDoubleExtra(HospitalListActivity.EXTRA_SELECTED_HOSPITAL_LAT, 0.0)
            val lng = data.getDoubleExtra(HospitalListActivity.EXTRA_SELECTED_HOSPITAL_LNG, 0.0)
            if (!name.isNullOrEmpty() && lat != 0.0 && lng != 0.0) {
                confirmChangeHospital(name, lat, lng)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDriveDriverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Panel info tampil penuh secara default (sama seperti sebelumnya), tapi sekarang bisa
        // ditarik turun oleh driver kapan saja untuk memaksimalkan tampilan peta.
        BottomSheetBehavior.from(binding.bottomLayout).state = BottomSheetBehavior.STATE_EXPANDED

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        binding.btnSelesai.setOnClickListener {
            if (currentMode == "jemput") konfirmasiJemput() else selesaikanTugas()
        }

        binding.btnBatalkanPesanan.setOnClickListener { confirmCancelBookingStep1() }

        binding.btnGantiRumahSakit.setOnClickListener {
            val intent = Intent(requireContext(), HospitalListActivity::class.java).apply {
                putExtra(HospitalListActivity.EXTRA_PICKER_MODE, true)
            }
            hospitalPickerLauncher.launch(intent)
        }

        binding.btnCallPassenger.setOnClickListener {
            val phone = passengerPhoneNumber
            if (!phone.isNullOrBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(requireContext(), "Passenger phone number not yet available", Toast.LENGTH_SHORT).show()
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

    // Ukuran dalam dp (bukan pixel mentah) supaya ukuran fisik ikon sama di semua kepadatan layar,
    // dan sama persis dengan marker RS di pov passenger (ActivityDrive, TARGET_MARKER_SIZE_DP).
    private fun getResizedBitmapDescriptor(drawableId: Int, sizeDp: Int = TARGET_MARKER_SIZE_DP): BitmapDescriptor? {
        val sizePx = (sizeDp * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
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
                val previousPos = lastKnownDriverPosition
                lastKnownDriverPosition = currentPos

                auth.currentUser?.uid?.let { uid ->
                    db.collection("users").document(uid)
                        .update("currentLocation", GeoPoint(loc.latitude, loc.longitude))
                }

                updateDriverMarker(currentPos, previousPos)

                if (isFirstLocation) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPos, 17f))
                    isFirstLocation = false
                }

                targetLocation?.let { target ->
                    if (!isRouteZoomed) {
                        requestRouteThrottled(currentPos, target, currentMode)
                    } else {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(currentPos.latitude, currentPos.longitude, target.latitude, target.longitude, results)
                        val statusStr = if (currentMode == "jemput") "Picking Up Patient" else "Heading to Hospital"
                        binding.tvCurrentStatus.text = statusStr

                        // 🚀 Rute lengkap sudah ditampilkan sekali di awal etape ini (lihat
                        // drawRouteWithDirections -> fitCameraToRoute). Update lokasi selanjutnya
                        // cukup mengikuti posisi driver dari dekat (~200m) supaya belokan kecil
                        // tetap terlihat jelas walau jarak keseluruhan jauh.
                        followDriverCamera(currentPos)

                        // Potong bagian jalur yang sudah dilewati driver dari polyline (tanpa
                        // panggil Directions API lagi) supaya jalur yang sudah dilalui menghilang
                        // dari peta, sama seperti pov passenger.
                        trimTraveledRoute(currentPos, target)

                        // Sisa jarak diukur SEPANJANG jalur (bukan garis lurus) supaya estimasi
                        // tidak terlalu optimis di jalan yang berkelok.
                        val remainingMeters = if (currentRoutePath.size >= 2) {
                            RouteGeometry.lengthMeters(currentRoutePath.toRoutePoints())
                        } else {
                            results[0].toDouble()
                        }
                        val remainingKm = remainingMeters / 1000
                        val refDistance = lastLegDistanceMeters
                        val refDuration = lastLegDurationSeconds
                        binding.tvEstimationInfo.text = if (refDistance != null && refDuration != null && refDistance > 0) {
                            // Skala durasi awal secara proporsional terhadap sisa jarak sekarang,
                            // sama seperti "Tiba dalam: X menit (Y km)" di pov passenger.
                            val estMinutes = ((remainingMeters / refDistance) * refDuration / 60).roundToInt().coerceAtLeast(1)
                            "Estimate: $estMinutes min (${String.format("%.1f", remainingKm)} km)"
                        } else {
                            "Remaining Distance: ${String.format("%.1f", remainingKm)} km"
                        }

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
                    updateTripStepper(1)
                    binding.btnGantiRumahSakit.visibility = View.GONE

                    val geo = doc.getGeoPoint("pickupLocation")
                    geo?.let {
                        targetLocation = LatLng(it.latitude, it.longitude)
                        binding.btnSelesai.text = "Confirm Pickup (Arrived at Patient)"
                        updateTargetMarker(targetLocation!!, "pasien")
                    }
                } else if (status == "picked_up") {
                    if (currentMode != "antar") isRouteZoomed = false
                    currentMode = "antar"
                    updateTripStepper(2)
                    binding.btnGantiRumahSakit.visibility = View.VISIBLE

                    val lat = doc.getDouble("hospitalLat") ?: 0.0
                    val lng = doc.getDouble("hospitalLng") ?: 0.0
                    if (lat != 0.0 && lng != 0.0) {
                        val newTarget = LatLng(lat, lng)
                        // Tujuan RS berubah (driver mengalihkan ke RS lain di tengah jalan) -
                        // paksa gambar ulang rute ke tujuan baru, bukan cuma sekali di awal.
                        if (targetLocation != newTarget) isRouteZoomed = false
                        targetLocation = newTarget
                        binding.btnSelesai.text = "Complete Trip (Arrived at Hospital)"
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
        binding.tvPassengerName.text = fallbackName ?: "Passenger"

        db.collection("users").document(passengerId).get()
            .addOnSuccessListener { doc ->
                if (_binding == null || doc == null || !doc.exists()) return@addOnSuccessListener

                binding.tvPassengerName.text = doc.getString("nama") ?: fallbackName ?: "Passenger"
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
        binding.btnBatalkanPesanan.visibility = View.VISIBLE

        targetMarker?.remove()

        val iconRes = if (type == "rs") R.drawable.ic_hospital else R.drawable.ic_person_marker
        val titleStr = if (type == "rs") "Destination Hospital" else "Pickup Location"

        targetMarker = mMap.addMarker(MarkerOptions()
            .position(pos)
            .title(titleStr)
            .icon(getResizedBitmapDescriptor(iconRes)))
    }

    // Panggil Directions API paling cepat tiap MIN_ROUTE_REQUEST_INTERVAL_MS. Dipakai dari update
    // lokasi (tiap 3 detik) - permintaan yang memang wajib langsung (etape baru dari listener
    // booking) tetap memanggil drawRouteWithDirections() secara langsung.
    private fun requestRouteThrottled(start: LatLng, end: LatLng, mode: String, fitCamera: Boolean = true) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRouteRequestAt < MIN_ROUTE_REQUEST_INTERVAL_MS) return
        drawRouteWithDirections(start, end, mode, fitCamera)
    }

    private fun drawRouteWithDirections(start: LatLng, end: LatLng, mode: String, fitCamera: Boolean = true) {
        lastRouteRequestAt = SystemClock.elapsedRealtime()
        val requestSeq = ++routeRequestSeq
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

                            durationText = legs.getJSONObject("duration").getString("text")
                            distanceText = legs.getJSONObject("distance").getString("text")
                            distanceMeters = legs.getJSONObject("distance").getDouble("value")
                            durationSeconds = legs.getJSONObject("duration").getDouble("value")
                            path = decodePolyline(overviewPolyline)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DRIVER_MAPS", "Directions API Error: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                // Respons basi (sudah ada permintaan yang lebih baru, atau etape sudah berganti) -
                // abaikan supaya tidak menggambar ulang rute dari posisi lama.
                if (requestSeq != routeRequestSeq || mode != currentMode) return@withContext
                roadOverlay?.remove()

                val lineColor = if (mode == "jemput") Color.RED else Color.BLUE

                if (isApiSuccess && path.isNotEmpty()) {
                    roadOverlay = mMap.addPolyline(
                        PolylineOptions().addAll(path).width(14f).color(lineColor)
                            .jointType(JointType.ROUND).startCap(RoundCap()).endCap(RoundCap())
                    )

                    val statusStr = if (mode == "jemput") "Picking Up Patient" else "Heading to Hospital"
                    binding.tvCurrentStatus.text = statusStr
                    binding.tvEstimationInfo.text = "Estimate: $durationText ($distanceText)"
                    lastLegDistanceMeters = distanceMeters
                    lastLegDurationSeconds = durationSeconds
                    currentRoutePath = path

                    // isRouteZoomed cuma menahan supaya Directions API tidak dipanggil ulang tiap 3 detik
                    // (boros kuota) — kamera sendiri tetap disesuaikan tiap update lewat fitCameraToRoute()
                    // di setupLocationUpdates(), bukan cuma sekali di sini.
                    isRouteZoomed = true
                    if (fitCamera) fitCameraToRoute(start, end, path) else followDriverCamera(start)
                } else {
                    roadOverlay = mMap.addPolyline(PolylineOptions().add(start, end).width(10f).color(lineColor).pattern(listOf(Dash(20f), Gap(10f))))
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
                    val statusStr = if (mode == "jemput") "Picking Up Patient" else "Heading to Hospital"
                    binding.tvCurrentStatus.text = statusStr
                    binding.tvEstimationInfo.text = "Remaining Distance: ${String.format("%.1f", results[0]/1000)} km"
                    lastLegDistanceMeters = null
                    lastLegDurationSeconds = null
                    currentRoutePath = emptyList()
                    // Tetap masuk mode "mengikuti driver" walau API gagal: update lokasi berikutnya
                    // lewat trimTraveledRoute() yang mencoba ambil rute lagi (dibatasi jeda) tanpa
                    // zoom-out berulang, dan deteksi "sudah sampai" tetap jalan - sama seperti pov
                    // passenger saat Directions API gagal.
                    isRouteZoomed = true
                    if (fitCamera) fitCameraToRoute(start, end) else followDriverCamera(start)
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

    // Kamera mengikuti posisi driver dari dekat (radius ~200m), dipakai untuk update lokasi
    // yang berkelanjutan SETELAH rute lengkap sudah ditampilkan sekali di awal etape - beda dari
    // fitCameraToRoute() yang selalu menampilkan seluruh rute (dipakai cuma sekali di awal).
    private fun followDriverCamera(pos: LatLng) {
        if (_binding == null) return
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, DRIVER_FOLLOW_ZOOM))
    }

    // Memotong bagian polyline yang sudah dilewati driver secara LOKAL (tanpa panggil Directions
    // API lagi). Perhitungannya ada di RouteGeometry.trim(): posisi driver diproyeksikan ke ruas
    // jalur terdekat sehingga garis terpotong tepat di posisi ambulans. currentRoutePath ikut
    // dipotong permanen supaya progresnya maju terus (tidak "kembali" karena noise GPS kecil).
    private fun trimTraveledRoute(currentPos: LatLng, target: LatLng) {
        if (_binding == null) return

        // Rute belum ada - biasanya karena pengambilan ulang (keluar jalur) gagal dan yang tampil
        // cuma garis putus-putus sementara. Sebelumnya fungsi ini langsung return, sehingga garis
        // putus-putus dari posisi lama tertinggal terus di peta & tidak pernah diambil ulang.
        // Sekarang dicoba lagi (dibatasi jeda), sama seperti pov passenger (updateTrackedRoute).
        if (currentRoutePath.size < 2) {
            requestRouteThrottled(currentPos, target, currentMode, fitCamera = false)
            return
        }

        val trim = RouteGeometry.trim(currentRoutePath.toRoutePoints(), currentPos.toRoutePoint()) ?: return

        // Driver keluar jalur (salah belok / ambil jalan lain) - jalur lama sudah tidak relevan,
        // minta rute baru dari posisi sekarang tanpa me-zoom-out kamera.
        if (trim.distanceFromRouteMeters > OFF_ROUTE_METERS) {
            requestRouteThrottled(currentPos, target, currentMode, fitCamera = false)
            return
        }

        val remaining = trim.remaining.toLatLngs()
        currentRoutePath = remaining
        // Ganti titik-titik polyline yang sudah ada (bukan hapus lalu gambar ulang) supaya garis
        // tidak berkedip tiap update.
        val overlay = roadOverlay
        if (overlay != null) {
            overlay.points = remaining
        } else {
            val lineColor = if (currentMode == "jemput") Color.RED else Color.BLUE
            roadOverlay = mMap.addPolyline(
                PolylineOptions().addAll(remaining).width(14f).color(lineColor)
                    .jointType(JointType.ROUND).startCap(RoundCap()).endCap(RoundCap())
            )
        }
    }

    // Marker ambulans meluncur halus ke posisi baru dan berputar mengikuti arah gerak - sama
    // seperti ikon mobil di Gojek/Grab - alih-alih "loncat" tiap update lokasi.
    private fun updateDriverMarker(currentPos: LatLng, previousPos: LatLng?) {
        val marker = driverMarker
        if (marker == null) {
            driverMarker = mMap.addMarker(MarkerOptions()
                .position(currentPos)
                .icon(VehicleMarkerIcon.create(requireContext(), R.drawable.ic_ambulance_topview))
                .anchor(0.5f, 0.5f)
                .flat(true)
                .zIndex(1f))
            return
        }

        // Rotasi cuma diperbarui kalau memang bergerak cukup jauh - kalau diam, arah dari noise
        // GPS kecil bisa bikin ikon "gemetar" berputar-putar sendiri di tempat.
        val newRotation = previousPos
            ?.takeIf { RouteGeometry.distanceMeters(it.toRoutePoint(), currentPos.toRoutePoint()) > 3.0 }
            ?.let { RouteGeometry.bearingDegrees(it.toRoutePoint(), currentPos.toRoutePoint()) }
        driverMarkerAnimator.animateTo(marker, currentPos, newRotation)
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
            ?.takeIf { it.isNotBlank() && it != "Passenger Name" } ?: "Passenger"
        val sender = Person.Builder().setName(passengerName).build()

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
        val channel = NotificationChannel(CHAT_CHANNEL_ID, "Chat Messages", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notification for new chat messages during an active booking"
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
        showTripNotification("You've Arrived", "You have arrived at the patient's pickup location.")
    }

    // ID notifikasi tetap PER BOOKING (bukan per teks pesan) supaya tiap tahap perjalanan
    // ("Sudah Sampai" -> "Pasien Dijemput" -> "Perjalanan Selesai") MENGGANTI notifikasi tahap
    // sebelumnya di tray, bukan menumpuk jadi banyak notifikasi terpisah yang tidak pernah
    // hilang sendiri kalau tidak di-swipe manual satu-satu.
    private fun tripNotificationId(bookingId: String) = ("trip_$bookingId").hashCode()

    private fun showTripNotification(title: String, message: String) {
        if (_binding == null) return
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val bookingId = currentBookingId ?: return

        createTripNotificationChannel()

        val notification = NotificationCompat.Builder(requireContext(), TRIP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(requireContext())
            .notify(tripNotificationId(bookingId), notification)
    }

    private fun createTripNotificationChannel() {
        val manager = requireContext().getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(TRIP_CHANNEL_ID, "Trip Status", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Notifications for key trip milestones (arrived, picked up, completed)"
        }
        manager.createNotificationChannel(channel)
    }

    private fun konfirmasiJemput() {
        currentBookingId?.let { id ->
            binding.btnSelesai.isEnabled = false
            db.collection("bookings").document(id).update("status", "picked_up")
                .addOnSuccessListener {
                    binding.btnSelesai.isEnabled = true
                    Toast.makeText(requireContext(), "Patient picked up. Heading to the hospital!", Toast.LENGTH_SHORT).show()
                    showTripNotification("Patient Picked Up", "Patient successfully picked up. Heading to the hospital.")
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
                    updateTripStepper(3)
                    Toast.makeText(requireContext(), "Task Complete! Back online.", Toast.LENGTH_LONG).show()
                    showTripNotification("Trip Completed", "Task complete. You're back online.")
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

    // Driver dianggap punya kapabilitas menilai kegawatdaruratan pasien - kalau di tengah
    // perjalanan ternyata butuh RS dengan fasilitas lebih lengkap, driver bisa mengalihkan
    // tujuan. Cukup 1x konfirmasi (bukan 2x seperti batalkan pesanan) karena ini bukan tindakan
    // destruktif - booking tetap jalan, cuma tujuannya yang berubah, dan passenger diberi tahu.
    private fun confirmChangeHospital(name: String, lat: Double, lng: Double) {
        AlertDialog.Builder(requireContext())
            .setTitle("Change Hospital Destination?")
            .setMessage("Change the destination to \"$name\"? This will be relayed to the passenger immediately.")
            .setPositiveButton("Yes, Change") { _, _ -> changeHospital(name, lat, lng) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Cukup update field tujuan di booking - fetchActiveBooking() (listener real-time yang sudah
    // ada) otomatis mendeteksi perubahan ini & menggambar ulang rute ke tujuan baru, jadi tidak
    // perlu duplikasi logika update peta di sini.
    private fun changeHospital(name: String, lat: Double, lng: Double) {
        val id = currentBookingId ?: return
        db.collection("bookings").document(id)
            .update(
                mapOf(
                    "hospitalName" to name,
                    "hospitalLat" to lat,
                    "hospitalLng" to lng
                )
            )
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                Toast.makeText(requireContext(), "Destination hospital changed to $name", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(requireContext(), "Failed to change destination: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Tombol darurat untuk driver membatalkan pesanan di TENGAH perjalanan (kecelakaan, ban
    // bocor, dll) - sengaja 2x konfirmasi berurutan supaya tidak ke-tap tidak sengaja, karena
    // efeknya nyata: penumpang kehilangan driver yang sudah menuju/menjemputnya.
    private fun confirmCancelBookingStep1() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Booking?")
            .setMessage("Use this ONLY in an emergency (e.g. accident, flat tire) that prevents you from continuing the trip. The passenger will be notified and can look for another ambulance.")
            .setPositiveButton("Continue") { _, _ -> confirmCancelBookingStep2() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmCancelBookingStep2() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm One More Time")
            .setMessage("Are you sure you want to cancel this booking now? This action cannot be undone.")
            .setPositiveButton("Yes, Cancel Booking") { _, _ -> cancelBookingByDriver() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun cancelBookingByDriver() {
        val id = currentBookingId ?: return
        binding.btnBatalkanPesanan.isEnabled = false

        db.collection("bookings").document(id)
            .update(
                mapOf(
                    "status" to "CANCELLED",
                    // Dipakai passenger (ActivityDrive.kt) untuk membedakan pembatalan darurat
                    // oleh driver dari pembatalan oleh passenger sendiri - keduanya sama-sama
                    // berstatus CANCELLED, tapi cuma yang ini perlu menawarkan cari ambulans lain
                    // DAN yang masuk ke riwayat chat (lihat ChatFragment.kt/ChatFragmentDriver.kt).
                    "cancelledBy" to "driver",
                    // Dasar hitungan H+3 sebelum riwayatnya disembunyikan otomatis - sama seperti
                    // completedAt untuk pesanan yang selesai normal.
                    "cancelledAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                if (_binding == null) return@addOnSuccessListener
                Toast.makeText(requireContext(), "Booking cancelled. You're back online.", Toast.LENGTH_LONG).show()
                // Bersihkan notifikasi tahap perjalanan terakhir (mis. "Sudah Sampai") - resetToStandbyMode()
                // sengaja tidak menyentuh notifikasi trip supaya notifikasi "Perjalanan Selesai" tetap ada
                // untuk kasus selesai normal, tapi di sini tidak ada notifikasi pengganti yang menandakan usai.
                NotificationManagerCompat.from(requireContext()).cancel(tripNotificationId(id))
                resetToStandbyMode()

                try {
                    val switchOnline = requireActivity().findViewById<SwitchCompat>(R.id.switchOnline)
                    val statusText = requireActivity().findViewById<TextView>(R.id.statusText)
                    val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_navigation)

                    switchOnline?.isChecked = true
                    statusText?.text = "Status: Online"
                    bottomNav?.selectedItemId = R.id.nav_home
                } catch (e: Exception) {
                    Log.e("CANCEL_BOOKING", "Gagal auto-online: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                binding.btnBatalkanPesanan.isEnabled = true
                Toast.makeText(requireContext(), "Failed to cancel: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Tandai tahap aktif (pink), tahap yang sudah lewat (hijau), tahap yang belum (abu-abu).
    // stage: 1=Jemput, 2=Menuju RS, 3=Selesai.
    private fun updateTripStepper(activeStage: Int) {
        if (_binding == null) return
        val green = Color.parseColor("#2E7D32")
        val gray = Color.parseColor("#E0E0E0")
        val pink = ContextCompat.getColor(requireContext(), R.color.pink_main)

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

    private fun resetToStandbyMode() {
        roadOverlay?.remove()
        roadOverlay = null
        routeRequestSeq++ // batalkan respons rute yang masih di jalan
        targetMarker?.remove()
        targetLocation = null
        currentBookingId?.let { cancelChatNotification(it) }
        currentBookingId = null
        isRouteZoomed = false
        hasNotifiedArrival = false
        lastFetchedPassengerId = null
        passengerPhoneNumber = null
        lastLegDistanceMeters = null
        lastLegDurationSeconds = null
        currentRoutePath = emptyList()

        binding.btnSelesai.visibility = View.GONE
        binding.btnGantiRumahSakit.visibility = View.GONE
        binding.btnBatalkanPesanan.visibility = View.GONE
        binding.btnChatPassenger?.visibility = View.GONE
        binding.tvUnreadBadge.visibility = View.GONE // Reset Badge
        binding.layoutPassengerInfo.visibility = View.GONE
        binding.tvCurrentStatus.text = "Ready to accept bookings..."
        binding.tvEstimationInfo.text = ""

        unreadListener?.remove() // Hapus Listener Badge
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        unreadListener?.remove() // Pembersihan tambahan
        driverMarkerAnimator.cancel()
        _binding = null
    }
}