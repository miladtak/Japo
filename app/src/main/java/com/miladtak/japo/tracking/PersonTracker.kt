package com.miladtak.japo.tracking

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

data class PersonDetection(
    val bounds: RectF,
    val confidence: Float = 1f
) {
    init {
        require(bounds.width() > 0f && bounds.height() > 0f) { "Detection bounds must be non-empty" }
        require(confidence in 0f..1f) { "Confidence must be between 0 and 1" }
    }
}

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

    init {
        require(iouThreshold in 0f..1f)
        require(maxMissingFrames >= 0)
    }

    fun reset() {
        synchronized(this) {
            tracks.clear()
            nextId = 1
        }
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
                val d = detections[bestIndex]
                track.bounds = RectF(d.bounds)
                track.confidence = d.confidence
                track.missing = 0
                used[bestIndex] = true
            } else {
                track.missing++
            }
        }

        tracks.removeAll { it.missing > maxMissingFrames }

        for (i in detections.indices) {
            if (!used[i]) {
                val d = detections[i]
                tracks.add(Track(nextId++, RectF(d.bounds), d.confidence, 0))
            }
        }

        return tracks.sortedBy { it.id }
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
