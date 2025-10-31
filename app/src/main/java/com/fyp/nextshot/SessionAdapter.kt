package com.fyp.nextshot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class SessionAdapter(
    private val sessions: List<SessionData>,
    private val onViewAnalysisClick: (SessionData) -> Unit,
    private val onShareClick: (SessionData) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sessionTitle: TextView = itemView.findViewById(R.id.session_title)
        val sessionDate: TextView = itemView.findViewById(R.id.session_date)
        val sessionScore: TextView = itemView.findViewById(R.id.session_score)
        val sessionAccuracy: TextView = itemView.findViewById(R.id.session_accuracy)
        val sessionDuration: TextView = itemView.findViewById(R.id.session_duration)
        val sessionShots: TextView = itemView.findViewById(R.id.session_shots)
        val btnViewAnalysis: MaterialButton = itemView.findViewById(R.id.btn_view_analysis)
        val btnShare: MaterialButton = itemView.findViewById(R.id.btn_share)

        fun bind(session: SessionData) {
            sessionTitle.text = session.title
            sessionDate.text = session.date
            sessionScore.text = session.score.toString()
            sessionAccuracy.text = "${session.accuracy}%"
            sessionDuration.text = session.duration.toString()
            sessionShots.text = session.shots.toString()

            btnViewAnalysis.setOnClickListener {
                onViewAnalysisClick(session)
            }

            btnShare.setOnClickListener {
                onShareClick(session)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size
}