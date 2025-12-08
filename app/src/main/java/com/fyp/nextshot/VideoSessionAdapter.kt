package com.fyp.nextshot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fyp.nextshot.data.local.models.SessionEntity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class VideoSessionAdapter(
    private val sessions: MutableList<SessionEntity>,
    private val onVideoClick: (SessionEntity) -> Unit
) : RecyclerView.Adapter<VideoSessionAdapter.VideoSessionViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    inner class VideoSessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.video_session_title)
        val date: TextView = itemView.findViewById(R.id.video_session_date)
        val duration: TextView = itemView.findViewById(R.id.video_session_duration)
        val accuracy: TextView = itemView.findViewById(R.id.video_session_accuracy)
        val playButton: MaterialButton = itemView.findViewById(R.id.btn_play_video)
        val flaws: TextView = itemView.findViewById(R.id.video_session_flaws)

        fun bind(session: SessionEntity) {
            title.text = session.drillType
            date.text = dateFormat.format(Date(session.dateMillis))
            duration.text = "Duration: ${session.durationSeconds / 60} min ${session.durationSeconds % 60} sec"
            accuracy.text = "Accuracy: ${(session.successRate * 100).roundToInt()}%"

            // Extract and display flaw details
            val flawText = session.flawDetails?.substringAfter("Flaws:")?.trim() ?: "No analysis available"
            flaws.text = flawText

            // Highlight based on analysis completion (optional)
            if (flawText.contains("FIXED")) {
                flaws.setTextColor(ContextCompat.getColor(itemView.context, R.color.words_blue)) // Use a success color
            }

            playButton.setOnClickListener {
                onVideoClick(session)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoSessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_session, parent, false)
        return VideoSessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoSessionViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    fun updateList(newSessions: List<SessionEntity>) {
        sessions.clear()
        sessions.addAll(newSessions)
        notifyDataSetChanged()
    }
}