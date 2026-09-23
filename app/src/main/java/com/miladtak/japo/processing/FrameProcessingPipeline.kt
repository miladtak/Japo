package com.miladtak.japo.processing

import android.graphics.Bitmap
import android.graphics.Color
import com.miladtak.japo.chroma.ChromaKeyProcessor
import com.miladtak.japo.matting.BitmapMattingProcessor
import com.miladtak.japo.matting.TemporalMaskSmoother
import com.miladtak.japo.tracking.PersonDetection
import com.miladtak.japo.tracking.PersonTracker

data class FrameProcessingConfig(
    val enablePersonMask: Boolean = false,
    val enableChromaKey: Boolean = false,
    val chromaColor: Int = Color.rgb(20, 204, 20),
    val chromaThreshold: Float = 0.28f,
    val chromaSoftness: Float = 0.08f,
    val edgeSoftness: Float = 0.18f,
    val enableTemporalSmoothing: Boolean = true
)

data class ProcessedFrame(
    val bitmap: Bitmap,
    val alphaMask: Bitmap?,
    val trackedPersons: List<PersonDetection>
)

class FrameProcessingPipeline(
    private val segmenter: suspend (Bitmap) -> Bitmap? = { null },
    private val tracker: PersonTracker = PersonTracker(),
    private val smoother: TemporalMaskSmoother = TemporalMaskSmoother(),
    private val chroma: ChromaKeyProcessor = ChromaKeyProcessor(),
    private val matting: BitmapMattingProcessor = BitmapMattingProcessor()
) {
    suspend fun process(
        source: Bitmap,
        config: FrameProcessingConfig
    ): ProcessedFrame {
        var current = source.copy(Bitmap.Config.ARGB_8888, false)
        var alpha: Bitmap? = null
        var detections = emptyList<PersonDetection>()

        if (config.enablePersonMask) {
            alpha = segmenter(source)
            if (alpha != null) {
                if (config.enableTemporalSmoothing) {
                    alpha = smoother.smooth(alpha)
                }
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
                config.chromaThreshold,
                config.chromaSoftness
            )
        }

        return ProcessedFrame(current, alpha, detections)
    }

    fun resetTemporalState() {
        smoother.reset()
        tracker.reset()
    }
}
