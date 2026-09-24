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
        val blurred = boxBlur(src, width, height, r)
        val outputPixels = IntArray(src.size)

        for (i in src.indices) {
            val original = src[i]
            val background = blurred[i]
            val a = (mask[i] ushr 24).coerceIn(0, 255)
            val inv = 255 - a
            outputPixels[i] = Color.argb(
                255,
                (Color.red(original) * a + Color.red(background) * inv) / 255,
                (Color.green(original) * a + Color.green(background) * inv) / 255,
                (Color.blue(original) * a + Color.blue(background) * inv) / 255
            )
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outputPixels, 0, width, 0, 0, width, height)
        source.recycle()
        return output
    }

    private fun boxBlur(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val horizontal = IntArray(src.size)
        val output = IntArray(src.size)
        val window = radius * 2 + 1

        for (y in 0 until height) {
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var sumA = 0L
            var previousLeft = 0
            var previousRight = 0
            for (x in 0 until width + radius) {
                val addX = x.coerceAtMost(width - 1)
                val removeX = (x - window).coerceAtLeast(0)
                if (x < width + radius) {
                    val add = src[y * width + addX]
                    sumR += Color.red(add)
                    sumG += Color.green(add)
                    sumB += Color.blue(add)
                    sumA += Color.alpha(add)
                }
                if (x >= window) {
                    val remove = src[y * width + removeX]
                    sumR -= Color.red(remove)
                    sumG -= Color.green(remove)
                    sumB -= Color.blue(remove)
                    sumA -= Color.alpha(remove)
                }
                if (x >= radius) {
                    val outX = x - radius
                    if (outX < width) {
                        val count = max(1, minOf(window, outX + radius + 1, width + radius - outX))
                        horizontal[y * width + outX] = Color.argb(
                            (sumA / count).toInt(),
                            (sumR / count).toInt(),
                            (sumG / count).toInt(),
                            (sumB / count).toInt()
                        )
                    }
                }
            }
        }

        for (x in 0 until width) {
            var sumR = 0L
            var sumG = 0L
            var sumB = 0L
            var sumA = 0L
            for (y in 0 until height + radius) {
                val addY = y.coerceAtMost(height - 1)
                val removeY = (y - window).coerceAtLeast(0)
                val add = horizontal[addY * width + x]
                sumR += Color.red(add)
                sumG += Color.green(add)
                sumB += Color.blue(add)
                sumA += Color.alpha(add)
                if (y >= window) {
                    val remove = horizontal[removeY * width + x]
                    sumR -= Color.red(remove)
                    sumG -= Color.green(remove)
                    sumB -= Color.blue(remove)
                    sumA -= Color.alpha(remove)
                }
                if (y >= radius) {
                    val outY = y - radius
                    if (outY < height) {
                        val count = max(1, minOf(window, outY + radius + 1, height + radius - outY))
                        output[outY * width + x] = Color.argb(
                            (sumA / count).toInt(),
                            (sumR / count).toInt(),
                            (sumG / count).toInt(),
                            (sumB / count).toInt()
                        )
                    }
                }
            }
        }
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
