package com.miladtak.japo.chroma

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ChromaKeyProcessorTest {
    @Test fun removesKeyColorAndKeepsDifferentColor() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.rgb(0, 255, 0))
        bitmap.setPixel(1, 0, Color.rgb(255, 0, 0))
        val result = ChromaKeyProcessor().removeKey(bitmap, 0f, 1f, 0f)
        assertEquals(0, Color.alpha(result.getPixel(0, 0)))
        assertEquals(255, Color.alpha(result.getPixel(1, 0)))
    }
}
