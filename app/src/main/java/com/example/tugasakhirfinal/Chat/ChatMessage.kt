package com.example.tugasakhirfinal.Model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class ChatMessage(
    var id: String = "",
    var senderId: String = "",
    var senderRole: String = "",
    var message: String = "",
    var imageUrl: String = "",
    var timestamp: Timestamp? = null,

    // Paksa Firestore membaca dan menulis dengan nama "isRead" persis
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false
)