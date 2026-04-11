package com.fyp.nextshot

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class VideoPlayerDialogFragment : DialogFragment() {

    private var videoId: String? = null
    private var playerView: YouTubePlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentDialog)
        videoId = arguments?.getString(ARG_VIDEO_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_video_player, container, false)

        playerView = view.findViewById(R.id.youtube_player_view)
        val btnClose = view.findViewById<MaterialButton>(R.id.btn_close)

        playerView?.let { ipv ->
            lifecycle.addObserver(ipv)
            
            // Setting a proper origin can sometimes resolve embedding restricted errors (150/403)
            val options = IFramePlayerOptions.Builder()
                .controls(1)
                .rel(0)
                .ivLoadPolicy(3)
                .ccLoadPolicy(1)
                .build()

            ipv.initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    videoId?.let { id ->
                        Log.d("VideoPlayerDialog", "Ready to load video: $id")
                        // Try cueVideo first if loadVideo is being blocked by autoplay policies
                        // or if it triggers embedding errors immediately.
                        // However, loadVideo is usually fine if the user clicked something.
                        youTubePlayer.loadVideo(id, 0f)
                    }
                }

                override fun onError(youTubePlayer: YouTubePlayer, error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError) {
                    Log.e("VideoPlayerDialog", "YouTube Error: $error")
                    // If we get an error, we can try to fallback or notify the user
                }
            }, options)
        }

        btnClose.setOnClickListener {
            dismiss()
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    companion object {
        private const val ARG_VIDEO_ID = "video_id"

        fun newInstance(videoId: String): VideoPlayerDialogFragment {
            val fragment = VideoPlayerDialogFragment()
            val args = Bundle()
            args.putString(ARG_VIDEO_ID, videoId)
            fragment.arguments = args
            return fragment
        }
    }
}
