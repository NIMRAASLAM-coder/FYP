package com.fyp.nextshot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fyp.nextshot.data.local.models.AiTipEntity
import com.google.android.material.chip.Chip

class DynamicTipAdapter :
    ListAdapter<AiTipEntity, DynamicTipAdapter.TipViewHolder>(TipDiffCallback()) {

    class TipViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tip_title)
        val description: TextView = view.findViewById(R.id.tip_description)
        val tagChip: Chip = view.findViewById(R.id.chip_tag_1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tip_card_dynamic, parent, false)
        return TipViewHolder(view)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        val tip = getItem(position)
        holder.title.text = tip.title
        holder.description.text = tip.description
        holder.tagChip.text = tip.tag
    }

    class TipDiffCallback : DiffUtil.ItemCallback<AiTipEntity>() {
        override fun areItemsTheSame(oldItem: AiTipEntity, newItem: AiTipEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AiTipEntity, newItem: AiTipEntity): Boolean {
            return oldItem == newItem
        }
    }
}
