package com.example.tugasakhirfinal.ForAdmin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.ForPassenger.Hospital
import com.example.tugasakhirfinal.R

class HospitalAdminAdapter(
    private var hospitalList: List<Hospital>,
    private val onDeleteClick: (Hospital) -> Unit
) : RecyclerView.Adapter<HospitalAdminAdapter.HospitalAdminViewHolder>() {

    class HospitalAdminViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvName: android.widget.TextView = itemView.findViewById(R.id.tvHospitalName)
        val tvAddress: android.widget.TextView = itemView.findViewById(R.id.tvHospitalAddress)
        val tvPhone: android.widget.TextView = itemView.findViewById(R.id.tvHospitalPhone)
        val tvIgdStatus: android.widget.TextView = itemView.findViewById(R.id.tvIgdStatus)
        val btnDelete: android.widget.Button = itemView.findViewById(R.id.btnDeleteHospital)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalAdminViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hospital_admin, parent, false)
        return HospitalAdminViewHolder(view)
    }

    override fun onBindViewHolder(holder: HospitalAdminViewHolder, position: Int) {
        val hospital = hospitalList[position]
        holder.tvName.text = hospital.nama.ifEmpty { "Tanpa Nama" }
        holder.tvAddress.text = hospital.alamat.ifEmpty { "-" }
        holder.tvPhone.text = hospital.telepon.ifEmpty { "-" }
        holder.tvIgdStatus.text = "IGD ${hospital.igdStatus}"

        holder.btnDelete.setOnClickListener {
            onDeleteClick(hospital)
        }
    }

    override fun getItemCount(): Int = hospitalList.size

    fun updateList(newList: List<Hospital>) {
        hospitalList = newList
        notifyDataSetChanged()
    }
}
