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
    private val onToggleActiveClick: (AdminUserItem) -> Unit,
    private val onDeleteClick: (AdminUserItem) -> Unit
) : RecyclerView.Adapter<UserAdminAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvUserName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvUserEmail)
        val tvRole: TextView = itemView.findViewById(R.id.tvUserRole)
        val tvStatus: TextView = itemView.findViewById(R.id.tvUserStatus)
        val btnToggle: AppCompatButton = itemView.findViewById(R.id.btnToggleActive)
        val btnDelete: AppCompatButton = itemView.findViewById(R.id.btnDeleteUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user_admin, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = userList[position]
        holder.tvName.text = user.nama.ifEmpty { "No Name" }
        holder.tvEmail.text = user.email.ifEmpty { "-" }
        holder.tvRole.text = if (user.role.equals("driver", ignoreCase = true)) "Driver" else "Passenger"

        if (user.isActive) {
            holder.tvStatus.text = "Active"
            holder.tvStatus.setTextColor(Color.parseColor("#15803D"))
            holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E9F8EF")) // v2 success-soft pill
            holder.btnToggle.text = "Deactivate"
            // Warna utama app (oranye), bukan merah - supaya jelas beda dari tombol Delete Account
            // (merah) di sebelahnya. Sama dengan pola Edit/Delete di Manage Hospitals.
            holder.btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D63B27"))
        } else {
            holder.tvStatus.text = "Inactive"
            holder.tvStatus.setTextColor(Color.parseColor("#DC2626"))
            holder.tvStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FDECEC")) // v2 danger-soft pill
            holder.btnToggle.text = "Reactivate"
            holder.btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#15803D"))
        }

        holder.btnToggle.setOnClickListener {
            onToggleActiveClick(user)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(user)
        }
    }

    override fun getItemCount(): Int = userList.size

    fun updateList(newList: List<AdminUserItem>) {
        userList = newList
        notifyDataSetChanged()
    }
}
