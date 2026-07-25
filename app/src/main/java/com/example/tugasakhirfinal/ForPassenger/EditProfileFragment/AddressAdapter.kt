package com.example.tugasakhirfinal.ForPassenger.EditProfileFragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R

class AddressAdapter(
    private val addressList: ArrayList<Address>,
    private val onFavoriteClick: (Address) -> Unit,
    private val onEditClick: (Address) -> Unit,
    private val onSetPrimaryClick: (Address) -> Unit,
    private val onDeleteClick: (Address) -> Unit // Parameter Baru untuk Hapus
) : RecyclerView.Adapter<AddressAdapter.AddressViewHolder>() {

    class AddressViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLabel: TextView = itemView.findViewById(R.id.tvLabel)
        val tvFullAddress: TextView = itemView.findViewById(R.id.tvFullAddress)
        val tvNote: TextView = itemView.findViewById(R.id.tvNote)
        val tvPrimaryBadge: TextView = itemView.findViewById(R.id.tvPrimaryBadge)
        val btnFavorite: ImageView = itemView.findViewById(R.id.btnFavorite)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEdit)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDelete)
        val btnSetPrimary: Button = itemView.findViewById(R.id.btnSetPrimary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_address, parent, false)
        return AddressViewHolder(view)
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        val currentItem = addressList[position]

        holder.tvLabel.text = currentItem.label
        holder.tvFullAddress.text = currentItem.fullAddress

        if (currentItem.note.isNotEmpty()) {
            holder.tvNote.visibility = View.VISIBLE
            holder.tvNote.text = "Patokan: ${currentItem.note}"
        } else {
            holder.tvNote.visibility = View.GONE
        }

        // Tampilan Alamat Utama
        if (currentItem.isPrimary) {
            holder.tvPrimaryBadge.visibility = View.VISIBLE
            holder.btnSetPrimary.visibility = View.GONE
        } else {
            holder.tvPrimaryBadge.visibility = View.GONE
            holder.btnSetPrimary.visibility = View.VISIBLE
        }

        // Tampilan Bintang Favorit
        if (currentItem.isFavorite) {
            holder.btnFavorite.setImageResource(android.R.drawable.btn_star_big_on)
        } else {
            holder.btnFavorite.setImageResource(android.R.drawable.btn_star_big_off)
        }

        // Event Listeners
        holder.btnFavorite.setOnClickListener { onFavoriteClick(currentItem) }
        holder.btnEdit.setOnClickListener { onEditClick(currentItem) }
        holder.btnSetPrimary.setOnClickListener { onSetPrimaryClick(currentItem) }
        holder.btnDelete.setOnClickListener { onDeleteClick(currentItem) } // Listener Hapus
    }

    override fun getItemCount(): Int = addressList.size
}