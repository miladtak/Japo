package com.miladtak.japo.models

import android.content.Context
import android.graphics.Bitmap
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.Interpreter

class TfliteImageMaskModel(context: Context, assetPath: String) : Closeable {
    private val interpreter: Interpreter

    init {
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        require(bytes.isNotEmpty()) { "TFLite model asset is empty: " + assetPath }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes).rewind()
        interpreter = Interpreter(buffer)
    }

    fun runMask(source: Bitmap): Bitmap {
        val inputShape = interpreter.getInputTensor(0).shape()
        require(inputShape.size == 4 && inputShape[0] == 1 && inputShape[3] == 3) {
            "Unsupported image model input shape: " + inputShape.contentToString()
        }
        val h = inputShape[1]
        val w = inputShape[2]
        val outputShape = interpreter.getOutputTensor(0).shape()
        require(outputShape.size == 4 && outputShape[0] == 1 && outputShape[1] == h && outputShape[2] == w) {
            "Unsupported mask output shape: " + outputShape.contentToString()
        }
        require(outputShape[3] == 1) { "Expected a one-channel mask output." }

        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        val input = Array(1) { Array(h) { Array(w) { FloatArray(3) } } }
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val c = pixels[i]
            input[0][i / w][i % w][0] = android.graphics.Color.red(c) / 255f
            input[0][i / w][i % w][1] = android.graphics.Color.green(c) / 255f
            input[0][i / w][i % w][2] = android.graphics.Color.blue(c) / 255f
        }
        if (scaled !== source) scaled.recycle()

        val output = Array(1) { Array(h) { Array(w) { FloatArray(1) } } }
        interpreter.run(input, output)
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(w * h)
        for (i in outPixels.indices) {
            val a = (output[0][i / w][i % w][0].coerceIn(0f, 1f) * 255f).toInt()
            outPixels[i] = android.graphics.Color.argb(a, 255, 255, 255)
        }
        mask.setPixels(outPixels, 0, w, 0, 0, w, h)
        return mask
    }

    override fun close() = interpreter.close()
}