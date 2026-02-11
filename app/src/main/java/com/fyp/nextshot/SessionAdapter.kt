package com.fyp.nextshot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fyp.nextshot.data.local.models.SessionEntity
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SessionAdapter(
    // 1. Change input type from SessionData to SessionEntity
    private val sessions: MutableList<SessionEntity>,
    private val onViewAnalysisClick: (SessionEntity) -> Unit,
    private val onShareClick: (SessionEntity) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    // Helper function to format date
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val sessionTitle: TextView = itemView.findViewById(R.id.session_title)
        val sessionDate: TextView = itemView.findViewById(R.id.session_date)
        val sessionScore: TextView = itemView.findViewById(R.id.session_score)
        val sessionAccuracy: TextView = itemView.findViewById(R.id.session_accuracy)
        val sessionDuration: TextView = itemView.findViewById(R.id.session_duration)
        val sessionShots: TextView = itemView.findViewById(R.id.session_shots)
        val btnViewAnalysis: MaterialButton = itemView.findViewById(R.id.btn_view_analysis)
        val btnShare: MaterialButton = itemView.findViewById(R.id.btn_share)
        val analysisSummaryLayout: View = itemView.findViewById(R.id.analysis_summary_layout)
        val tvAnalysisDetails: TextView = itemView.findViewById(R.id.tv_analysis_details)

        // 2. Change bind parameter type
        fun bind(session: SessionEntity) {

            // Map SessionEntity properties to UI elements:

            // SessionEntity.drillType -> SessionData.title
            sessionTitle.text = session.drillType

            // SessionEntity.dateMillis -> SessionData.date
            val formattedDate = dateFormat.format(Date(session.dateMillis))
            sessionDate.text = formattedDate

            // SessionEntity.successRate (0.0 to 1.0) -> SessionData.score/accuracy
            val accuracyPercent = (session.successRate * 100).toInt()
            sessionScore.text = accuracyPercent.toString() // Using accuracy as "Score"
            sessionAccuracy.text = "${accuracyPercent}%"

            // SessionEntity.durationSeconds -> SessionData.duration (convert to minutes)
            val durationMinutes = TimeUnit.SECONDS.toMinutes(session.durationSeconds.toLong())
            sessionDuration.text = durationMinutes.toString()

            // SessionEntity doesn't explicitly have 'shots', so we use duration as placeholder or add a default
            sessionShots.text = "${session.durationSeconds / 5} shots" // Example calculation for shots

            // Click Listeners (types are now SessionEntity)
            btnViewAnalysis.setOnClickListener {
                onViewAnalysisClick(session)
            }

            btnShare.setOnClickListener {
                onShareClick(session)
            }

            // Bind Analysis Details
            if (session.drillType == "Pose Analysis" && session.flawDetails != null) {
                analysisSummaryLayout.visibility = View.VISIBLE
                tvAnalysisDetails.text = session.flawDetails
            } else {
                analysisSummaryLayout.visibility = View.GONE
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