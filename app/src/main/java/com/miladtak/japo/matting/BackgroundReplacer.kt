package com.miladtak.japo.matting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
    fun blurred(
        foreground: Bitmap,
        alphaMask: Bitmap,
        radius: Float
    ): Bitmap {
        require(foreground.width == alphaMask.width && foreground.height == alphaMask.height)
        val output = foreground.copy(Bitmap.Config.ARGB_8888, false)
        val source = output.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val mask = IntArray(alphaMask.width * alphaMask.height)
        alphaMask.getPixels(mask, 0, alphaMask.width, 0, 0, alphaMask.width, alphaMask.height)
        val r = radius.coerceIn(1f, 32f).toInt()
        val blurred = IntArray(pixels.size)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                var sr = 0L
                var sg = 0L
                var sb = 0L
                var count = 0
                for (yy in (y - r).coerceAtLeast(0)..(y + r).coerceAtMost(source.height - 1)) {
                    for (xx in (x - r).coerceAtLeast(0)..(x + r).coerceAtMost(source.width - 1)) {
                        val c = pixels[yy * source.width + xx]
                        sr += Color.red(c)
                        sg += Color.green(c)
                        sb += Color.blue(c)
                        count++
                    }
                }
                val i = y * source.width + x
                val original = pixels[i]
                val a = mask[i] ushr 24
                val br = (sr / count).toInt()
                val bg = (sg / count).toInt()
                val bb = (sb / count).toInt()
                val inv = 255 - a
                blurred[i] = Color.argb(
                    255,
                    (Color.red(original) * a + br * inv) / 255,
                    (Color.green(original) * a + bg * inv) / 255,
                    (Color.blue(original) * a + bb * inv) / 255
                )
            }
        }
        output.setPixels(blurred, 0, output.width, 0, 0, output.width, output.height)
        source.recycle()
        return output
    }

}
