package com.example.tugasakhirfinal.ForAdmin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.ForPassenger.Hospital
import com.example.tugasakhirfinal.R

class HospitalAdminAdapter(
    private var hospitalList: List<Hospital>,
    private val onEditClick: (Hospital) -> Unit,
    private val onDeleteClick: (Hospital) -> Unit
) : RecyclerView.Adapter<HospitalAdminAdapter.HospitalAdminViewHolder>() {

    class HospitalAdminViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvHospitalName)
        val tvAddress: TextView = itemView.findViewById(R.id.tvHospitalAddress)
        val tvPhone: TextView = itemView.findViewById(R.id.tvHospitalPhone)
        val tvIgdStatus: TextView = itemView.findViewById(R.id.tvIgdStatus)
        val tvMissingLocation: TextView = itemView.findViewById(R.id.tvMissingLocation)
        val btnEdit: Button = itemView.findViewById(R.id.btnEditHospital)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteHospital)
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

        val hasLocation = hospital.getLatLng() != null
        holder.tvMissingLocation.visibility = if (hasLocation) View.GONE else View.VISIBLE

        holder.btnEdit.setOnClickListener {
            onEditClick(hospital)
        }
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
