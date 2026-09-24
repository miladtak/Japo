package com.miladtak.japo.export

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.miladtak.japo.encoder.BitmapH264Encoder
import com.miladtak.japo.processing.BackgroundFrameProvider
import com.miladtak.japo.processing.FrameProcessingConfig
import com.miladtak.japo.processing.FrameProcessingPipeline
import com.miladtak.japo.processing.MlKitSegmenterAdapter
import com.miladtak.japo.segmentation.MlKitPersonSegmenter
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

data class ProcessedVideoExportRequest(
    val source: Uri,
    val output: File,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val frameStepMs: Long = 33L,
    val config: FrameProcessingConfig = FrameProcessingConfig(),
    val backgroundFrameProvider: BackgroundFrameProvider? = null,
    val backgroundVideo: Uri? = null
)

class ProcessedVideoExporter(
    private val context: Context,
    pipeline: FrameProcessingPipeline? = null
) {
    private val segmenter = MlKitSegmenterAdapter(MlKitPersonSegmenter())
    private val pipeline: FrameProcessingPipeline = pipeline ?: FrameProcessingPipeline(
        segmenter = { frame -> segmenter.segment(frame) }
    )
    fun export(
        request: ProcessedVideoExportRequest,
        onProgress: (Int) -> Unit = {},
        onComplete: (File) -> Unit,
        onError: (Exception) -> Unit
    ): Thread {
        val cancelled = AtomicBoolean(false)
        return Thread {
            val retriever = MediaMetadataRetriever()
            val encoder = BitmapH264Encoder()
            var first: Bitmap? = null
            var ownedBackgroundProvider: BackgroundFrameProvider? = null
            try {
                retriever.setDataSource(context, request.source)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val start = request.startMs.coerceAtLeast(0L)
                val end = (request.endMs ?: duration).coerceAtMost(duration)
                require(end > start) { "Video range is empty." }
                val decodedFirst = retriever.getFrameAtTime(start * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: error("Unable to decode first frame.")
                val width = decodedFirst.width and 1.inv()
                val height = decodedFirst.height and 1.inv()
                require(width >= 2 && height >= 2) { "Video dimensions are too small." }
                first = if (decodedFirst.width == width && decodedFirst.height == height) decodedFirst
                else Bitmap.createBitmap(decodedFirst, 0, 0, width, height)
                if (first !== decodedFirst) decodedFirst.recycle()

                val fps = (1000f / request.frameStepMs.coerceAtLeast(1L)).roundToInt().coerceIn(1, 60)
                encoder.start(request.output.absolutePath, width, height, fps)
                pipeline.resetTemporalState()
                val backgroundProvider = request.backgroundFrameProvider ?: request.backgroundVideo?.let {
                    com.miladtak.japo.processing.MediaMetadataBackgroundFrameProvider.open(context, it)
                }
                ownedBackgroundProvider = if (request.backgroundFrameProvider == null) backgroundProvider else null

                var timestamp = start
                while (!cancelled.get() && timestamp < end) {
                    val decoded = if (timestamp == start) first!!
                    else retriever.getFrameAtTime(timestamp * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: error("Unable to decode frame at $timestamp ms")
                    val frame = if (decoded.width == width && decoded.height == height) decoded
                    else Bitmap.createScaledBitmap(decoded, width, height, true)

                    var background: Bitmap? = null
                    var resultBitmap: Bitmap? = null
                    var resultAlpha: Bitmap? = null
                    try {
                        if (request.config.background == com.miladtak.japo.processing.BackgroundMode.VIDEO) {
                            background = backgroundProvider?.frameAt(timestamp)
                                ?: error("Background video frame unavailable at $timestamp ms")
                        }
                        val result = kotlinx.coroutines.runBlocking {
                            pipeline.process(frame, request.config, background)
                        }
                        resultBitmap = result.bitmap
                        resultAlpha = result.alphaMask
                        encoder.encode(result.bitmap, (timestamp - start) * 1000L)
                    } finally {
                        if (resultBitmap != null && resultBitmap !== frame && !resultBitmap.isRecycled) resultBitmap.recycle()
                        if (resultAlpha != null && !resultAlpha.isRecycled) resultAlpha.recycle()
                        if (background != null && !background.isRecycled) background.recycle()
                        if (frame !== decoded && !frame.isRecycled) frame.recycle()
                        if (decoded !== first && !decoded.isRecycled) decoded.recycle()
                    }

                    onProgress((((timestamp - start).toDouble() / (end - start).toDouble()) * 100.0)
                        .toInt().coerceIn(0, 99))
                    timestamp += request.frameStepMs
                }

                require(!cancelled.get()) { "Export cancelled." }
                encoder.stop()
                onProgress(100)
                onComplete(request.output)
            } catch (e: Exception) {
                runCatching { encoder.stop() }
                if (!cancelled.get()) onError(e)
            } finally {
                first?.let { if (!it.isRecycled) it.recycle() }
                retriever.release()
                ownedBackgroundProvider?.release()
            }
        }.apply {
            name = "Japo-ProcessedVideoExporter"
            start()
        }
    }
}
