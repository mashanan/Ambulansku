package com.example.tugasakhirfinal.ForPassenger.DriveFragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R

class HospitalAdapter(
    private var hospitalList: List<Hospital>,
    private val onItemClick: (Hospital) -> Unit
) : RecyclerView.Adapter<HospitalAdapter.HospitalViewHolder>() {

    class HospitalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvHospitalName)
        val tvAddress: TextView = itemView.findViewById(R.id.tvHospitalAddress)
        val tvPhone: TextView = itemView.findViewById(R.id.tvHospitalPhone)
        val layoutDistance: LinearLayout = itemView.findViewById(R.id.layoutHospitalDistance)
        val tvDistance: TextView = itemView.findViewById(R.id.tvHospitalDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hospital, parent, false)
        return HospitalViewHolder(view)
    }

    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        val hospital = hospitalList[position]

        // Menggunakan properti bahasa Indonesia milik model Hospital
        holder.tvName.text = hospital.nama.ifEmpty { "No Name" }
        holder.tvAddress.text = hospital.alamat.ifEmpty { "-" }
        holder.tvPhone.text = hospital.telepon.ifEmpty { "-" }

        if (hospital.distanceMeters >= 0) {
            holder.layoutDistance.visibility = View.VISIBLE
            val km = hospital.distanceMeters / 1000f
            holder.tvDistance.text = "${String.format("%.1f", km)} km • ~${hospital.estimatedMinutes} min"
        } else {
            holder.layoutDistance.visibility = View.GONE
        }

        // Event Klik Item
        holder.itemView.setOnClickListener {
            onItemClick(hospital)
        }
    }

    override fun getItemCount(): Int = hospitalList.size

    fun updateList(newList: List<Hospital>) {
        hospitalList = newList
        notifyDataSetChanged()
    }
}