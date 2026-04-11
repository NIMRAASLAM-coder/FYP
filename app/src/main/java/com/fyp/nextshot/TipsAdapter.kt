package com.fyp.nextshot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import java.util.regex.Pattern

class TipsAdapter(private val tips: List<Tip>, private val onTipClick: (String) -> Unit) :
    RecyclerView.Adapter<TipsAdapter.TipViewHolder>() {

    class TipViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tip_title)
        val desc: TextView = view.findViewById(R.id.tip_description)
        val category: Chip = view.findViewById(R.id.tip_category)
        val thumbnail: ImageView = view.findViewById(R.id.tip_thumbnail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tip, parent, false)
        return TipViewHolder(view)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        val tip = tips[position]
        holder.title.text = tip.title
        holder.desc.text = tip.description
        holder.category.text = tip.category

        val youtubeVideoId = extractVideoId(tip.videoUrl)
        
        val finalThumbnailUrl = if (!tip.thumbnailUrl.isNullOrEmpty()) {
            tip.thumbnailUrl
        } else if (youtubeVideoId.isNotEmpty()) {
            "https://img.youtube.com/vi/$youtubeVideoId/0.jpg"
        } else {
            null
        }

        if (finalThumbnailUrl != null) {
            Glide.with(holder.itemView.context)
                .load(finalThumbnailUrl)
                .placeholder(R.drawable.img_7)
                .error(R.drawable.img_7)
                .into(holder.thumbnail)
        } else {
            holder.thumbnail.setImageResource(tip.thumbnailResId)
        }

        holder.itemView.setOnClickListener { onTipClick(tip.videoUrl) }
    }

    override fun getItemCount() = tips.size

    companion object {
        fun extractVideoId(url: String): String {
            if (url.isBlank()) return ""
            // Updated regex to be more specific and capture exactly 11 characters
            val pattern = "(?:v=|be/|embed/|shorts/|/v/|/vi/|\\?v=|&v=)([a-zA-Z0-9_-]{11})"
            val matcher = Pattern.compile(pattern).matcher(url)
            return if (matcher.find()) {
                matcher.group(1) ?: ""
            } else {
                // Last resort fallback
                val index = url.indexOf("youtu.be/")
                if (index != -1 && url.length >= index + 9 + 11) {
                    url.substring(index + 9, index + 9 + 11)
                } else ""
            }
        }
    }
}