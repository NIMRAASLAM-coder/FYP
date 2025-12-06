package com.fyp.nextshot

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

data class Detection(
    val label: String,
    val confidence: Float,
    val bbox: RectF  // in normalized 0..1 coordinates
)

class BoundingBoxOverlay : View {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private var detections: List<Detection> = ArrayList()
    private var imageWidth = 1
    private var imageHeight = 1

    fun updateDetections(
        detections: List<Detection>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        this.detections = detections
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun clear() {
        detections = ArrayList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (detections.isEmpty()) return

        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        val scale = min(scaleX, scaleY)

        val offsetX = (width - imageWidth * scale) / 2f
        val offsetY = (height - imageHeight * scale) / 2f

        for (det in detections) {
            val left = det.bbox.left * imageWidth * scale + offsetX
            val top = det.bbox.top * imageHeight * scale + offsetY
            val right = det.bbox.right * imageWidth * scale + offsetX
            val bottom = det.bbox.bottom * imageHeight * scale + offsetY

            paint.color = getColorForLabel(det.label)
            canvas.drawRect(left, top, right, bottom, paint)

            val text = "${det.label} ${(det.confidence * 100).toInt()}%"
            // val textWidth = textPaint.measureText(text) // Unused variable
            canvas.drawText(text, left + 12, top + 48, textPaint)
        }
    }

    private fun getColorForLabel(label: String): Int {
        return when (label.lowercase()) {
            "ball" -> Color.RED
            "player", "person" -> Color.CYAN
            "hoop", "basket" -> Color.YELLOW
            "shooter" -> Color.MAGENTA
            else -> Color.GREEN
        }
    }
}
