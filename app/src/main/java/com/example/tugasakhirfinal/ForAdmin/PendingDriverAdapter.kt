package com.example.tugasakhirfinal.ForAdmin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tugasakhirfinal.R

class PendingDriverAdapter(
    private var driverList: List<PendingDriverItem>,
    private val onApproveClick: (PendingDriverItem) -> Unit,
    private val onRejectClick: (PendingDriverItem) -> Unit,
    private val onPhotoClick: (String) -> Unit
) : RecyclerView.Adapter<PendingDriverAdapter.PendingDriverViewHolder>() {

    class PendingDriverViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvDriverName)
        val tvEmail: TextView = itemView.findViewById(R.id.tvDriverEmail)
        val tvLicenseNumber: TextView = itemView.findViewById(R.id.tvLicenseNumber)
        val tvAmbulancePlate: TextView = itemView.findViewById(R.id.tvAmbulancePlate)
        val ivLicensePhoto: ImageView = itemView.findViewById(R.id.ivLicensePhoto)
        val ivAmbulancePhoto: ImageView = itemView.findViewById(R.id.ivAmbulancePhoto)
        val btnApprove: AppCompatButton = itemView.findViewById(R.id.btnApprove)
        val btnReject: AppCompatButton = itemView.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PendingDriverViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pending_driver, parent, false)
        return PendingDriverViewHolder(view)
    }

    override fun onBindViewHolder(holder: PendingDriverViewHolder, position: Int) {
        val driver = driverList[position]
        holder.tvName.text = driver.nama.ifEmpty { "Driver Baru" }
        holder.tvEmail.text = driver.email
        holder.tvLicenseNumber.text = "No. SIM: ${driver.licenseNumber.ifEmpty { "-" }}"
        holder.tvAmbulancePlate.text = "No. Plat Ambulans: ${driver.ambulancePlateNumber.ifEmpty { "-" }}"

        Glide.with(holder.itemView).load(driver.licensePhotoUrl).into(holder.ivLicensePhoto)
        Glide.with(holder.itemView).load(driver.ambulancePhotoUrl).into(holder.ivAmbulancePhoto)

        holder.ivLicensePhoto.setOnClickListener {
            if (driver.licensePhotoUrl.isNotEmpty()) onPhotoClick(driver.licensePhotoUrl)
        }
        holder.ivAmbulancePhoto.setOnClickListener {
            if (driver.ambulancePhotoUrl.isNotEmpty()) onPhotoClick(driver.ambulancePhotoUrl)
        }

        holder.btnApprove.setOnClickListener { onApproveClick(driver) }
        holder.btnReject.setOnClickListener { onRejectClick(driver) }
    }

    override fun getItemCount(): Int = driverList.size

    fun updateList(newList: List<PendingDriverItem>) {
        driverList = newList
        notifyDataSetChanged()
    }
}
