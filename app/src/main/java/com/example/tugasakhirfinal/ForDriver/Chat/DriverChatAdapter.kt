package com.example.tugasakhirfinal.ForDriver.Chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.ForPassenger.Chat.PassengerChatHistory
import com.example.tugasakhirfinal.R

class DriverChatAdapter(
    private var chatList: List<PassengerChatHistory>,
    private val onItemClick: (PassengerChatHistory) -> Unit
) : RecyclerView.Adapter<DriverChatAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPassengerName: TextView = itemView.findViewById(R.id.tvPassengerName)
        val tvHospitalDestination: TextView = itemView.findViewById(R.id.tvHospitalDestination)
        val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        val tvChatStatus: TextView = itemView.findViewById(R.id.tvChatStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_driver_chat_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = chatList[position]
        holder.tvPassengerName.text = item.driverName.ifEmpty { "Ambulance Patient" } // Diisi nama pasien/penumpang
        holder.tvHospitalDestination.text = "To ${item.hospitalName}"
        holder.tvLastMessage.text = item.lastMessage.ifEmpty { "Tap to open chat" }

        // v2: status ditampilkan sebagai badge (warna teks + latar pill), sama dengan sisi passenger
        val ctx = holder.itemView.context
        val (label, textColor, bgColor) = when {
            item.status.equals("accepted", ignoreCase = true) || item.status.equals("picked_up", ignoreCase = true) ->
                Triple("Ongoing", R.color.v2_success, R.color.v2_success_soft)
            item.status.equals("CANCELLED", ignoreCase = true) ->
                Triple("Cancelled", R.color.v2_danger, R.color.v2_danger_soft)
            else -> Triple("Completed", R.color.v2_text_secondary, R.color.v2_bg_subtle)
        }
        holder.tvChatStatus.text = label
        holder.tvChatStatus.setTextColor(ctx.getColor(textColor))
        holder.tvChatStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(ctx.getColor(bgColor))

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = chatList.size

    fun updateData(newList: List<PassengerChatHistory>) {
        chatList = newList
        notifyDataSetChanged()
    }
}