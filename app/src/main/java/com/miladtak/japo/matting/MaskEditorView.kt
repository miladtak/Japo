package com.miladtak.japo.matting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class MaskEditorView(
    context: android.content.Context,
    initialMask: Bitmap
) : View(context) {
    enum class Mode { ADD, REMOVE }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mask = initialMask.copy(Bitmap.Config.ARGB_8888, true)
    private var mode = Mode.ADD
    private var radius = 36f
    private var lastX = -1f
    private var lastY = -1f

    fun setMode(value: Mode) {
        mode = value
    }

    fun setBrushRadius(value: Float) {
        radius = value.coerceIn(4f, 200f)
    }

    fun bitmap(): Bitmap = mask.copy(Bitmap.Config.ARGB_8888, true)

    fun feather() {
        val radiusPx = 4f
        val output = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val src = IntArray(mask.width * mask.height)
        val dst = IntArray(src.size)
        mask.getPixels(src, 0, mask.width, 0, 0, mask.width, mask.height)
        for (y in 1 until mask.height - 1) {
            for (x in 1 until mask.width - 1) {
                var sum = 0
                for (dy in -1..1) for (dx in -1..1) {
                    sum += (src[(y + dy) * mask.width + (x + dx)] ushr 24)
                }
                val alpha = (sum / 9).coerceIn(0, 255)
                dst[y * mask.width + x] = alpha shl 24 or 0x00FFFFFF
            }
        }
        output.setPixels(dst, 0, mask.width, 0, 0, mask.width, mask.height)
        mask = output
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.DKGRAY)
        val scale = min(width.toFloat() / mask.width, height.toFloat() / mask.height)
        val left = (width - mask.width * scale) / 2f
        val top = (height - mask.height * scale) / 2f
        canvas.drawBitmap(mask, null, RectF(left, top, left + mask.width * scale, top + mask.height * scale), paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scale = min(width.toFloat() / mask.width, height.toFloat() / mask.height)
        val left = (width - mask.width * scale) / 2f
        val top = (height - mask.height * scale) / 2f
        val x = ((event.x - left) / scale).coerceIn(0f, mask.width - 1f)
        val y = ((event.y - top) / scale).coerceIn(0f, mask.height - 1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                paintStroke(x, y)
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                lastX = -1f
                lastY = -1f
                invalidate()
                return true
            }
        }
        return true
    }

    private fun paintStroke(cx: Float, cy: Float) {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        val r = radius.toInt()
        val minX = max(0, cx.toInt() - r)
        val maxX = min(mask.width - 1, cx.toInt() + r)
        val minY = max(0, cy.toInt() - r)
        val maxY = min(mask.height - 1, cy.toInt() + r)

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x - cx
                val dy = y - cy
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                if (distance <= radius) {
                    val strength = 1f - (distance / radius)
                    val index = y * mask.width + x
                    val oldAlpha = pixels[index] ushr 24
                    val newAlpha = if (mode == Mode.ADD) {
                        (oldAlpha + 255f * strength).toInt().coerceAtMost(255)
                    } else {
                        (oldAlpha - 255f * strength).toInt().coerceAtLeast(0)
                    }
                    pixels[index] = (newAlpha shl 24) or 0x00FFFFFF
                }
            }
        }
        mask.setPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
    }
}
