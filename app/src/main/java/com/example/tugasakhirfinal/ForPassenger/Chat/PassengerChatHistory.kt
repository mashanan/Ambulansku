package com.example.tugasakhirfinal.ForPassenger.Chat

import com.google.firebase.Timestamp

data class PassengerChatHistory(
    var bookingId: String = "",
    var hospitalName: String = "",
    var driverName: String = "Ambulance Driver",
    var lastMessage: String = "",
    var lastTimestamp: Timestamp? = null,
    var status: String = "" // "accepted", "picked_up", "completed", dll.
)