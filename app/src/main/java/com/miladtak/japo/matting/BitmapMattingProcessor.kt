package com.miladtak.japo.matting

import android.graphics.Bitmap
import kotlin.math.abs

class BitmapMattingProcessor : MattingProcessor {
    override fun refine(source: Bitmap, alphaMask: Bitmap, edgeSoftness: Float): Bitmap {
        require(source.width == alphaMask.width && source.height == alphaMask.height)
        val width = source.width
        val height = source.height
        val sourcePixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        val outputPixels = IntArray(width * height)
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
        alphaMask.getPixels(maskPixels, 0, width, 0, 0, width, height)

        val softness = (edgeSoftness.coerceIn(0.01f, 1f) * 255f).toInt()
        for (i in sourcePixels.indices) {
            val baseAlpha = sourcePixels[i] ushr 24
            val maskAlpha = maskPixels[i] ushr 24
            val adjusted = if (softness <= 0) maskAlpha else {
                val center = 128
                val delta = maskAlpha - center
                val normalized = (center + delta * (255f / softness)).toInt()
                normalized.coerceIn(0, 255)
            }
            val alpha = (baseAlpha * adjusted / 255).coerceIn(0, 255)
            outputPixels[i] = (alpha shl 24) or (sourcePixels[i] and 0x00FFFFFF)
        }
        return Bitmap.createBitmap(outputPixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
