package com.miladtak.japo.matting

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TemporalMaskSmootherTest {
    @Test fun smoothingBlendsWithPreviousMask() {
        val first = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        first.setPixel(0, 0, Color.argb(255, 255, 255, 255))
        val second = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        second.setPixel(0, 0, Color.argb(0, 255, 255, 255))

        val smoother = TemporalMaskSmoother(0.5f)
        smoother.smooth(first)
        val result = smoother.smooth(second)

        assertTrue((result.getPixel(0, 0) ushr 24) in 120..135)
    }
}
