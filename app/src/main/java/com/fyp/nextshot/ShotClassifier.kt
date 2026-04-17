package com.fyp.nextshot

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Rule-based cricket shot classifier optimized for SIDE-VIEW footage.
 *
 * COCO keypoint indices:
 *  0=Nose, 1=L.Eye, 2=R.Eye,
 *  5=L.Shoulder, 6=R.Shoulder,
 *  7=L.Elbow, 8=R.Elbow, 9=L.Wrist, 10=R.Wrist,
 *  11=L.Hip, 12=R.Hip, 13=L.Knee, 14=R.Knee,
 *  15=L.Ankle, 16=R.Ankle
 *
 * SIDE-VIEW STRATEGY:
 *  • Wrist HEIGHT (vertical position) → Drive vs Defensive vs Scoop
 *  • Wrist DEPTH (horizontal reach) → Forward aggression vs Back-foot
 *  • FRONT KNEE BEND → Indicates weight distribution and shot type
 *  • BODY LEAN → Forward drive vs Back-foot pull/cut
 */
object ShotClassifier {

    private const val CONF_THRESHOLD = 0.25f

    fun classify(keypoints: List<Keypoint>): String {
        fun pt(idx: Int): Pair<Float, Float>? {
            val k = keypoints.getOrNull(idx) ?: return null
            return if (k.confidence >= CONF_THRESHOLD) k.x to k.y else null
        }

        // ── Extract key pose landmarks ──────────────────────────────────────
        val nose       = pt(0)
        val lShoulder  = pt(5)
        val rShoulder  = pt(6)
        val lElbow     = pt(7)
        val rElbow     = pt(8)
        val lWrist     = pt(9)
        val rWrist     = pt(10)
        val lHip       = pt(11)
        val rHip       = pt(12)
        val lKnee      = pt(13)
        val rKnee      = pt(14)
        val lAnkle     = pt(15)
        val rAnkle     = pt(16)

        // Minimum viable detections
        if (lWrist == null && rWrist == null) return ""
        if (lShoulder == null || rShoulder == null) return ""

        // ── Reference frame setup (normalized to body height) ──────────────
        val shoulderMid = Pair(
            (lShoulder.first + rShoulder.first) / 2,
            (lShoulder.second + rShoulder.second) / 2
        )
        val shoulderY = shoulderMid.second
        val shoulderX = shoulderMid.first

        // Estimate body height (shoulder to ankle)
        val ankleY = if (lAnkle != null && rAnkle != null) {
            (lAnkle.second + rAnkle.second) / 2
        } else if (lAnkle != null) {
            lAnkle.second
        } else if (rAnkle != null) {
            rAnkle.second
        } else {
            shoulderY + 0.50f  // Fallback: assume 50% of frame
        }

        val bodyHeight = (ankleY - shoulderY).coerceAtLeast(0.05f)
        val hipY = if (lHip != null && rHip != null) {
            (lHip.second + rHip.second) / 2
        } else {
            shoulderY + 0.20f
        }

        // ── Wrist analysis (dominant or average of both) ────────────────────
        val wrists = listOfNotNull(lWrist, rWrist)
        if (wrists.isEmpty()) return ""

        // For side-view: prioritize the visible wrist (usually one side is clearer)
        val dominantWrist = wrists.maxByOrNull { it.first } ?: wrists[0]  // rightmost = batting wrist in right-handed
        val wristX = dominantWrist.first
        val wristY = dominantWrist.second

        // ── Normalized metrics (relative to body frame) ──────────────────────
        // Height of wrist above shoulder: negative = above, 0 = shoulder, 1 = ankle
        val wristHeightNorm = (wristY - shoulderY) / bodyHeight

        // Horizontal reach: how far wrist extends from shoulder midline
        // Positive = right (forward in side-view), negative = left (back)
        val wristDepthNorm = (wristX - shoulderX) / bodyHeight

        // ── Elbow angle (bat angle indicator) ────────────────────────────────
        val elbowAngle = if (lElbow != null && lWrist != null) {
            // Angle of forearm (elbow to wrist) relative to horizontal
            val dx = lWrist.first - lElbow.first
            val dy = lWrist.second - lElbow.second
            Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        } else if (rElbow != null && rWrist != null) {
            val dx = rWrist.first - rElbow.first
            val dy = rWrist.second - rElbow.second
            Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        } else {
            0f
        }

        // ── Body lean (shoulder to hip horizontal drift) ────────────────────
        val hipMid = Pair(
            (lHip?.first ?: shoulderX) + (rHip?.first ?: shoulderX) / 2,
            hipY
        )
        val bodyLean = (hipMid.first - shoulderX) / bodyHeight

        // ── Front knee bend (depth of crease play) ──────────────────────────
        val frontKneeBend = if (lKnee != null && rKnee != null) {
            // Which knee is forward (lower X in side-view)?
            // Knee closer to batting position (right for RHB) = front knee
            val frontKnee = if (abs(lKnee.first - shoulderX) > abs(rKnee.first - shoulderX)) {
                lKnee  // Left knee is front (RHB perspective)
            } else {
                rKnee
            }
            // Normalized position: how deep is the front knee (Y-axis compression)?
            val kneeDepth = (frontKnee.second - hipY) / (ankleY - hipY + 0.001f)
            kneeDepth > 0.60f  // True if knee is significantly bent (crease play)
        } else {
            false
        }

        // ── Wrist height categories ────────────────────────────────────────
        val wristAboveShoulder = wristHeightNorm < -0.08f  // Clearly above head
        val wristAtShoulder = wristHeightNorm in -0.08f..0.05f
        val wristAtChest = wristHeightNorm in 0.05f..0.25f
        val wristAtHip = wristHeightNorm in 0.25f..0.50f
        val wristBelowHip = wristHeightNorm > 0.50f

        // ── Wrist depth categories ────────────────────────────────────────
        val wristFarForward = wristDepthNorm > 0.35f   // Well ahead of shoulder
        val wristForward = wristDepthNorm in 0.10f..0.35f
        val wristNeutral = abs(wristDepthNorm) <= 0.10f
        val wristBackward = wristDepthNorm < -0.10f    // Behind shoulder (back-foot)

        // ── Elbow angle categories ────────────────────────────────────────
        val elbowLow = elbowAngle > 30f         // Forearm angling down (cut, scoop)
        val elbowLevel = elbowAngle in -15f..30f
        val elbowUp = elbowAngle < -15f         // Forearm angling up

        // ── Classification rules (priority order) ───────────────────────────
        return when {
            // ═════════════════════════════════════════════════════════════════
            // 1. SCOOP/RAMP — Wrist extremely low & bat angle upward
            // ═════════════════════════════════════════════════════════════════
            wristBelowHip && elbowUp && wristHeightNorm > 0.55f -> "Scoop/Ramp"

            // ═════════════════════════════════════════════════════════════════
            // 2. PULL/HOOK — Back-foot, wrist high, aggressive horizontal motion
            // ═════════════════════════════════════════════════════════════════
            wristBackward && (wristAtShoulder || wristAboveShoulder) && !frontKneeBend -> "Pull/Hook"

            // ═════════════════════════════════════════════════════════════════
            // 3. CUT SHOT — Back-foot, wrist at chest/hip level, controlled reach
            // ═════════════════════════════════════════════════════════════════
            wristBackward && (wristAtChest || wristAtHip) && !frontKneeBend -> "Cut Shot"

            // ═════════════════════════════════════════════════════════════════
            // 4. DRIVE — Front-foot, wrist forward & chest-high, front knee bent
            // ═════════════════════════════════════════════════════════════════
            wristForward && wristAtChest && frontKneeBend && bodyLean > -0.05f -> "Drive"

            // ═════════════════════════════════════════════════════════════════
            // 5. DEFENSIVE — Wrist low, minimal depth, protective stance
            // ═════════════════════════════════════════════════════════════════
            wristBelowHip && (wristNeutral || wristBackward) && elbowLevel -> "Defensive"

            // ═════════════════════════════════════════════════════════════════
            // Fallback: defensive if wrist is very low
            // ═════════════════════════════════════════════════════════════════
            wristBelowHip -> "Defensive"

            // Default: conservative
            else -> "Defensive"
        }
    }

    // ── Helper extension functions ──────────────────────────────────────────
    private fun List<Pair<Float, Float>>.avgX() = map { it.first }.average().toFloat()
    private fun List<Pair<Float, Float>>.avgY() = map { it.second }.average().toFloat()

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) =
        sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
}