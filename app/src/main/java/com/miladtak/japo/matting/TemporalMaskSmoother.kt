package com.miladtak.japo.matting

import android.graphics.Bitmap
import kotlin.math.roundToInt

class TemporalMaskSmoother(
    private val historyWeight: Float = 0.65f
) {
    private var previous: Bitmap? = null

    @Synchronized
    fun smooth(current: Bitmap): Bitmap {
        require(current.config == Bitmap.Config.ARGB_8888)
        val old = previous
        if (old == null || old.width != current.width || old.height != current.height) {
            previous = current.copy(Bitmap.Config.ARGB_8888, false)
            return current
        }

        val width = current.width
        val height = current.height
        val a = IntArray(width * height)
        val b = IntArray(width * height)
        current.getPixels(a, 0, width, 0, 0, width, height)
        old.getPixels(b, 0, width, 0, 0, width, height)

        val w = historyWeight.coerceIn(0f, 0.95f)
        for (i in a.indices) {
            val now = a[i] ushr 24
            val before = b[i] ushr 24
            val blended = (now * (1f - w) + before * w).roundToInt().coerceIn(0, 255)
            a[i] = (blended shl 24) or 0x00FFFFFF
        }

        val result = Bitmap.createBitmap(a, width, height, Bitmap.Config.ARGB_8888)
        previous?.recycle()
        previous = result.copy(Bitmap.Config.ARGB_8888, false)
        return result
    }

    @Synchronized
    fun reset() {
        previous?.recycle()
        previous = null
    }
}
