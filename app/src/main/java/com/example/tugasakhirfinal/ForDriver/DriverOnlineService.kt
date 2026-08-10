package com.example.tugasakhirfinal.ForDriver

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.example.tugasakhirfinal.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration

// Foreground Service supaya driver tetap tercatat Online (lokasi terus terkirim ke Firestore) dan
// tetap diberi tahu ada pesanan baru meski aplikasi di-background/keluar dari recent apps.
// Satu-satunya yang menyalakan/mematikan service ini adalah toggle switchOnline di DriverHomeActivity.
class DriverOnlineService : Service() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var bookingListener: ListenerRegistration? = null
    private var isFirstBookingSnapshot = true
    private var nextBookingNotificationId = BOOKING_NOTIFICATION_ID_BASE

    // bookingId -> notificationId, supaya notifikasi bisa dibatalkan lagi begitu booking itu
    // tidak lagi SEARCHING (dibatalkan passenger, kedaluwarsa, atau sudah diambil driver lain).
    private val activeBookingNotifications = mutableMapOf<String, Int>()

    // Kategori ambulans driver ini - dibaca sekali di fetchDriverCategoryThenListenBookings()
    // sebelum listenToNewBookings() dipasang, supaya query pesanan sudah tersaring sejak awal.
    private var driverAmbulanceCategory: String = "Ambulans Biasa"

    companion object {
        private const val STATUS_CHANNEL_ID = "driver_status_channel"
        private const val BOOKING_CHANNEL_ID = "driver_booking_channel"
        private const val STATUS_NOTIFICATION_ID = 1001
        private const val BOOKING_NOTIFICATION_ID_BASE = 2000
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Wajib dipanggil segera setelah service mulai (batas waktu sistem ~5 detik).
        ServiceCompat.startForeground(
            this,
            STATUS_NOTIFICATION_ID,
            buildStatusNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        updateDriverStatus("online")
        startLocationUpdates()
        fetchDriverCategoryThenListenBookings()

        return START_NOT_STICKY
    }

    // Baca kategori ambulans driver sendiri dulu (sekali, cukup untuk masa hidup service ini)
    // sebelum memasang listener pesanan - supaya notifikasi cuma dikirim untuk booking dengan
    // kategori yang sama. Kalau gagal dibaca (mis. offline), tetap fallback ke "Ambulans Biasa".
    private fun fetchDriverCategoryThenListenBookings() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            listenToNewBookings()
            return
        }
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                driverAmbulanceCategory = doc.getString("ambulanceCategory") ?: "Ambulans Biasa"
                listenToNewBookings()
            }
            .addOnFailureListener {
                listenToNewBookings()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        bookingListener?.remove()
        bookingListener = null
        updateDriverStatus("offline")

        // Notifikasi "Pesanan Baru" adalah notifikasi TERPISAH dari notifikasi persisten foreground
        // service — sistem cuma otomatis membersihkan notifikasi persisten saat service berhenti,
        // notifikasi pesanan yang masih aktif harus dibatalkan manual di sini juga.
        val manager = NotificationManagerCompat.from(this)
        for (notificationId in activeBookingNotifications.values) {
            manager.cancel(notificationId)
        }
        activeBookingNotifications.clear()
    }

    // 🛠️ PENTING: onDestroy() TIDAK dijamin terpanggil kalau OS/pengguna membunuh proses app
    // secara paksa (mis. "Clear all" / swipe dari daftar Recent Apps) — beda dengan app ditutup
    // secara normal. onTaskRemoved() adalah callback yang memang ditujukan untuk kasus ini, jadi
    // status Firestore & notifikasi tidak nyangkut "Online" padahal app sudah dipaksa keluar total.
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val statusChannel = NotificationChannel(
            STATUS_CHANNEL_ID, "Driver Online Status", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while the driver is Online"
            setShowBadge(false)
        }

        val bookingChannel = NotificationChannel(
            BOOKING_CHANNEL_ID, "New Ambulance Bookings", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification when a new ambulance booking comes in"
        }

        manager.createNotificationChannel(statusChannel)
        manager.createNotificationChannel(bookingChannel)
    }

    private fun openAppPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(this, DriverHomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildStatusNotification(): Notification {
        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("Ambulansku")
            .setContentText("You're Online — ready to accept bookings")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent(0))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val uid = auth.currentUser?.uid ?: return
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                firestore.collection("users").document(uid).update("lokasi", geoPoint)
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    private fun updateDriverStatus(status: String) {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid).update("status", status)
    }

    // Deteksi pesanan SEARCHING yang baru masuk (bukan yang sudah ada duluan saat listener baru
    // menyala) dan tampilkan notifikasi heads-up untuk masing-masing. Sebaliknya, kalau sebuah
    // booking KELUAR dari hasil query ini (dibatalkan passenger, kedaluwarsa, atau sudah diambil
    // driver lain), Firestore melaporkannya sebagai event REMOVED — dipakai untuk membatalkan
    // notifikasi terkait supaya tidak nyangkut menampilkan pesanan yang sudah tidak relevan.
    private fun listenToNewBookings() {
        isFirstBookingSnapshot = true
        activeBookingNotifications.clear()
        bookingListener?.remove()
        bookingListener = firestore.collection("bookings")
            .whereEqualTo("status", "SEARCHING")
            .whereEqualTo("ambulanceCategory", driverAmbulanceCategory)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                // Snapshot pertama berisi semua pesanan yang sudah ada sebelumnya — jangan dianggap "baru".
                if (isFirstBookingSnapshot) {
                    isFirstBookingSnapshot = false
                    return@addSnapshotListener
                }

                for (change in snapshot.documentChanges) {
                    when (change.type) {
                        DocumentChange.Type.ADDED -> {
                            val hospitalName = change.document.getString("hospitalName") ?: "Hospital"
                            showNewBookingNotification(change.document.id, hospitalName)
                        }
                        DocumentChange.Type.REMOVED -> {
                            dismissBookingNotification(change.document.id)
                        }
                        else -> Unit
                    }
                }
            }
    }

    private fun showNewBookingNotification(bookingId: String, hospitalName: String) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val notificationId = nextBookingNotificationId++
        val notification = NotificationCompat.Builder(this, BOOKING_CHANNEL_ID)
            .setContentTitle("New Ambulance Booking!")
            .setContentText("Destination: $hospitalName. Open the app to accept.")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent(notificationId))
            .build()

        NotificationManagerCompat.from(this).notify(notificationId, notification)
        activeBookingNotifications[bookingId] = notificationId
    }

    private fun dismissBookingNotification(bookingId: String) {
        activeBookingNotifications.remove(bookingId)?.let { notificationId ->
            NotificationManagerCompat.from(this).cancel(notificationId)
        }
    }
}
