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

        // v2: status ditampilkan sebagai badge (warna teks + latar pill)
        val ctx = holder.itemView.context
        val (label, textColor, bgColor) = when {
            item.status == "accepted" || item.status == "picked_up" ->
                Triple("Ongoing", R.color.v2_success, R.color.v2_success_soft)
            item.status.equals("CANCELLED", ignoreCase = true) ->
                Triple("Cancelled", R.color.v2_danger, R.color.v2_danger_soft)
            else -> Triple("Completed", R.color.v2_text_secondary, R.color.v2_bg_subtle)
        }
        holder.tvStatus.text = label
        holder.tvStatus.setTextColor(ctx.getColor(textColor))
        holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(bgColor))

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = chatList.size

    fun updateData(newList: List<PassengerChatHistory>) {
        chatList = newList
        notifyDataSetChanged()
    }
}