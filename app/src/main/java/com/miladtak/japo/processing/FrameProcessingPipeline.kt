package com.miladtak.japo.processing

import android.graphics.Bitmap
import android.graphics.Color
import com.miladtak.japo.chroma.ChromaKeyProcessor
import com.miladtak.japo.chroma.ChromaKeySettings
import com.miladtak.japo.matting.BackgroundReplacer
import com.miladtak.japo.matting.BitmapMattingProcessor
import com.miladtak.japo.matting.TemporalMaskSmoother
import com.miladtak.japo.tracking.PersonMaskDetector
import com.miladtak.japo.tracking.PersonTracker

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
    val styleStrength: Float = 0.65f,
    val manualMask: Bitmap? = null,
    val manualMaskMode: ManualMaskMode = ManualMaskMode.REPLACE
) {
    init {
        require(chromaSimilarity in 0f..1f)
        require(chromaThreshold in 0f..1f)
        require(chromaSmoothness in 0f..1f)
        require(chromaEdgeSoftness in 0f..1f)
        require(spillSuppression in 0f..1f)
        require(edgeSoftness in 0f..1f)
        require(styleStrength in 0f..1f)
    }
}

enum class BackgroundMode { NONE, TRANSPARENT, COLOR, IMAGE, BLUR, VIDEO }

enum class ManualMaskMode { REPLACE, INTERSECT, UNION }

