package com.example.tugasakhirfinal.ForPassenger

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.Adapter.ChatAdapter
import com.example.tugasakhirfinal.Model.ChatMessage
import com.example.tugasakhirfinal.R
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ChatPassengerActivity : AppCompatActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnBack: ImageView

    private lateinit var chatAdapter: ChatAdapter
    private val messageList = ArrayList<ChatMessage>()

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = auth.currentUser?.uid ?: ""
    private var bookingId: String = ""

    private var chatListener: ListenerRegistration? = null

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

        btnBack.setOnClickListener { finish() }

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        rvChat.layoutManager = layoutManager

        chatAdapter = ChatAdapter(messageList, currentUserId)
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
    }

    // 🚀 Fungsi untuk mengunci chat jika status pesanan sudah completed
    private fun checkBookingStatusForChat() {
        firestore.collection("bookings").document(bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val status = snapshot.getString("status") ?: ""
                val expiresAt = snapshot.getTimestamp("expiresAt")

                // Cek apakah status sudah selesai ATAU sudah melewati batas 3 hari (expiresAt)
                val isExpired = expiresAt != null && expiresAt.toDate().before(java.util.Date())

                if (status == "completed" || isExpired) {
                    etMessage.isEnabled = false
                    etMessage.hint = "Sesi chat telah berakhir"
                    btnSend.isEnabled = false
                    btnSend.alpha = 0.5f
                }
            }
    }

    private fun setupQuickChatChips() {
        val chip1 = findViewById<Chip?>(R.id.chip1)
        val chip2 = findViewById<Chip?>(R.id.chip2)
        val chip3 = findViewById<Chip?>(R.id.chip3)
        val chip4 = findViewById<Chip?>(R.id.chip4)

        chip1?.setOnClickListener { sendMessage(chip1.text.toString()) }
        chip2?.setOnClickListener { sendMessage(chip2.text.toString()) }
        chip3?.setOnClickListener { sendMessage(chip3.text.toString()) }
        chip4?.setOnClickListener { sendMessage(chip4.text.toString()) }
    }

    private fun sendMessage(text: String) {
        if (currentUserId.isEmpty() || bookingId.isEmpty()) {
            Toast.makeText(this, "Sesi tidak valid, mohon login ulang", Toast.LENGTH_SHORT).show()
            return
        }

        val messageData = hashMapOf(
            "senderId" to currentUserId,
            "senderRole" to "PASSENGER",
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
                Log.e("CHAT_ERROR", "Gagal mengirim pesan: ${e.message}", e)
                Toast.makeText(this, "Gagal mengirim pesan: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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

                            if (chat.senderId != currentUserId && !chat.isRead) {
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

    override fun onDestroy() {
        super.onDestroy()
        chatListener?.remove()
    }
}