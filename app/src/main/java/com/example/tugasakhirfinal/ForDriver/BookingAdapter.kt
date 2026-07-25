package com.example.tugasakhirfinal.Adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.Model.Booking
import com.example.tugasakhirfinal.databinding.ItemBookingDriverBinding

class BookingAdapter(
    private val bookings: List<Booking>,
    private val onAcceptClick: (Booking) -> Unit
) : RecyclerView.Adapter<BookingAdapter.BookingViewHolder>() {

    inner class BookingViewHolder(val binding: ItemBookingDriverBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder {
        val binding = ItemBookingDriverBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return BookingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        val booking = bookings[position]
        holder.binding.apply {
            tvPassengerName.text = booking.passengerName
            tvLocation.text = "Lokasi: ${booking.locationName}"
            tvDistance.text = if (booking.distanceMeters >= 0) {
                "Jarak: ${String.format("%.1f", booking.distanceMeters / 1000)} km"
            } else {
                "Jarak: -"
            }

            btnTerima.setOnClickListener {
                onAcceptClick(booking)
            }
        }
    }

    override fun getItemCount(): Int = bookings.size
}