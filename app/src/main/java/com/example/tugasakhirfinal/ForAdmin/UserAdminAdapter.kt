package com.example.tugasakhirfinal.ForAdmin

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R

class UserAdminAdapter(
    private var userList: List<AdminUserItem>,
    private val onToggleActiveClick: (AdminUserItem) -> Unit
) : RecyclerView.Adapter<UserAdminAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
        val tvRole: TextView = itemView.findViewById(R.id.tvUserRole)
        val tvStatus: TextView = itemView.findViewById(R.id.tvUserStatus)
        val btnToggle: AppCompatButton = itemView.findViewById(R.id.btnToggleActive)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_admin, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.tvName.text = user.nama.ifEmpty { "Tanpa Nama" }
        holder.tvEmail.text = user.email.ifEmpty { "-" }
        holder.tvRole.text = if (user.role.equals("driver", ignoreCase = true)) "Driver" else "Passenger"

        if (user.isActive) {
            holder.tvStatus.text = "Aktif"
            holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
            holder.tvStatus.setBackgroundColor(Color.parseColor("#E8F5E9"))
            holder.btnToggle.text = "Nonaktifkan"
            holder.btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        } else {
            holder.tvStatus.text = "Nonaktif"
            holder.tvStatus.setTextColor(Color.parseColor("#C62828"))
            holder.tvStatus.setBackgroundColor(Color.parseColor("#FFEBEE"))
            holder.btnToggle.text = "Aktifkan Lagi"
            holder.btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32"))
        }

        holder.btnToggle.setOnClickListener {
            onToggleActiveClick(user)
        }
    }

    override fun getItemCount(): Int = userList.size

    fun updateList(newList: List<AdminUserItem>) {
        userList = newList
        notifyDataSetChanged()
    }
}
