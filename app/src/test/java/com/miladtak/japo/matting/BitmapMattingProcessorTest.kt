package com.miladtak.japo.matting

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapMattingProcessorTest {
    @Test fun preservesOpaqueSourceWithOpaqueMask() {
        val source = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.RED)
        val mask = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        mask.setPixel(0, 0, Color.WHITE)
        val result = BitmapMattingProcessor().refine(source, mask, 0.5f)
        assertTrue(Color.alpha(result.getPixel(0, 0)) > 0)
    }
}
