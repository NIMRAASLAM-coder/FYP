package com.fyp.nextshot

import kotlin.math.sqrt
import android.util.Log

class ShotEventDetector {

    private val VELOCITY_SWING_START  = 0.008f
    private val VELOCITY_SWING_HOLD   = 0.005f
    private val VELOCITY_SETTLE_ENTER = 0.004f

    private val SETTLE_FRAMES = 3
    private val MIN_SWING_FRAMES = 4
    private val CONF_THRESHOLD = 0.25f

    private enum class State { IDLE, SWINGING, SETTLING }

    private var state = State.IDLE
    private val swingLabels = mutableListOf<String>()
    private var settleCount = 0
    private var swingFrameCount = 0
    private var lastWristPos: Pair<Float, Float>? = null
    private var finalizedLabel = ""

    fun reset() {
        state = State.IDLE
        swingLabels.clear()
        settleCount = 0
        swingFrameCount = 0
        lastWristPos = null
        finalizedLabel = ""
    }

    /**
     * Call this after the last frame has been fed (e.g. video ended, session stopped).
     * Forces finalization of any in-progress swing so the label is not lost.
     */
    fun flush(): String {
        if ((state == State.SWINGING || state == State.SETTLING)
            && swingFrameCount >= MIN_SWING_FRAMES
            && swingLabels.isNotEmpty()
        ) {
            finalizedLabel = swingLabels.last()
            Log.d("SHOT_DETECTOR", "flush() finalized: $finalizedLabel (${swingLabels.size} labels from $swingFrameCount frames)")
        }
        state = State.IDLE
        swingLabels.clear()
        settleCount = 0
        swingFrameCount = 0
        return finalizedLabel
    }

    fun feed(keypoints: List<Keypoint>): String {
        val wristPos = getBestWristPos(keypoints) ?: run {
            Log.d("SHOT_DETECTOR", "No wrist found")
            return finalizedLabel
        }

        val velocity = if (lastWristPos != null) {
            val dx = wristPos.first  - lastWristPos!!.first
            val dy = wristPos.second - lastWristPos!!.second
            sqrt(dx * dx + dy * dy)
        } else 0f

        lastWristPos = wristPos

        Log.d("SHOT_DETECTOR", "state=$state velocity=${"%.4f".format(velocity)} wrist=(${wristPos.first},${wristPos.second})")

        val frameLabel = ShotClassifier.classify(keypoints)

        when (state) {
            State.IDLE -> {
                if (velocity > VELOCITY_SWING_START) {
                    state = State.SWINGING
                    swingLabels.clear()
                    swingFrameCount = 1
                    if (frameLabel.isNotEmpty()) swingLabels += frameLabel
                }
            }

            State.SWINGING -> {
                swingFrameCount++
                if (frameLabel.isNotEmpty()) swingLabels += frameLabel

                if (velocity < VELOCITY_SETTLE_ENTER) {
                    state = State.SETTLING
                    settleCount = 1
                }
            }

            State.SETTLING -> {
                if (frameLabel.isNotEmpty()) swingLabels += frameLabel

                if (velocity > VELOCITY_SWING_HOLD) {
                    state = State.SWINGING
                    settleCount = 0
                    swingFrameCount++
                } else {
                    settleCount++
                    if (settleCount >= SETTLE_FRAMES) {
                        if (swingFrameCount >= MIN_SWING_FRAMES && swingLabels.isNotEmpty()) {
                            finalizedLabel = swingLabels.last()
                        }
                        state = State.IDLE
                        swingLabels.clear()
                        settleCount = 0
                        swingFrameCount = 0
                    }
                }
            }
        }

        return finalizedLabel
    }

    private fun getBestWristPos(keypoints: List<Keypoint>): Pair<Float, Float>? {
        val lw = keypoints.getOrNull(9)?.takeIf { it.confidence >= CONF_THRESHOLD }
        val rw = keypoints.getOrNull(10)?.takeIf { it.confidence >= CONF_THRESHOLD }
        val wristResult = when {
            lw != null && rw != null -> (lw.x + rw.x) / 2f to (lw.y + rw.y) / 2f
            lw != null               -> lw.x to lw.y
            rw != null               -> rw.x to rw.y
            else                     -> null
        }
        if (wristResult != null) return wristResult

        val le = keypoints.getOrNull(7)?.takeIf { it.confidence >= CONF_THRESHOLD }
        val re = keypoints.getOrNull(8)?.takeIf { it.confidence >= CONF_THRESHOLD }
        return when {
            le != null && re != null -> (le.x + re.x) / 2f to (le.y + re.y) / 2f
            le != null               -> le.x to le.y
            re != null               -> re.x to re.y
            else                     -> null
        }
    }

    private fun majorityVote(labels: List<String>): String =
        labels.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""
}