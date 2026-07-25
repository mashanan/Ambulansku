package com.example.tugasakhirfinal.ForPassenger

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        val tvIgdStatus: TextView = itemView.findViewById(R.id.tvIgdStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HospitalViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hospital, parent, false)
        return HospitalViewHolder(view)
    }

    override fun onBindViewHolder(holder: HospitalViewHolder, position: Int) {
        val hospital = hospitalList[position]

        // Menggunakan properti bahasa Indonesia milik model Hospital
        holder.tvName.text = hospital.nama.ifEmpty { "Tanpa Nama" }
        holder.tvAddress.text = hospital.alamat.ifEmpty { "-" }
        holder.tvPhone.text = hospital.telepon.ifEmpty { "-" }

        val status = hospital.igdStatus ?: "Tersedia"

        // Logika Tampilan & Warna Badge Status IGD
        when (status.lowercase()) {
            "tersedia" -> {
                holder.tvIgdStatus.text = "IGD Tersedia"
                setIgdStatusStyle(holder.tvIgdStatus, "#2E7D32", "#E8F5E9")
            }
            "ramai" -> {
                holder.tvIgdStatus.text = "IGD Ramai"
                setIgdStatusStyle(holder.tvIgdStatus, "#E65100", "#FFF3E0")
            }
            "penuh" -> {
                holder.tvIgdStatus.text = "IGD Penuh"
                setIgdStatusStyle(holder.tvIgdStatus, "#C62828", "#FFEBEE")
            }
            else -> {
                holder.tvIgdStatus.text = "IGD: $status"
                setIgdStatusStyle(holder.tvIgdStatus, "#424242", "#EEEEEE")
            }
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

    private fun setIgdStatusStyle(textView: TextView, textColorHex: String, bgColorHex: String) {
        textView.setTextColor(Color.parseColor(textColorHex))
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f
            setColor(Color.parseColor(bgColorHex))
        }
        textView.background = drawable
    }
}