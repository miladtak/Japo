package com.miladtak.japo.tracking

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Converts a person alpha mask into stable detection boxes.
 * Small connected components are ignored as segmentation noise.
 */
class PersonMaskDetector(
    private val alphaThreshold: Int = 64,
    private val minComponentPixels: Int = 64,
    private val maxComponents: Int = 8
) {
    fun detect(mask: Bitmap): List<PersonDetection> {
        require(mask.width > 0 && mask.height > 0)
        val width = mask.width
        val height = mask.height
        val pixels = IntArray(width * height)
        mask.getPixels(pixels, 0, width, 0, 0, width, height)
        val visited = BooleanArray(pixels.size)
        val result = ArrayList<PersonDetection>()

        for (start in pixels.indices) {
            if (visited[start] || alpha(pixels[start]) < alphaThreshold) continue
            val queue = IntArray(min(width * height, 16384))
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var count = 0
            var left = width
            var top = height
            var right = -1
            var bottom = -1

            while (head < tail) {
                val index = queue[head++]
                val x = index % width
                val y = index / width
                count++
                left = min(left, x)
                top = min(top, y)
                right = max(right, x)
                bottom = max(bottom, y)

                if (x > 0) tail = enqueue(index - 1, pixels, visited, queue, tail)
                if (x + 1 < width) tail = enqueue(index + 1, pixels, visited, queue, tail)
                if (y > 0) tail = enqueue(index - width, pixels, visited, queue, tail)
                if (y + 1 < height) tail = enqueue(index + width, pixels, visited, queue, tail)
                if (tail >= queue.size && head < tail) break
            }

            if (count >= minComponentPixels && right >= left && bottom >= top) {
                result += PersonDetection(
                    RectF(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat()),
                    (count.toFloat() / ((right - left + 1) * (bottom - top + 1))).coerceIn(0f, 1f)
                )
            }
        }

        return result.sortedByDescending { it.bounds.width() * it.bounds.height() }
            .take(maxComponents)
    }

    private fun enqueue(
        index: Int,
        pixels: IntArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int
    ): Int {
        if (visited[index] || alpha(pixels[index]) < alphaThreshold || tail >= queue.size) return tail
        visited[index] = true
        queue[tail] = index
        return tail + 1
    }

    private fun alpha(pixel: Int): Int = pixel ushr 24
}
