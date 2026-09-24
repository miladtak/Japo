package com.miladtak.japo.matting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max

class BackgroundReplacer {
    fun color(foreground: Bitmap, alphaMask: Bitmap, backgroundColor: Int): Bitmap {
        requireSameSize(foreground, alphaMask)
        val output = Bitmap.createBitmap(foreground.width, foreground.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(backgroundColor)
        drawMasked(canvas, foreground, alphaMask)
        return output
    }

    fun image(foreground: Bitmap, alphaMask: Bitmap, background: Bitmap): Bitmap {
        requireSameSize(foreground, alphaMask)
        val output = Bitmap.createBitmap(foreground.width, foreground.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(background, null, Rect(0, 0, output.width, output.height), Paint(Paint.ANTI_ALIAS_FLAG))
        drawMasked(canvas, foreground, alphaMask)
        return output
    }

    /**
     * Replaces non-person pixels with a box-blurred version of the original frame.
     * The blur is separable: horizontal + vertical passes, so complexity is O(pixels * radius)
     * instead of the previous O(pixels * radius^2) neighborhood.
     */
    fun blurred(foreground: Bitmap, alphaMask: Bitmap, radius: Float): Bitmap {
        requireSameSize(foreground, alphaMask)
        val width = foreground.width
        val height = foreground.height
        val source = foreground.copy(Bitmap.Config.ARGB_8888, false)
        val src = IntArray(width * height)
        source.getPixels(src, 0, width, 0, 0, width, height)
        val mask = IntArray(src.size)
        alphaMask.getPixels(mask, 0, width, 0, 0, width, height)

        val r = radius.toInt().coerceIn(1, 32)
        val horizontal = IntArray(src.size)
        val blurred = IntArray(src.size)

        for (y in 0 until height) {
            var sr = 0L
            var sg = 0L
            var sb = 0L
            var count = 0
            for (x in 0 until width) {
                val addX = (x + r).coerceAtMost(width - 1)
                val removeX = (x - r - 1).coerceAtLeast(0)
                val add = src[y * width + addX]
                val remove = src[y * width + removeX]
                sr += Color.red(add) - Color.red(remove)
                sg += Color.green(add) - Color.green(remove)
                sb += Color.blue(add) - Color.blue(remove)
                count = (count + 1).coerceAtMost(2 * r + 1)
                horizontal[y * width + x] = Color.rgb(
                    (sr / max(1, count)).toInt(),
                    (sg / max(1, count)).toInt(),
                    (sb / max(1, count)).toInt()
                )
            }
        }

        for (x in 0 until width) {
            var sr = 0L
            var sg = 0L
            var sb = 0L
            var count = 0
            for (y in 0 until height) {
                val addY = (y + r).coerceAtMost(height - 1)
                val removeY = (y - r - 1).coerceAtLeast(0)
                val add = horizontal[addY * width + x]
                val remove = horizontal[removeY * width + x]
                sr += Color.red(add) - Color.red(remove)
                sg += Color.green(add) - Color.green(remove)
                sb += Color.blue(add) - Color.blue(remove)
                count = (count + 1).coerceAtMost(2 * r + 1)
                val i = y * width + x
                val original = src[i]
                val a = (mask[i] ushr 24).coerceIn(0, 255)
                val inv = 255 - a
                blurred[i] = Color.argb(
                    255,
                    (Color.red(original) * a + Color.red(add) * inv / max(1, count)) / 255,
                    (Color.green(original) * a + Color.green(add) * inv / max(1, count)) / 255,
                    (Color.blue(original) * a + Color.blue(add) * inv / max(1, count)) / 255
                )
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(blurred, 0, width, 0, 0, width, height)
        source.recycle()
        return output
    }

    private fun drawMasked(canvas: Canvas, foreground: Bitmap, mask: Bitmap) {
        val source = foreground.copy(Bitmap.Config.ARGB_8888, true)
        val sourcePixels = IntArray(source.width * source.height)
        val maskPixels = IntArray(mask.width * mask.height)
        source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        mask.getPixels(maskPixels, 0, mask.width, 0, 0, mask.width, mask.height)
        for (i in sourcePixels.indices) {
            sourcePixels[i] = ((maskPixels[i] ushr 24) shl 24) or (sourcePixels[i] and 0x00FFFFFF)
        }
        source.setPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        canvas.drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        source.recycle()
    }

    private fun requireSameSize(a: Bitmap, b: Bitmap) {
        require(a.width == b.width && a.height == b.height) {
            "Foreground and alpha mask dimensions must match."
        }
    }
}
