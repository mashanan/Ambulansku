package com.example.tugasakhirfinal.ForDriver.Chat

import android.Manifest
import android.app.ProgressDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tugasakhirfinal.Chat.ChatAdapter
import com.example.tugasakhirfinal.Chat.ChatMessage
import com.example.tugasakhirfinal.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import de.hdodenhof.circleimageview.CircleImageView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

private const val FILE_PROVIDER_AUTHORITY = "com.example.tugasakhirfinal.fileprovider"

class ChatDriverActivity : AppCompatActivity() {

    companion object {
        // Dipakai layar lain (mis. ActivityDriveFragmentDriver) untuk tahu apakah chat booking
        // tertentu SEDANG dilihat driver, supaya tidak perlu tampilkan notifikasi untuk itu.
        var openBookingId: String? = null
    }

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnBack: ImageView
    private lateinit var btnAttachPhoto: ImageView
    private lateinit var ivContactPhoto: CircleImageView
    private lateinit var tvContactName: TextView
    private lateinit var tvContactInfo: TextView
    private lateinit var btnCallContact: ImageView
    private lateinit var tvHistoryExpiryNotice: TextView
    private var passengerPhoneNumber: String = ""

    private lateinit var chatAdapter: ChatAdapter
    private val messageList = ArrayList<ChatMessage>()

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://tugasakhirfinal-9e73d.firebasestorage.app")
    private val auth = FirebaseAuth.getInstance()
    private val currentDriverId = auth.currentUser?.uid ?: ""
    private var bookingId: String = ""

    private var chatListener: ListenerRegistration? = null
    private var isChatLocked = false

    private var chip1: Chip? = null
    private var chip2: Chip? = null
    private var chip3: Chip? = null
    private var chip4: Chip? = null

    private var latestCapturedUri: Uri? = null

    private val progressDialog by lazy {
        ProgressDialog(this).apply {
            setMessage("Sending photo...")
            setCancelable(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_passenger)
        latestCapturedUri = savedInstanceState?.getParcelable("latestCapturedUri")

        bookingId = intent.getStringExtra("BOOKING_ID") ?: ""
        if (bookingId.isEmpty()) {
            Toast.makeText(this, "Invalid chat session!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnBack = findViewById(R.id.btnBackPassengerChat)
        rvChat = findViewById(R.id.rvPassengerChat)
        etMessage = findViewById(R.id.etChatMessage)
        btnSend = findViewById(R.id.btnSendPassengerChat)
        btnAttachPhoto = findViewById(R.id.btnAttachPhoto)
        ivContactPhoto = findViewById(R.id.ivContactPhoto)
        tvContactName = findViewById(R.id.tvDriverName)
        tvContactInfo = findViewById(R.id.tvDriverStatus)
        btnCallContact = findViewById(R.id.btnCallContact)
        tvHistoryExpiryNotice = findViewById(R.id.tvHistoryExpiryNotice)

        btnBack.setOnClickListener { finish() }

        btnCallContact.setOnClickListener {
            if (passengerPhoneNumber.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$passengerPhoneNumber")))
            } else {
                Toast.makeText(this, "Passenger phone number not available", Toast.LENGTH_SHORT).show()
            }
        }

        btnAttachPhoto.setOnClickListener {
            if (isChatLocked) {
                Toast.makeText(this, "Chat session has ended", Toast.LENGTH_SHORT).show()
            } else {
                checkCameraPermissionThenShowOptions()
            }
        }

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        rvChat.layoutManager = layoutManager

        chatAdapter = ChatAdapter(messageList, currentDriverId, onImageClick = { url -> showImageDialog(url) })
        rvChat.adapter = chatAdapter

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }

        setupQuickChatChips()
        checkBookingStatusForChat() // 🚀 Cek apakah tugas sudah selesai / terkunci
        listenChatMessages()
        fetchPassengerContactInfo()
    }

    // --- PILIH SUMBER FOTO (GALERI / KAMERA) --- reuse pola yang sama dipakai ganti foto profil.
    private fun checkCameraPermissionThenShowOptions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            showPhotoOptionsDialog()
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) showPhotoOptionsDialog()
    }

