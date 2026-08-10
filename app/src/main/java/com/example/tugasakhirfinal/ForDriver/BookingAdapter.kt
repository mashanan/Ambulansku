package com.example.tugasakhirfinal.ForDriver

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
            tvBookingCategory.text = booking.ambulanceCategory.ifEmpty { "Ambulans Biasa" }
            tvHospitalName.text = "Destination: ${booking.hospitalName.ifEmpty { "Hospital" }}"
            tvLocation.text = "Pickup Location: ${booking.locationName.ifEmpty { "-" }}"
            tvDistance.text = if (booking.distanceMeters >= 0) {
                "Distance: ${String.format("%.1f", booking.distanceMeters / 1000)} km"
            } else {
                "Distance: -"
            }
            tvEstimasi.text = if (booking.estimatedMinutes >= 0) {
                "Estimate: ${booking.estimatedMinutes} min"
            } else {
                "Estimate: -"
            }

            btnTerima.setOnClickListener {
                onAcceptClick(booking)
            }
        }
    }

    override fun getItemCount(): Int = bookings.size
}