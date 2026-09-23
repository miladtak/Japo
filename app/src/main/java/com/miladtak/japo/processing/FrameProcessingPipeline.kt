package com.miladtak.japo.processing

import android.graphics.Bitmap
import android.graphics.Color
import com.miladtak.japo.chroma.ChromaKeyProcessor
import com.miladtak.japo.chroma.ChromaKeySettings
import com.miladtak.japo.matting.BitmapMattingProcessor
import com.miladtak.japo.matting.TemporalMaskSmoother
import com.miladtak.japo.tracking.PersonTracker
import com.miladtak.japo.tracking.PersonMaskDetector

data class FrameProcessingConfig(
    val enablePersonMask: Boolean = false,
    val enableChromaKey: Boolean = false,
    val chromaColor: Int = Color.rgb(20, 204, 20),
    val chromaSimilarity: Float = 0.42f,
    val chromaThreshold: Float = 0.28f,
    val chromaSmoothness: Float = 0.08f,
    val chromaEdgeSoftness: Float = 0.08f,
    val spillSuppression: Float = 0.65f,
    val edgeSoftness: Float = 0.18f,
    val enableTemporalSmoothing: Boolean = true,
    val background: BackgroundMode = BackgroundMode.NONE,
    val backgroundColor: Int = Color.TRANSPARENT,
    val style: StyleMode = StyleMode.NONE,
    val styleStrength: Float = 0.65f
)

enum class BackgroundMode { NONE, TRANSPARENT, COLOR, BLUR }

enum class StyleMode { NONE, ANIME, PENCIL, INK, WATERCOLOR, COMIC, CARTOON, SKETCH, OIL, ILLUSTRATION }

data class ProcessedFrame(
    val bitmap: Bitmap,
    val alphaMask: Bitmap?,
    val trackedPersonCount: Int
)

class FrameProcessingPipeline(
    private val segmenter: suspend (Bitmap) -> Bitmap? = { null },
    private val tracker: PersonTracker = PersonTracker(),
    private val maskDetector: PersonMaskDetector = PersonMaskDetector(),
    private val smoother: TemporalMaskSmoother = TemporalMaskSmoother(),
    private val chroma: ChromaKeyProcessor = ChromaKeyProcessor(),
    private val matting: BitmapMattingProcessor = BitmapMattingProcessor()
) {
    suspend fun process(source: Bitmap, config: FrameProcessingConfig): ProcessedFrame {
        var current = source.copy(Bitmap.Config.ARGB_8888, false)
        var alpha: Bitmap? = null
        var trackedCount = 0

        if (config.enablePersonMask) {
            alpha = segmenter(source)
            if (alpha != null) {
                if (config.enableTemporalSmoothing) alpha = smoother.smooth(alpha)
                val detections = maskDetector.detect(alpha)
                trackedCount = tracker.update(detections).count { it.confidence > 0f }
                current = matting.refine(source, alpha, config.edgeSoftness)
            }
        }

        if (config.enableChromaKey) {
            val c = config.chromaColor
            current = chroma.removeKey(
                current,
                Color.red(c) / 255f,
                Color.green(c) / 255f,
                Color.blue(c) / 255f,
                ChromaKeySettings(
                    similarity = config.chromaSimilarity,
                    threshold = config.chromaThreshold,
                    smoothness = config.chromaSmoothness,
                    edgeSoftness = config.chromaEdgeSoftness,
                    spillSuppression = config.spillSuppression
                )
            )
        }

        if (config.style != StyleMode.NONE) {
            current = applyStyle(current, config.style, config.styleStrength)
        }

        current = when (config.background) {
            BackgroundMode.NONE -> current
            BackgroundMode.TRANSPARENT -> if (alpha != null) matting.refine(current, alpha, 0f) else current
            BackgroundMode.COLOR -> if (alpha != null) {
                com.miladtak.japo.matting.BackgroundReplacer().color(current, alpha, config.backgroundColor)
            } else current
            BackgroundMode.BLUR -> if (alpha != null) {
                val blurred = com.miladtak.japo.matting.BackgroundReplacer().blurred(
                    current, alpha, 18f
                )
                blurred
            } else current
        }

        if (!config.enablePersonMask) tracker.update(emptyList())
        return ProcessedFrame(current, alpha, trackedCount)
    }

    private fun applyStyle(source: Bitmap, style: StyleMode, strength: Float): Bitmap {
        val amount = strength.coerceIn(0f, 1f)
        val input = source.copy(Bitmap.Config.ARGB_8888, false)
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            var r = Color.red(c)
            var g = Color.green(c)
            var b = Color.blue(c)
            val gray = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            when (style) {
                StyleMode.PENCIL, StyleMode.INK, StyleMode.SKETCH -> {
                    r = (gray + (r - gray) * (1f - amount)).toInt()
                    g = (gray + (g - gray) * (1f - amount)).toInt()
                    b = (gray + (b - gray) * (1f - amount)).toInt()
                }
                StyleMode.ANIME, StyleMode.CARTOON, StyleMode.COMIC -> {
                    r = ((r / 32) * 32).coerceIn(0, 255)
                    g = ((g / 32) * 32).coerceIn(0, 255)
                    b = ((b / 32) * 32).coerceIn(0, 255)
                }
                StyleMode.WATERCOLOR -> {
                    r = (r + gray) / 2
                    g = (g + gray) / 2
                    b = (b + gray) / 2
                }
                StyleMode.OIL, StyleMode.ILLUSTRATION -> {
                    r = ((r * (1f + amount) + gray * amount) / (1f + 2f * amount)).toInt()
                    g = ((g * (1f + amount) + gray * amount) / (1f + 2f * amount)).toInt()
                    b = ((b * (1f + amount) + gray * amount) / (1f + 2f * amount)).toInt()
                }
                StyleMode.NONE -> Unit
            }
            pixels[i] = Color.argb(Color.alpha(c), r.coerceIn(0,255), g.coerceIn(0,255), b.coerceIn(0,255))
        }
        input.setPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        return input
    }
    }

    fun resetTemporalState() {
        smoother.reset()
        tracker.reset()
    }
}