enum class StyleMode {
    NONE, ANIME, PENCIL, INK, WATERCOLOR, COMIC, CARTOON, SKETCH, OIL, ILLUSTRATION
}

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
    private val matting: BitmapMattingProcessor = BitmapMattingProcessor(),
    private val backgrounds: BackgroundReplacer = BackgroundReplacer()
) {
    suspend fun process(
        source: Bitmap,
        config: FrameProcessingConfig,
        backgroundBitmap: Bitmap? = null
    ): ProcessedFrame {
        var current = source
        var ownsCurrent = false
        var alpha: Bitmap? = null

        try {
            if (config.enablePersonMask) {
                alpha = segmenter(source)
                if (alpha != null) {
                    if (config.enableTemporalSmoothing) alpha = smoother.smooth(alpha)
                    val detections = maskDetector.detect(alpha)
                    tracker.update(detections)
                    val refined = matting.refine(source, alpha, config.edgeSoftness)
                    if (refined !== current) {
                        if (ownsCurrent && !current.isRecycled) current.recycle()
                        current = refined
                        ownsCurrent = true
                    }
                }
            } else {
                tracker.reset()
                smoother.reset()
            }

            if (config.manualMask != null) {
                val manual = normalizeMask(config.manualMask, source.width, source.height)
                alpha = combineMasks(alpha, manual, config.manualMaskMode)
            }

            if (config.enableChromaKey) {
                val c = config.chromaColor
                val keyed = chroma.removeKey(
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
                if (keyed !== current) {
                    if (ownsCurrent && !current.isRecycled) current.recycle()
                    current = keyed
                    ownsCurrent = true
                }
            }

            if (config.style != StyleMode.NONE) {
                val styled = applyStyle(current, config.style, config.styleStrength)
                if (styled !== current) {
                    if (ownsCurrent && !current.isRecycled) current.recycle()
                    current = styled
                    ownsCurrent = true
                }
            }

            if (alpha != null) {
                val composited = when (config.background) {
                    BackgroundMode.NONE -> null
                    BackgroundMode.TRANSPARENT -> matting.refine(current, alpha, 0f)
                    BackgroundMode.COLOR -> backgrounds.color(current, alpha, config.backgroundColor)
                    BackgroundMode.IMAGE, BackgroundMode.VIDEO -> backgroundBitmap?.let {
                        backgrounds.image(current, alpha, it)
                    }
                    BackgroundMode.BLUR -> backgrounds.blurred(current, alpha, 18f)
                }
                if (composited != null && composited !== current) {
                    if (ownsCurrent && !current.isRecycled) current.recycle()
                    current = composited
                    ownsCurrent = true
                }
            }

            return ProcessedFrame(
                bitmap = current,
                alphaMask = alpha,
                trackedPersonCount = if (config.enablePersonMask) tracker.trackedCount() else 0
            )
        } catch (t: Throwable) {
            if (ownsCurrent && !current.isRecycled) current.recycle()
            alpha?.let { if (!it.isRecycled) it.recycle() }
            throw t
        }
    }

    fun resetTemporalState() {
        smoother.reset()
        tracker.reset()
    }

    private fun normalizeMask(mask: Bitmap, width: Int, height: Int): Bitmap {
        if (mask.width == width && mask.height == height) {
            return mask.copy(Bitmap.Config.ARGB_8888, true)
        }
        return Bitmap.createScaledBitmap(mask, width, height, true)
    }

    private fun combineMasks(
        detected: Bitmap?,
        manual: Bitmap,
        mode: ManualMaskMode
    ): Bitmap {
        if (detected == null || mode == ManualMaskMode.REPLACE) {
            return manual
        }
        val out = Bitmap.createBitmap(manual.width, manual.height, Bitmap.Config.ARGB_8888)
        val a = IntArray(manual.width * manual.height)
        val b = IntArray(a.size)
        val o = IntArray(a.size)
        manual.getPixels(a, 0, manual.width, 0, 0, manual.width, manual.height)
        detected.getPixels(b, 0, detected.width, 0, 0, detected.width, detected.height)
        for (i in o.indices) {
            val ma = a[i] ushr 24
            val da = b[i] ushr 24
            val alpha = when (mode) {
                ManualMaskMode.REPLACE -> ma
                ManualMaskMode.INTERSECT -> minOf(ma, da)
                ManualMaskMode.UNION -> maxOf(ma, da)
            }
            o[i] = Color.argb(alpha, 255, 255, 255)
        }
        out.setPixels(o, 0, out.width, 0, 0, out.width, out.height)
        detected.recycle()
        return out
    }

    private fun applyStyle(source: Bitmap, style: StyleMode, strength: Float): Bitmap {
        val amount = strength.coerceIn(0f, 1f)
        val input = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(input.width * input.height)
        input.getPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r0 = Color.red(c)
            val g0 = Color.green(c)
            val b0 = Color.blue(c)
            val gray = (0.299f * r0 + 0.587f * g0 + 0.114f * b0).toInt()
            var r = r0
            var g = g0
            var b = b0
            when (style) {
                StyleMode.PENCIL, StyleMode.INK, StyleMode.SKETCH -> {
                    r = (gray + (r0 - gray) * (1f - amount)).toInt()
                    g = (gray + (g0 - gray) * (1f - amount)).toInt()
                    b = (gray + (b0 - gray) * (1f - amount)).toInt()
                }
                StyleMode.ANIME, StyleMode.CARTOON, StyleMode.COMIC -> {
                    val levels = if (amount < 0.5f) 48 else 32
                    r = (r0 / levels) * levels
                    g = (g0 / levels) * levels
                    b = (b0 / levels) * levels
                }
                StyleMode.WATERCOLOR -> {
                    r = (r0 * (1f - amount) + gray * amount).toInt()
                    g = (g0 * (1f - amount) + gray * amount).toInt()
                    b = (b0 * (1f - amount) + gray * amount).toInt()
                }
                StyleMode.OIL, StyleMode.ILLUSTRATION -> {
                    r = ((r0 * (1f + amount) + gray * amount) / (1f + 2f * amount)).toInt()
                    g = ((g0 * (1f + amount) + gray * amount) / (1f + 2f * amount)).toInt()
                    b = ((b0 * (1f + amount) + gray * amount) / (1f + 2f * amount)).toInt()
                }
                StyleMode.NONE -> Unit
            }
            pixels[i] = Color.argb(
                Color.alpha(c),
                r.coerceIn(0, 255),
                g.coerceIn(0, 255),
                b.coerceIn(0, 255)
            )
        }
        input.setPixels(pixels, 0, input.width, 0, 0, input.width, input.height)
        return input
    }
}
