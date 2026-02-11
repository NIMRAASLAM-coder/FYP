package com.fyp.nextshot

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

data class Detection(
    val label: String,
    val confidence: Float,
    val bbox: RectF,
    val keypoints: List<Keypoint> = emptyList()  // uses the shared Keypoint class
)

class BoundingBoxOverlay : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private var imageWidth = 0f
    private var imageHeight = 0f

    fun setImageSize(w: Int, h: Int) {
        imageWidth = w.toFloat()
        imageHeight = h.toFloat()
    }

    private var detections: List<Detection> = emptyList()

    fun setDetections(newDetections: List<Detection>) {
        detections = newDetections
        invalidate()
    }

    fun clear() {
        detections = emptyList()
        invalidate()
    }

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.CYAN
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 30f
        isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private val linePaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val pointPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 1f
        style = Paint.Style.FILL
    }

    private val skeleton = listOf(
        // Nose to eyes
        0 to 1, 0 to 2,
        // Eyes to ears
        1 to 3, 2 to 4,
        // Shoulders
        5 to 6,
        // Arms
        5 to 7, 7 to 9,       // left: shoulder → elbow → wrist
        6 to 8, 8 to 10,      // right: shoulder → elbow → wrist
        // Torso
        5 to 11, 6 to 12,     // shoulders to hips
        11 to 12,
        // Legs
        11 to 13, 13 to 15,    // left: hip → knee → ankle
        12 to 14, 14 to 16     // right: hip → knee → ankle
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() || imageWidth == 0f || imageHeight == 0f) return

        val scale = min(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        for (det in detections) {
            val left = det.bbox.left * imageWidth * scale + offsetX
            val top = det.bbox.top * imageHeight * scale + offsetY
            val right = det.bbox.right * imageWidth * scale + offsetX
            val bottom = det.bbox.bottom * imageHeight * scale + offsetY

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val text = "${det.label.uppercase()} ${(det.confidence * 100).toInt()}%"
            canvas.drawText(text, left + 20, top + 60, textPaint)

            // Draw keypoints
            det.keypoints.forEach { kp ->
                if (kp.confidence > 0.3f) {
                    val cx = kp.x * imageWidth * scale + offsetX
                    val cy = kp.y * imageHeight * scale + offsetY
                    canvas.drawCircle(cx, cy, 6f, pointPaint)
                }
            }

            // Draw skeleton
            skeleton.forEach { (i, j) ->
                val kp1 = det.keypoints.getOrNull(i) ?: return@forEach
                val kp2 = det.keypoints.getOrNull(j) ?: return@forEach
                if (kp1.confidence > 0.3f && kp2.confidence > 0.3f) {
                    val x1 = kp1.x * imageWidth * scale + offsetX
                    val y1 = kp1.y * imageHeight * scale + offsetY
                    val x2 = kp2.x * imageWidth * scale + offsetX
                    val y2 = kp2.y * imageHeight * scale + offsetY
                    canvas.drawLine(x1, y1, x2, y2, linePaint)
                }
            }
        }
    }
}