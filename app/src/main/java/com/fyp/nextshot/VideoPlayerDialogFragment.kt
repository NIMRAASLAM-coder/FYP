package com.fyp.nextshot

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton

class VideoPlayerDialogFragment : DialogFragment() {

    private var videoId: String? = null
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.TransparentDialog)
        isCancelable = true
        videoId = arguments?.getString(ARG_VIDEO_ID)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_video_player, container, false)

        webView = view.findViewById(R.id.youtube_webview)
        val btnClose = view.findViewById<MaterialButton>(R.id.btn_close)

        webView?.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT

            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()

            // Load the YouTube embed URL via HTML — this works for most restricted videos
            videoId?.let { id ->
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            * { margin: 0; padding: 0; }
                            body { background: #000; }
                            .container { position: relative; width: 100%; padding-bottom: 56.25%; height: 0; overflow: hidden; }
                            .container iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <iframe 
                                src="https://www.youtube.com/embed/$id?autoplay=1&rel=0&modestbranding=1&playsinline=1" 
                                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" 
                                allowfullscreen>
                            </iframe>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null)
            }
        }

        btnClose.setOnClickListener {
            dismiss()
        }

        // Dismiss when tapping outside the card (on the dark overlay)
        val overlay = view.findViewById<FrameLayout>(R.id.dialog_overlay)
        overlay.setOnClickListener { dismiss() }

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }
    }

    override fun onDestroyView() {
        webView?.apply {
            loadUrl("about:blank")
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroyView()
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
