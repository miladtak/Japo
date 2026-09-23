package com.miladtak.japo.segmentation

import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.common.InputImage

class MlKitPersonSegmenter : PersonSegmenter {
    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .build()
    )

    override fun segment(
        frame: Bitmap,
        onSuccess: (Bitmap) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val image = InputImage.fromBitmap(frame, 0)
        segmenter.process(image)
            .addOnSuccessListener { result ->
                val mask = result.buffer
                val width = result.width
                val height = result.height
                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(width * height)
                var index = 0
                while (mask.hasRemaining() && index < pixels.size) {
                    val confidence = mask.float.coerceIn(0f, 1f)
                    val alpha = (confidence * 255f).toInt()
                    pixels[index++] = Color.argb(alpha, 255, 255, 255)
                }
                output.setPixels(pixels, 0, width, 0, 0, width, height)
                onSuccess(output)
            }
            .addOnFailureListener { error -> onFailure(error) }
    }
}
