package com.miladtak.japo.matting

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BitmapMattingProcessorTest {
    @Test fun preservesOpaqueSourceWithOpaqueMask() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.RED)
        val mask = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        mask.setPixel(0, 0, Color.WHITE)
        val result = BitmapMattingProcessor().refine(source, mask, 0.01f)
        assertEquals(255, Color.alpha(result.getPixel(0, 0)))
    }

    @Test fun removesSourceWithTransparentMask() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.RED)
        val mask = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        mask.setPixel(0, 0, Color.TRANSPARENT)
        val result = BitmapMattingProcessor().refine(source, mask, 0.01f)
        assertEquals(0, Color.alpha(result.getPixel(0, 0)))
    }
}
