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
    val enableTemporalSmoothing: Boolean = true
)

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

        if (!config.enablePersonMask) tracker.update(emptyList())
        return ProcessedFrame(current, alpha, trackedCount)
    }

    fun resetTemporalState() {
        smoother.reset()
        tracker.reset()
    }
}
