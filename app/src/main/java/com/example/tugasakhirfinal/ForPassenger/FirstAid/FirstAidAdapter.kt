package com.example.tugasakhirfinal.ForPassenger.FirstAid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tugasakhirfinal.R

class FirstAidAdapter(
    private val conditionList: List<FirstAidCondition>
) : RecyclerView.Adapter<FirstAidAdapter.FirstAidViewHolder>() {

    class FirstAidViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutHeader: View = itemView.findViewById(R.id.layoutHeader)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvSymptoms: TextView = itemView.findViewById(R.id.tvSymptoms)
        val tvExpandArrow: TextView = itemView.findViewById(R.id.tvExpandArrow)
        val layoutDetail: View = itemView.findViewById(R.id.layoutDetail)
        val tvSteps: TextView = itemView.findViewById(R.id.tvSteps)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirstAidViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_first_aid, parent, false)
        return FirstAidViewHolder(view)
    }

    override fun onBindViewHolder(holder: FirstAidViewHolder, position: Int) {
        val condition = conditionList[position]
        holder.tvTitle.text = condition.title
        holder.tvSymptoms.text = condition.symptoms
        holder.tvSteps.text = condition.steps.joinToString("\n") { "•  $it" }

        holder.layoutDetail.visibility = if (condition.isExpanded) View.VISIBLE else View.GONE
        holder.tvExpandArrow.text = if (condition.isExpanded) "▲" else "▼"

        holder.layoutHeader.setOnClickListener {
            condition.isExpanded = !condition.isExpanded
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = conditionList.size
}