    private fun showPhotoOptionsDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.activity_edit_foto_profile, null)
        view.findViewById<TextView>(R.id.editProfileTitle).text = "Send Photo"

        view.findViewById<Button>(R.id.selectImageButton).setOnClickListener {
            galleryLauncher.launch("image/*")
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.captureImageButton).setOnClickListener {
            openCamera()
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.cancelButton).setOnClickListener { dialog.dismiss() }
        dialog.setContentView(view)
        dialog.show()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadChatImage(it) }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = latestCapturedUri
        if (success && uri != null) {
            uploadChatImage(uri)
        } else if (success) {
            Toast.makeText(this, "Photo capture session ended, try again", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        try {
            val uri = getNewImageUri()
            latestCapturedUri = uri
            cameraLauncher.launch(uri)
        } catch (e: IOException) {
            Log.e("DriverChat", e.message.toString())
        }
    }

    private fun getNewImageUri(): Uri {
        val photoFile = File.createTempFile("IMG_CHAT_DRV_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
        return FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, photoFile)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        latestCapturedUri?.let { outState.putParcelable("latestCapturedUri", it) }
    }

    // Viewer foto full-screen (bukan popup kecil) - tombol tutup berbentuk X ditumpuk di pojok
    // kanan atas foto, latar hitam penuh layar supaya foto terlihat sejelas mungkin.
    private fun showImageDialog(url: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_full_image)

        val ivFullImage = dialog.findViewById<ImageView>(R.id.ivFullImage)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseFullImage)

        Glide.with(this)
            .load(url)
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e("DriverChat", "Failed to load photo $url: ${e?.message}", e)
                    Toast.makeText(this@ChatDriverActivity, "Failed to load photo: ${e?.rootCauses?.firstOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    return false
                }
                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean
                ): Boolean = false
            })
            .into(ivFullImage)

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // Foto chat diperkecil dulu (sama seperti foto profil) supaya upload cepat & hemat kuota -
    // decode langsung ukuran kecil via inSampleSize, bukan decode penuh baru di-resize belakangan.
    private fun uploadChatImage(uri: Uri) {
        if (isChatLocked) {
            Toast.makeText(this, "Chat session has ended", Toast.LENGTH_SHORT).show()
            return
        }
        if (currentDriverId.isEmpty() || bookingId.isEmpty()) {
            Toast.makeText(this, "Invalid driver session", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            var bitmap = decodeSampledBitmapFromUri(uri, 800)
            if (bitmap == null) {
                Toast.makeText(this, "Failed to process photo", Toast.LENGTH_SHORT).show()
                return
            }

            val ei = ExifInterface(contentResolver.openInputStream(uri)!!)
            val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap = resizeBitmap(bitmap, 800)

            progressDialog.show()

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)

            val fileName = "${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference.child("chat_images/$bookingId/$fileName")
            storageRef.putBytes(baos.toByteArray())
                .addOnSuccessListener {
                    storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        val messageData = hashMapOf(
                            "senderId" to currentDriverId,
                            "senderRole" to "DRIVER",
                            "message" to "",
                            "imageUrl" to downloadUri.toString(),
                            "timestamp" to FieldValue.serverTimestamp(),
                            "isRead" to false
                        )
                        firestore.collection("bookings").document(bookingId).collection("messages")
                            .add(messageData)
                            .addOnSuccessListener { progressDialog.dismiss() }
                            .addOnFailureListener { e ->
                                progressDialog.dismiss()
                                Toast.makeText(this, "Failed to send photo: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    Toast.makeText(this, "Failed to upload photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            progressDialog.dismiss()
            Log.e("DriverChat", e.message.toString())
            Toast.makeText(this, "Failed to process photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, reqSize: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }

        var inSampleSize = 1
        var halfWidth = boundsOptions.outWidth / 2
        var halfHeight = boundsOptions.outHeight / 2
        while (halfWidth / inSampleSize >= reqSize && halfHeight / inSampleSize >= reqSize) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val decodeStream = contentResolver.openInputStream(uri) ?: return null
        return decodeStream.use { BitmapFactory.decodeStream(it, null, decodeOptions) }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val (newWidth, newHeight) = if (width >= height) {
            maxDimension to (maxDimension / ratio).toInt()
        } else {
            (maxDimension * ratio).toInt() to maxDimension
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // Ambil data kontak passenger (nama, foto, no. tlp) supaya driver tetap bisa menghubungi
    // passenger walau chat sudah masuk mode terkunci/riwayat (mis. ada barang tertinggal).
    private fun fetchPassengerContactInfo() {
        firestore.collection("bookings").document(bookingId).get()
            .addOnSuccessListener { bookingDoc ->
                val passengerId = bookingDoc.getString("passengerId") ?: return@addOnSuccessListener
                firestore.collection("users").document(passengerId).get()
                    .addOnSuccessListener { userDoc ->
                        if (!userDoc.exists()) return@addOnSuccessListener

                        val name = userDoc.getString("nama") ?: "Ambulance Patient"
                        val phone = userDoc.getString("phoneNumber") ?: ""
                        val photoUrl = userDoc.getString("profileImageUrl") ?: ""

                        tvContactName.text = name
                        tvContactInfo.text = if (phone.isNotEmpty()) phone else "Phone number not available"
                        passengerPhoneNumber = phone
                        btnCallContact.visibility = if (phone.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE

                        if (photoUrl.isNotEmpty()) {
                            Glide.with(this).load(photoUrl).placeholder(R.drawable.foto_profil)
                                .into(ivContactPhoto)
                        }
                    }
            }
    }

    // 🚀 Fungsi untuk mengunci chat jika status pesanan sudah completed
    private fun checkBookingStatusForChat() {
        firestore.collection("bookings").document(bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val status = snapshot.getString("status") ?: ""
                val expiresAt = snapshot.getTimestamp("expiresAt")
                val isCancelled = status.equals("CANCELLED", ignoreCase = true)
                val isExpired = expiresAt != null && expiresAt.toDate().before(java.util.Date())

                if (status == "completed" || isCancelled || isExpired) {
                    isChatLocked = true
                    etMessage.isEnabled = false
                    etMessage.hint = if (isCancelled) "Booking has been cancelled" else "Chat session has ended"
                    btnSend.isEnabled = false
                    btnSend.alpha = 0.5f
                    btnAttachPhoto.isEnabled = false
                    btnAttachPhoto.alpha = 0.5f
                    lockQuickChatChips()
                }

                // Kalau trip sudah completed/dibatalkan, kasih tahu kapan riwayat chat ini bakal
                // disembunyikan dari tampilan (H+3 sejak completedAt/cancelledAt) - hitungannya
                // harus persis sama dengan isVisibleInHistory() di ChatFragmentDriver.kt supaya
                // tanggalnya tidak menyesatkan.
                if (status == "completed" || isCancelled) {
                    val referenceTime = snapshot.getTimestamp("completedAt")
                        ?: snapshot.getTimestamp("cancelledAt")
                        ?: snapshot.getTimestamp("createdAt")
                    if (referenceTime != null) {
                        val hideDate = java.util.Calendar.getInstance().apply {
                            time = referenceTime.toDate()
                            add(java.util.Calendar.DAY_OF_YEAR, 3)
                        }.time
                        val formatter = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id", "ID"))
                        val prefix = if (isCancelled) "This booking was cancelled as an emergency. Chat history" else "This chat history"
                        tvHistoryExpiryNotice.text = "$prefix will be hidden automatically on ${formatter.format(hideDate)}"
                        tvHistoryExpiryNotice.visibility = android.view.View.VISIBLE
                    }
                }
            }
    }

    private fun lockQuickChatChips() {
        for (chip in listOf(chip1, chip2, chip3, chip4)) {
            chip?.isEnabled = false
            chip?.alpha = 0.5f
        }
    }

    private fun setupQuickChatChips() {
        chip1 = findViewById(R.id.chip1)
        chip2 = findViewById(R.id.chip2)
        chip3 = findViewById(R.id.chip3)
        chip4 = findViewById(R.id.chip4)

        chip1?.setOnClickListener { sendMessage(chip1?.text.toString()) }
        chip2?.setOnClickListener { sendMessage(chip2?.text.toString()) }
        chip3?.setOnClickListener { sendMessage(chip3?.text.toString()) }
        chip4?.setOnClickListener { sendMessage(chip4?.text.toString()) }
    }

    private fun sendMessage(text: String) {
        if (isChatLocked) {
            Toast.makeText(this, "Chat session has ended", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentDriverId.isEmpty() || bookingId.isEmpty()) {
            Toast.makeText(this, "Invalid driver session", Toast.LENGTH_SHORT).show()
            return
        }

        val messageData = hashMapOf(
            "senderId" to currentDriverId,
            "senderRole" to "DRIVER",
            "message" to text,
            "timestamp" to FieldValue.serverTimestamp(),
            "isRead" to false
        )

        firestore.collection("bookings")
            .document(bookingId)
            .collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                etMessage.setText("")
            }
            .addOnFailureListener { e ->
                Log.e("CHAT_DRIVER_ERROR", "Failed to send: ${e.message}")
                Toast.makeText(this, "Failed to reply to message: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun listenChatMessages() {
        chatListener?.remove()

        chatListener = firestore.collection("bookings")
            .document(bookingId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                if (snapshot != null) {
                    messageList.clear()
                    for (doc in snapshot.documents) {
                        val chat = doc.toObject(ChatMessage::class.java)
                        if (chat != null) {
                            chat.id = doc.id
                            chat.isRead = doc.getBoolean("isRead") ?: false
                            messageList.add(chat)

                            if (chat.senderId != currentDriverId && !chat.isRead) {
                                doc.reference.update("isRead", true)
                            }
                        }
                    }
                    chatAdapter.updateMessages(messageList)
                    if (messageList.isNotEmpty()) {
                        rvChat.smoothScrollToPosition(messageList.size - 1)
                    }
                }
            }
    }

    override fun onResume() {
        super.onResume()
        openBookingId = bookingId

        // Notifikasi chat sebelumnya cuma dibatalkan lewat listener di ActivityDriveFragmentDriver
        // (layar peta) - kalau chat dibuka lewat tab Chat (bukan dari layar peta), listener itu
        // tidak aktif jadi notifikasi tidak pernah hilang meski pesannya sudah terbaca. Dibatalkan
        // langsung di sini supaya selalu hilang begitu chat ini dibuka, dari jalur manapun.
        NotificationManagerCompat.from(this).cancel(bookingId.hashCode())
    }

    override fun onPause() {
        super.onPause()
        if (openBookingId == bookingId) openBookingId = null
    }

    override fun onDestroy() {
        super.onDestroy()
        chatListener?.remove()
    }
}
