package com.miladtak.japo.tracking

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

data class PersonDetection(
    val bounds: RectF,
    val confidence: Float = 1f
)

data class TrackedPerson(
    val id: Int,
    val bounds: RectF,
    val confidence: Float
)

class PersonTracker(
    private val iouThreshold: Float = 0.25f,
    private val maxMissingFrames: Int = 12
) {
    private data class Track(
        val id: Int,
        var bounds: RectF,
        var confidence: Float,
        var missing: Int
    )

    private val tracks = mutableListOf<Track>()
    private var nextId = 1

    fun reset() {
        tracks.clear()
        nextId = 1
    }

    @Synchronized
    fun update(detections: List<PersonDetection>): List<TrackedPerson> {
        val used = BooleanArray(detections.size)

        for (track in tracks) {
            var bestIndex = -1
            var bestIou = 0f
            for (i in detections.indices) {
                if (used[i]) continue
                val score = iou(track.bounds, detections[i].bounds)
                if (score > bestIou) {
                    bestIou = score
                    bestIndex = i
                }
            }

            if (bestIndex >= 0 && bestIou >= iouThreshold) {
                val detection = detections[bestIndex]
                track.bounds = RectF(detection.bounds)
                track.confidence = detection.confidence
                track.missing = 0
                used[bestIndex] = true
            } else {
                track.missing++
            }
        }

        tracks.removeAll { it.missing > maxMissingFrames }

        for (i in detections.indices) {
            if (!used[i]) {
                val detection = detections[i]
                tracks.add(Track(nextId++, RectF(detection.bounds), detection.confidence, 0))
            }
        }

        return tracks
            .sortedBy { it.id }
            .map { TrackedPerson(it.id, RectF(it.bounds), it.confidence) }
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersection = max(0f, right - left) * max(0f, bottom - top)
        if (intersection <= 0f) return 0f
        val union = a.width() * a.height() + b.width() * b.height() - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
