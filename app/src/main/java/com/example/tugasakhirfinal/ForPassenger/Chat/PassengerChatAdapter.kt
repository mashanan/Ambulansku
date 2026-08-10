package com.example.tugasakhirfinal.ForPassenger.Chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R

class PassengerChatAdapter(
    private var chatList: List<PassengerChatHistory>,
    private val onItemClick: (PassengerChatHistory) -> Unit
) : RecyclerView.Adapter<PassengerChatAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvHospitalName: TextView = itemView.findViewById(R.id.tvHospitalName)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        val tvStatus: TextView = itemView.findViewById(R.id.tvChatStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_passenger_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = chatList[position]
        holder.tvHospitalName.text = item.hospitalName.ifEmpty { "Ambulance " + item.driverName }
        holder.tvLastMessage.text = item.lastMessage.ifEmpty { "Tap to view the conversation" }

        if (item.status == "accepted" || item.status == "picked_up") {
            holder.tvStatus.text = "Ongoing"
            holder.tvStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
        } else if (item.status.equals("CANCELLED", ignoreCase = true)) {
            holder.tvStatus.text = "Cancelled"
            holder.tvStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
        } else {
            holder.tvStatus.text = "Completed"
            holder.tvStatus.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = chatList.size

    fun updateData(newList: List<PassengerChatHistory>) {
        chatList = newList
        notifyDataSetChanged()
    }
}