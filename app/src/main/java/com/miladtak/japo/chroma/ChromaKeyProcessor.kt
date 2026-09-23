package com.miladtak.japo.chroma

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ChromaKeyProcessor {
    fun removeKey(
        source: Bitmap,
        keyR: Float,
        keyG: Float,
        keyB: Float,
        settings: ChromaKeySettings = ChromaKeySettings()
    ): Bitmap {
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        val output = IntArray(input.size)
        source.getPixels(input, 0, width, 0, 0, width, height)

        val similarity = settings.similarity.coerceIn(0f, 1f)
        val threshold = settings.threshold.coerceIn(0f, 1f)
        val smoothness = max(0.001f, settings.smoothness.coerceIn(0.001f, 1f))
        val edgeSoftness = settings.edgeSoftness.coerceIn(0f, 1f)

        for (i in input.indices) {
            val c = input[i]
            val r = ((c ushr 16) and 255) / 255f
            val g = ((c ushr 8) and 255) / 255f
            val b = (c and 255) / 255f

            val distance = sqrt(
                (r - keyR) * (r - keyR) +
                (g - keyG) * (g - keyG) +
                (b - keyB) * (b - keyB)
            ) / 1.7320508f

            // Similarity is the radius around the picked key color. Threshold and
            // smoothness control the transition instead of unexpectedly enlarging
            // the keyed region and removing unrelated colors.
            val killStart = similarity.coerceIn(0f, 1f)
            val transition = max(0.001f, threshold * 0.5f + smoothness + edgeSoftness)
            val killEnd = min(1f, killStart + transition)
            var alpha = ((distance - killStart) / max(0.001f, killEnd - killStart)).coerceIn(0f, 1f)

            val spill = (g - max(r, b)).coerceAtLeast(0f) *
                settings.spillSuppression.coerceIn(0f, 1f)
            val outR = (r + spill).coerceIn(0f, 1f)
            val outG = (g - spill).coerceIn(0f, 1f)
            val outB = (b + spill * 0.25f).coerceIn(0f, 1f)

            val finalAlpha = (alpha * 255f).toInt().coerceIn(0, 255)
            output[i] = (finalAlpha shl 24) or
                ((outR * 255f).toInt().shl(16)) or
                ((outG * 255f).toInt().shl(8)) or
                (outB * 255f).toInt()
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }
}
