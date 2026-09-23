package com.miladtak.japo.matting

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BitmapMattingProcessor : MattingProcessor {
    override fun refine(source: Bitmap, alphaMask: Bitmap, edgeSoftness: Float): Bitmap {
        require(source.width == alphaMask.width && source.height == alphaMask.height)
        val w = source.width
        val h = source.height
        val src = IntArray(w * h)
        val mask = IntArray(w * h)
        source.getPixels(src, 0, w, 0, 0, w, h)
        alphaMask.getPixels(mask, 0, w, 0, 0, w, h)

        // Edge-aware local refinement: estimate the foreground confidence from
        // the mask gradient and neighboring alpha values without inventing RGB
        // information. This improves soft hair/clothing boundaries while
        // preserving confident foreground/background pixels.
        val refined = IntArray(mask.size)
        val radius = max(1, (edgeSoftness.coerceIn(0.02f, 1f) * 4f).toInt())
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val a = mask[i] ushr 24
            var minA = a
            var maxA = a
            var sum = 0
            var count = 0
            for (dy in -radius..radius) {
                val yy = y + dy
                if (yy !in 0 until h) continue
                for (dx in -radius..radius) {
                    val xx = x + dx
                    if (xx !in 0 until w) continue
                    val n = mask[yy * w + xx] ushr 24
                    minA = min(minA, n)
                    maxA = max(maxA, n)
                    sum += n
                    count++
                }
            }
            val local = if (count == 0) a else sum / count
            val gradient = maxA - minA
            val refinedAlpha = if (gradient > 24) {
                ((a * 0.55f) + (local * 0.45f)).toInt()
            } else a
            val baseAlpha = src[i] ushr 24
            refined[i] = ((baseAlpha * refinedAlpha / 255) shl 24) or (src[i] and 0x00FFFFFF)
        }
        return Bitmap.createBitmap(refined, w, h, Bitmap.Config.ARGB_8888)
    }
}
