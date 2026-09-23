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
        source.getPixels(input, 0, width, 0, 0, width, height)
        val output = IntArray(input.size)

        for (i in input.indices) {
            val c = input[i]
            val r = ((c shr 16) and 255) / 255f
            val g = ((c shr 8) and 255) / 255f
            val b = (c and 255) / 255f

            val distance = sqrt(
                (r - keyR) * (r - keyR) +
                (g - keyG) * (g - keyG) +
                (b - keyB) * (b - keyB)
            ) / 1.7320508f

            val start = min(1f, settings.similarity)
            val end = min(1f, start + max(0.001f, settings.smoothness))
            val alpha = ((distance - start) / (end - start)).coerceIn(0f, 1f)

            val spill = (g - max(r, b)).coerceAtLeast(0f) * settings.spillSuppression
            val outR = (r + spill).coerceIn(0f, 1f)
            val outG = (g - spill).coerceIn(0f, 1f)
            val outB = (b + spill * 0.25f).coerceIn(0f, 1f)
            output[i] = (alpha * 255f).toInt().shl(24) or
                ((outR * 255f).toInt().shl(16)) or
                ((outG * 255f).toInt().shl(8)) or
                (outB * 255f).toInt()
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }
}
