package com.example.tugasakhirfinal.ForDriver.Chat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tugasakhirfinal.Adapter.ChatAdapter
import com.example.tugasakhirfinal.Model.ChatMessage
import com.example.tugasakhirfinal.R
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import de.hdodenhof.circleimageview.CircleImageView

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
    private lateinit var ivContactPhoto: CircleImageView
    private lateinit var tvContactName: TextView
    private lateinit var tvContactInfo: TextView
    private lateinit var btnCallContact: ImageView
    private var passengerPhoneNumber: String = ""

    private lateinit var chatAdapter: ChatAdapter
    private val messageList = ArrayList<ChatMessage>()

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentDriverId = auth.currentUser?.uid ?: ""
    private var bookingId: String = ""

    private var chatListener: ListenerRegistration? = null
    private var isChatLocked = false

    private var chip1: Chip? = null
    private var chip2: Chip? = null
    private var chip3: Chip? = null
    private var chip4: Chip? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_passenger)

        bookingId = intent.getStringExtra("BOOKING_ID") ?: ""
        if (bookingId.isEmpty()) {
            Toast.makeText(this, "Sesi chat tidak valid!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        btnBack = findViewById(R.id.btnBackPassengerChat)
        rvChat = findViewById(R.id.rvPassengerChat)
        etMessage = findViewById(R.id.etChatMessage)
        btnSend = findViewById(R.id.btnSendPassengerChat)
        ivContactPhoto = findViewById(R.id.ivContactPhoto)
        tvContactName = findViewById(R.id.tvDriverName)
        tvContactInfo = findViewById(R.id.tvDriverStatus)
        btnCallContact = findViewById(R.id.btnCallContact)

        btnBack.setOnClickListener { finish() }

        btnCallContact.setOnClickListener {
            if (passengerPhoneNumber.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$passengerPhoneNumber")))
            } else {
                Toast.makeText(this, "Nomor telepon pasien tidak tersedia", Toast.LENGTH_SHORT).show()
            }
        }

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        rvChat.layoutManager = layoutManager

        chatAdapter = ChatAdapter(messageList, currentDriverId)
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

    // Ambil data kontak passenger (nama, foto, no. tlp) supaya driver tetap bisa menghubungi
    // passenger walau chat sudah masuk mode terkunci/riwayat (mis. ada barang tertinggal).
    private fun fetchPassengerContactInfo() {
        firestore.collection("bookings").document(bookingId).get()
            .addOnSuccessListener { bookingDoc ->
                val passengerId = bookingDoc.getString("passengerId") ?: return@addOnSuccessListener
                firestore.collection("users").document(passengerId).get()
                    .addOnSuccessListener { userDoc ->
                        if (!userDoc.exists()) return@addOnSuccessListener

                        val name = userDoc.getString("nama") ?: "Pasien Ambulans"
                        val phone = userDoc.getString("phoneNumber") ?: ""
                        val photoUrl = userDoc.getString("profileImageUrl") ?: ""

                        tvContactName.text = name
                        tvContactInfo.text = if (phone.isNotEmpty()) phone else "Nomor telepon tidak tersedia"
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
                val isExpired = expiresAt != null && expiresAt.toDate().before(java.util.Date())

                if (status == "completed" || isExpired) {
                    isChatLocked = true
                    etMessage.isEnabled = false
                    etMessage.hint = "Sesi chat telah berakhir"
                    btnSend.isEnabled = false
                    btnSend.alpha = 0.5f
                    lockQuickChatChips()
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
            Toast.makeText(this, "Sesi chat telah berakhir", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentDriverId.isEmpty() || bookingId.isEmpty()) {
            Toast.makeText(this, "Sesi driver tidak valid", Toast.LENGTH_SHORT).show()
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
                Log.e("CHAT_DRIVER_ERROR", "Gagal mengirim: ${e.message}")
                Toast.makeText(this, "Gagal membalas pesan: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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