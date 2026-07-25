package com.example.tugasakhirfinal.ForDriver.Chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.Model.PassengerChatHistory
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
        holder.tvPassengerName.text = item.driverName.ifEmpty { "Pasien Ambulans" } // Diisi nama pasien/penumpang
        holder.tvHospitalDestination.text = "Tujuan: ${item.hospitalName}"
        holder.tvLastMessage.text = item.lastMessage.ifEmpty { "Klik untuk membuka obrolan" }

        if (item.status.equals("accepted", ignoreCase = true) || item.status.equals("picked_up", ignoreCase = true)) {
            holder.tvChatStatus.text = "Berlangsung"
            holder.tvChatStatus.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
        } else {
            holder.tvChatStatus.text = "Selesai"
            holder.tvChatStatus.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
        }

        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = chatList.size

    fun updateData(newList: List<PassengerChatHistory>) {
        chatList = newList
        notifyDataSetChanged()
    }
}