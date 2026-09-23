package com.miladtak.japo.matting

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MaskEditorView(
    context: android.content.Context,
    initialMask: Bitmap
) : View(context) {
    enum class Mode { ADD, REMOVE }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var mask = initialMask.copy(Bitmap.Config.ARGB_8888, true)
    private var mode = Mode.ADD
    private var radius = 36f
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()
    private var gestureChanged = false

    fun setMode(value: Mode) { mode = value }

    fun setBrushRadius(value: Float) {
        radius = value.coerceIn(2f, 512f)
    }

    fun bitmap(): Bitmap = mask.copy(Bitmap.Config.ARGB_8888, true)

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(mask.copy(Bitmap.Config.ARGB_8888, true))
        mask.recycle()
        mask = undoStack.removeLast()
        invalidate()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(mask.copy(Bitmap.Config.ARGB_8888, true))
        mask.recycle()
        mask = redoStack.removeLast()
        invalidate()
    }

    fun reset(newMask: Bitmap) {
        pushUndo()
        mask.recycle()
        mask = newMask.copy(Bitmap.Config.ARGB_8888, true)
        redoStack.forEach { it.recycle() }
        redoStack.clear()
        invalidate()
    }

    fun feather(radiusPx: Int = 2) {
        val radius = radiusPx.coerceIn(1, 12)
        pushUndo()
        val output = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val src = IntArray(mask.width * mask.height)
        val dst = IntArray(src.size)
        mask.getPixels(src, 0, mask.width, 0, 0, mask.width, mask.height)
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                var sum = 0
                var count = 0
                for (dy in -radius..radius) {
                    val yy = y + dy
                    if (yy !in 0 until mask.height) continue
                    for (dx in -radius..radius) {
                        val xx = x + dx
                        if (xx !in 0 until mask.width) continue
                        sum += src[yy * mask.width + xx] ushr 24
                        count++
                    }
                }
                val alpha = if (count == 0) 0 else sum / count
                dst[y * mask.width + x] = alpha shl 24 or 0x00FFFFFF
            }
        }
        output.setPixels(dst, 0, mask.width, 0, 0, mask.width, mask.height)
        mask.recycle()
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
            MotionEvent.ACTION_DOWN -> {
                pushUndo()
                redoStack.forEach { it.recycle() }
                redoStack.clear()
                gestureChanged = true
                paintStroke(x, y)
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!lastX.isNaN()) {
                    val distance = sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY))
                    val steps = max(1, (distance / max(1f, radius * 0.35f)).toInt())
                    for (step in 1..steps) {
                        val t = step.toFloat() / steps
                        paintStroke(lastX + (x - lastX) * t, lastY + (y - lastY) * t)
                    }
                } else {
                    paintStroke(x, y)
                }
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastX = Float.NaN
                lastY = Float.NaN
                gestureChanged = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun pushUndo() {
        undoStack.addLast(mask.copy(Bitmap.Config.ARGB_8888, true))
        while (undoStack.size > 20) undoStack.removeFirst().recycle()
    }

    private fun paintStroke(cx: Float, cy: Float) {
        val pixels = IntArray(mask.width * mask.height)
        mask.getPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
        val r = radius
        val minX = max(0, (cx - r).toInt())
        val maxX = min(mask.width - 1, (cx + r).toInt())
        val minY = max(0, (cy - r).toInt())
        val maxY = min(mask.height - 1, (cy + r).toInt())

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val dx = x - cx
                val dy = y - cy
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > r) continue
                val strength = 1f - distance / r
                val index = y * mask.width + x
                val oldAlpha = pixels[index] ushr 24
                val newAlpha = if (mode == Mode.ADD) {
                    (oldAlpha + 255f * strength).toInt().coerceAtMost(255)
                } else {
                    (oldAlpha - 255f * strength).toInt().coerceAtLeast(0)
                }
                pixels[index] = newAlpha shl 24 or 0x00FFFFFF
            }
        }
        mask.setPixels(pixels, 0, mask.width, 0, 0, mask.width, mask.height)
    }
}
