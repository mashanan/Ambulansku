package com.example.tugasakhirfinal.ForAdmin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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
        val ivStnkPhoto: ImageView = itemView.findViewById(R.id.ivStnkPhoto)
        val containerAmbulancePhotos: LinearLayout = itemView.findViewById(R.id.containerAmbulancePhotos)
        val ivAedCert: ImageView = itemView.findViewById(R.id.ivAedCert)
        val tvAedMissing: TextView = itemView.findViewById(R.id.tvAedMissing)
        val ivMedicalCert: ImageView = itemView.findViewById(R.id.ivMedicalCert)
        val tvMedicalMissing: TextView = itemView.findViewById(R.id.tvMedicalMissing)
        val tvSopSirine: TextView = itemView.findViewById(R.id.tvSopSirine)
        val tvSopEquipment: TextView = itemView.findViewById(R.id.tvSopEquipment)
        val tvSopApd: TextView = itemView.findViewById(R.id.tvSopApd)
        val tvSopHealthCheck: TextView = itemView.findViewById(R.id.tvSopHealthCheck)
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
        holder.tvAmbulancePlate.text = "No. Plat / STNK: ${driver.ambulancePlateNumber.ifEmpty { "-" }}"

        Glide.with(holder.itemView).load(driver.licensePhotoUrl).into(holder.ivLicensePhoto)
        Glide.with(holder.itemView).load(driver.stnkPhotoUrl).into(holder.ivStnkPhoto)

        holder.ivLicensePhoto.setOnClickListener {
            if (driver.licensePhotoUrl.isNotEmpty()) onPhotoClick(driver.licensePhotoUrl)
        }
        holder.ivStnkPhoto.setOnClickListener {
            if (driver.stnkPhotoUrl.isNotEmpty()) onPhotoClick(driver.stnkPhotoUrl)
        }

        bindAmbulancePhotos(holder, driver.ambulancePhotoUrls)
        bindOptionalCert(holder.ivAedCert, holder.tvAedMissing, driver.aedCertificateUrl)
        bindOptionalCert(holder.ivMedicalCert, holder.tvMedicalMissing, driver.medicalCertificateUrl)

        bindSopLine(holder.tvSopSirine, "Etika & peraturan sirine", driver.sopSirineChecked)
        bindSopLine(holder.tvSopEquipment, "Penguasaan alat utama ambulans", driver.sopEquipmentChecked)
        bindSopLine(holder.tvSopApd, "Penggunaan APD dasar", driver.sopApdChecked)
        bindSopLine(holder.tvSopHealthCheck, "Tes kesehatan berkala", driver.sopHealthCheckChecked)

        holder.btnApprove.setOnClickListener { onApproveClick(driver) }
        holder.btnReject.setOnClickListener { onRejectClick(driver) }
    }

    // Foto kondisi ambulans bisa lebih dari satu, jadi thumbnail-nya di-inflate dinamis
    // ke dalam container horizontal (read-only, tap untuk lihat penuh).
    private fun bindAmbulancePhotos(holder: PendingDriverViewHolder, urls: List<String>) {
        val context = holder.itemView.context
        holder.containerAmbulancePhotos.removeAllViews()

        val sizePx = (90 * context.resources.displayMetrics.density).toInt()
        val marginPx = (8 * context.resources.displayMetrics.density).toInt()

        for (url in urls) {
            val thumb = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply { marginEnd = marginPx }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFFEEEEEE.toInt())
                setOnClickListener { onPhotoClick(url) }
            }
            Glide.with(context).load(url).into(thumb)
            holder.containerAmbulancePhotos.addView(thumb)
        }
    }

    private fun bindOptionalCert(imageView: ImageView, missingLabel: TextView, url: String) {
        if (url.isNotEmpty()) {
            imageView.visibility = View.VISIBLE
            missingLabel.visibility = View.GONE
            Glide.with(imageView.context).load(url).into(imageView)
            imageView.setOnClickListener { onPhotoClick(url) }
        } else {
            imageView.visibility = View.GONE
            missingLabel.visibility = View.VISIBLE
        }
    }

    private fun bindSopLine(textView: TextView, label: String, checked: Boolean) {
        if (checked) {
            textView.text = "✓ $label"
            textView.setTextColor(0xFF2E7D32.toInt())
        } else {
            textView.text = "✗ $label"
            textView.setTextColor(0xFFD32F2F.toInt())
        }
    }

    override fun getItemCount(): Int = driverList.size

    fun updateList(newList: List<PendingDriverItem>) {
        driverList = newList
        notifyDataSetChanged()
    }
}
