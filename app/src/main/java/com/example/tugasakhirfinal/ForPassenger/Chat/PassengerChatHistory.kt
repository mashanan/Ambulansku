package com.example.tugasakhirfinal.Model

import com.google.firebase.Timestamp

data class PassengerChatHistory(
    var bookingId: String = "",
    var hospitalName: String = "",
    var driverName: String = "Driver Ambulans",
    var lastMessage: String = "",
    var lastTimestamp: Timestamp? = null,
    var status: String = "" // "accepted", "picked_up", "completed", dll.
)