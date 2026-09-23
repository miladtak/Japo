package com.miladtak.japo.chroma

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromaKeyProcessorTest {
    @Test fun removesKeyColorAndKeepsDifferentColor() {
        val bitmap = Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, Color.rgb(0, 255, 0))
        bitmap.setPixel(1, 0, Color.rgb(255, 0, 0))
        val result = ChromaKeyProcessor().removeKey(
            bitmap, 0f, 1f, 0f,
            ChromaKeySettings(similarity = 0.8f, threshold = 0.05f, smoothness = 0.05f)
        )
        assertTrue(Color.alpha(result.getPixel(0, 0)) < 10)
        assertTrue(Color.alpha(result.getPixel(1, 0)) > 245)
    }
}
