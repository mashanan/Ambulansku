package com.example.tugasakhirfinal.ForPassenger.Fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tugasakhirfinal.Adapter.PassengerChatAdapter
import com.example.tugasakhirfinal.ForPassenger.ChatPassengerActivity
import com.example.tugasakhirfinal.Model.PassengerChatHistory
import com.example.tugasakhirfinal.databinding.FragmentChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = auth.currentUser?.uid ?: ""

    private lateinit var chatAdapter: PassengerChatAdapter
    private val chatHistoryList = ArrayList<PassengerChatHistory>()
    private var chatListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        loadPassengerChatHistory()
    }

    private fun setupRecyclerView() {
        chatAdapter = PassengerChatAdapter(chatHistoryList) { selectedChat ->
            // Buka kembali obrolan saat item riwayat diklik
            val intent = Intent(requireContext(), ChatPassengerActivity::class.java)
            intent.putExtra("BOOKING_ID", selectedChat.bookingId)
            startActivity(intent)
        }

        // Catatan: Pastikan di R.layout.fragment_chat terdapat RecyclerView dengan id 'rvPassengerChatList'
        binding.rvPassengerChatList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
    }

    private fun loadPassengerChatHistory() {
        if (currentUserId.isEmpty()) return

        // Mengambil seluruh pesanan milik Penumpang ini yang statusnya di-ACC, berjalan, atau selesai
        chatListener = db.collection("bookings")
            .whereEqualTo("passengerId", currentUserId)
            .whereIn("status", listOf("accepted", "picked_up", "completed", "ACCEPTED", "PICKED_UP", "COMPLETED"))
            .addSnapshotListener { snapshot, error ->
                if (_binding == null) return@addSnapshotListener

                if (error != null) {
                    Log.e("CHAT_FRAGMENT_ERROR", "Error: ${error.message}")
                    return@addSnapshotListener
                }

                chatHistoryList.clear()

                if (snapshot != null) {
                    // Diurutkan di client (bukan .orderBy di query) supaya tidak butuh composite
                    // index Firestore. Riwayat pesanan terbaru ditaruh paling atas. Booking yang
                    // sudah "completed" lebih dari 3 hari disembunyikan dari daftar (data tetap
                    // ada di Firestore, cuma tidak ditampilkan lagi).
                    val visibleDocs = snapshot.documents
                        .filter { isVisibleInHistory(it) }
                        .sortedByDescending { it.getTimestamp("createdAt") }

                    for (doc in visibleDocs) {
                        val bookingId = doc.id
                        val hospitalName = doc.getString("hospitalName") ?: "Rumah Sakit"
                        val status = doc.getString("status") ?: ""

                        val historyItem = PassengerChatHistory(
                            bookingId = bookingId,
                            hospitalName = hospitalName,
                            status = status
                        )

                        // Ambil pratinjau pesan terakhir
                        fetchLastMessage(bookingId, historyItem)
                        chatHistoryList.add(historyItem)
                    }
                }

                // layoutEmptyChat sebelumnya tidak pernah di-toggle sama sekali (bug lama) - kalau
                // riwayat kosong, layar cuma blank tanpa pesan apapun ke user.
                binding.rvPassengerChatList.visibility = if (chatHistoryList.isNotEmpty()) View.VISIBLE else View.GONE
                binding.layoutEmptyChat.visibility = if (chatHistoryList.isNotEmpty()) View.GONE else View.VISIBLE
                chatAdapter.updateData(chatHistoryList)
            }
    }

    // Booking yang belum "completed" selalu tampil. Yang sudah completed disembunyikan begitu
    // lewat 3 hari dari completedAt (fallback ke createdAt untuk data lama sebelum field ini ada).
    private fun isVisibleInHistory(doc: DocumentSnapshot): Boolean {
        val status = doc.getString("status") ?: ""
        if (!status.equals("completed", ignoreCase = true)) return true

        val completedAt = doc.getTimestamp("completedAt") ?: doc.getTimestamp("createdAt") ?: return true
        val threeDaysAgo = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -3) }.time
        return completedAt.toDate().after(threeDaysAgo)
    }

    private fun fetchLastMessage(bookingId: String, historyItem: PassengerChatHistory) {
        db.collection("bookings")
            .document(bookingId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { query ->
                if (_binding == null) return@addOnSuccessListener
                if (!query.isEmpty) {
                    val lastMsgDoc = query.documents[0]
                    val rawMessage = lastMsgDoc.getString("message") ?: ""
                    val imageUrl = lastMsgDoc.getString("imageUrl") ?: ""
                    historyItem.lastMessage = if (rawMessage.isEmpty() && imageUrl.isNotEmpty()) "📷 Foto" else rawMessage
                    chatAdapter.notifyDataSetChanged()
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        chatListener?.remove()
        _binding = null
    }
}