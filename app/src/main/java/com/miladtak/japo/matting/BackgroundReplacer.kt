package com.miladtak.japo.matting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect

class BackgroundReplacer {
    fun color(
        foreground: Bitmap,
        alphaMask: Bitmap,
        backgroundColor: Int
    ): Bitmap {
        require(foreground.width == alphaMask.width && foreground.height == alphaMask.height)
        val output = Bitmap.createBitmap(foreground.width, foreground.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(backgroundColor)
        drawMasked(canvas, foreground, alphaMask)
        return output
    }

    fun image(
        foreground: Bitmap,
        alphaMask: Bitmap,
        background: Bitmap
    ): Bitmap {
        require(foreground.width == alphaMask.width && foreground.height == alphaMask.height)
        val output = Bitmap.createBitmap(foreground.width, foreground.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(
            background,
            null,
            Rect(0, 0, output.width, output.height),
            Paint(Paint.ANTI_ALIAS_FLAG)
        )
        drawMasked(canvas, foreground, alphaMask)
        return output
    }

    private fun drawMasked(canvas: Canvas, foreground: Bitmap, mask: Bitmap) {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        val source = foreground.copy(Bitmap.Config.ARGB_8888, true)
        val sourcePixels = IntArray(source.width * source.height)
        source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        for (i in sourcePixels.indices) {
            val alpha = pixels[i] ushr 24
            sourcePixels[i] = (alpha shl 24) or (sourcePixels[i] and 0x00FFFFFF)
        }
        source.setPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        source.recycle()
    }
}
