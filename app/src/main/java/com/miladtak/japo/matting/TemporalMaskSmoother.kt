package com.miladtak.japo.matting

import android.graphics.Bitmap

class TemporalMaskSmoother(private val previousWeight: Float = 0.65f) {
    private var previous: Bitmap? = null

    @Synchronized
    fun smooth(current: Bitmap): Bitmap {
        val old = previous
        if (old == null || old.width != current.width || old.height != current.height) {
            previous = current.copy(Bitmap.Config.ARGB_8888, true)
            return current
        }

        val width = current.width
        val height = current.height
        val currentPixels = IntArray(width * height)
        val previousPixels = IntArray(width * height)
        val outputPixels = IntArray(width * height)
        current.getPixels(currentPixels, 0, width, 0, 0, width, height)
        old.getPixels(previousPixels, 0, width, 0, 0, width, height)

        val currentWeight = 1f - previousWeight.coerceIn(0f, 0.95f)
        for (i in currentPixels.indices) {
            val a = (((previousPixels[i] ushr 24) * previousWeight) +
                ((currentPixels[i] ushr 24) * currentWeight)).toInt().coerceIn(0, 255)
            outputPixels[i] = (a shl 24) or 0x00FFFFFF
        }

        val output = Bitmap.createBitmap(outputPixels, width, height, Bitmap.Config.ARGB_8888)
        previous?.recycle()
        previous = output.copy(Bitmap.Config.ARGB_8888, true)
        return output
    }

    @Synchronized
    fun reset() {
        previous?.recycle()
        previous = null
    }
}
