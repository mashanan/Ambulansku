package com.example.tugasakhirfinal.Adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.tugasakhirfinal.Model.ChatMessage
import com.example.tugasakhirfinal.R

class ChatAdapter(
    private var messageList: List<ChatMessage>,
    private val currentUserId: String,
    private val onImageClick: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_SENT = 1
    private val VIEW_TYPE_RECEIVED = 2

    override fun getItemViewType(position: Int): Int {
        return if (messageList[position].senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chat = messageList[position]
        val hasImage = chat.imageUrl.isNotEmpty()

        if (holder is SentViewHolder) {
            bindImage(holder.ivImage, chat.imageUrl)
            holder.tvMessage.visibility = if (hasImage) View.GONE else View.VISIBLE
            holder.tvMessage.text = chat.message

            // 🚀 LOGIKA STATUS TERBACA / TERKIRIM
            // Menggunakan safe call (?.) berjaga-jaga jika kamu belum menambahkan TextView di XML
            holder.tvStatusRead?.visibility = View.VISIBLE
            if (chat.isRead) {
                holder.tvStatusRead?.text = "Terbaca"
                holder.tvStatusRead?.setTextColor(Color.parseColor("#2196F3")) // Warna Biru
            } else {
                holder.tvStatusRead?.text = "Terkirim"
                holder.tvStatusRead?.setTextColor(Color.parseColor("#9E9E9E")) // Warna Abu-abu
            }

        } else if (holder is ReceivedViewHolder) {
            bindImage(holder.ivImage, chat.imageUrl)
            holder.tvMessage.visibility = if (hasImage) View.GONE else View.VISIBLE
            holder.tvMessage.text = chat.message
        }
    }

    private fun bindImage(imageView: ImageView, imageUrl: String) {
        if (imageUrl.isEmpty()) {
            imageView.visibility = View.GONE
            return
        }
        imageView.visibility = View.VISIBLE
        Glide.with(imageView)
            .load(imageUrl)
            .transform(CenterCrop(), RoundedCorners(24))
            .into(imageView)
        imageView.setOnClickListener { onImageClick(imageUrl) }
    }

    override fun getItemCount(): Int = messageList.size

    fun updateMessages(newMessages: List<ChatMessage>) {
        messageList = newMessages
        notifyDataSetChanged()
    }

    class SentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvSentMessage)
        val ivImage: ImageView = itemView.findViewById(R.id.ivSentImage)
        // 🚀 TAMBAHKAN REFERENSI TEXTVIEW STATUS
        val tvStatusRead: TextView? = itemView.findViewById(R.id.tvStatusRead)
    }

    class ReceivedViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMessage: TextView = itemView.findViewById(R.id.tvReceivedMessage)
        val ivImage: ImageView = itemView.findViewById(R.id.ivReceivedImage)
    }
}
