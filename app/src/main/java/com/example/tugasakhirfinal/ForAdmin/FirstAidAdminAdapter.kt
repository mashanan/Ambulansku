package com.example.tugasakhirfinal.ForAdmin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.ForPassenger.FirstAid.FirstAidCondition
import com.example.tugasakhirfinal.R

class FirstAidAdminAdapter(
    private var conditionList: List<FirstAidCondition>,
    private val onEditClick: (FirstAidCondition) -> Unit,
    private val onDeleteClick: (FirstAidCondition) -> Unit
) : RecyclerView.Adapter<FirstAidAdminAdapter.FirstAidAdminViewHolder>() {

    class FirstAidAdminViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvConditionTitle)
        val tvSymptoms: TextView = itemView.findViewById(R.id.tvConditionSymptoms)
        val tvStepCount: TextView = itemView.findViewById(R.id.tvConditionStepCount)
        val btnEdit: Button = itemView.findViewById(R.id.btnEditFirstAid)
        val btnDelete: Button = itemView.findViewById(R.id.btnDeleteFirstAid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirstAidAdminViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_first_aid_admin, parent, false)
        return FirstAidAdminViewHolder(view)
    }

    override fun onBindViewHolder(holder: FirstAidAdminViewHolder, position: Int) {
        val condition = conditionList[position]
        holder.tvTitle.text = condition.title.ifEmpty { "Tanpa Nama" }
        holder.tvSymptoms.text = condition.symptoms.ifEmpty { "-" }
        holder.tvStepCount.text = "${condition.steps.size} langkah pertolongan"

        holder.btnEdit.setOnClickListener { onEditClick(condition) }
        holder.btnDelete.setOnClickListener { onDeleteClick(condition) }
    }

    override fun getItemCount(): Int = conditionList.size

    fun updateList(newList: List<FirstAidCondition>) {
        conditionList = newList
        notifyDataSetChanged()
    }
}
