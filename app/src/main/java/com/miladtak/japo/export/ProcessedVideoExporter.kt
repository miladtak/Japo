package com.miladtak.japo.export

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.miladtak.japo.encoder.BitmapH264Encoder
import com.miladtak.japo.processing.FrameProcessingConfig
import com.miladtak.japo.processing.FrameProcessingPipeline
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

data class ProcessedVideoExportRequest(
    val source: Uri,
    val output: File,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val frameStepMs: Long = 33L,
    val config: FrameProcessingConfig = FrameProcessingConfig()
)

class ProcessedVideoExporter(
    private val context: Context,
    private val pipeline: FrameProcessingPipeline = FrameProcessingPipeline()
) {
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
            try {
                retriever.setDataSource(context, request.source)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val end = (request.endMs ?: duration).coerceAtMost(duration)
                require(end > request.startMs) { "Video range is empty." }

                val first = retriever.getFrameAtTime(
                    request.startMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: error("Unable to decode the first video frame.")
                val width = first.width and 1.inv()
                val height = first.height and 1.inv()
                val normalizedFirst = if (first.width == width && first.height == height) first
                else Bitmap.createBitmap(first, 0, 0, width, height)

                encoder.start(request.output.absolutePath, width, height, (1000f / request.frameStepMs).roundToInt().coerceIn(1, 60))
                pipeline.resetTemporalState()

                var timestamp = request.startMs
                var processed = 0L
                try {
                    while (!cancelled.get() && timestamp < end) {
                        val decoded = if (timestamp == request.startMs) normalizedFirst else
                            retriever.getFrameAtTime(timestamp * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                        if (decoded == null) error("Unable to decode frame at $timestamp ms")
                        val frame = if (decoded.width == width && decoded.height == height) decoded
                        else Bitmap.createBitmap(decoded, 0, 0, width, height)
                        val result = kotlinx.coroutines.runBlocking { pipeline.process(frame, request.config) }
                        encoder.encode(result.bitmap, (timestamp - request.startMs) * 1000L)
                        processed++
                        val fraction = ((timestamp - request.startMs).toFloat() / (end - request.startMs).toFloat()).coerceIn(0f, 1f)
                        onProgress((fraction * 100f).toInt())
                        if (result.bitmap !== frame && !result.bitmap.isRecycled) result.bitmap.recycle()
                        result.alphaMask?.let { if (!it.isRecycled) it.recycle() }
                        if (frame !== decoded) {
                            if (!frame.isRecycled) frame.recycle()
                            if (decoded !== normalizedFirst && !decoded.isRecycled) decoded.recycle()
                        } else if (decoded !== normalizedFirst && !decoded.isRecycled) {
                            decoded.recycle()
                        }
                        timestamp += request.frameStepMs
                    }
                } finally {
                    if (normalizedFirst !== first && !normalizedFirst.isRecycled) normalizedFirst.recycle()
                    if (!first.isRecycled) first.recycle()
                }
                if (cancelled.get()) error("Export cancelled.")
                encoder.stop()
                onProgress(100)
                onComplete(request.output)
            } catch (e: Exception) {
                runCatching { encoder.stop() }
                if (!cancelled.get()) onError(e)
            } finally {
                retriever.release()
            }
        }.apply {
            name = "Japo-ProcessedVideoExporter"
            start()
        }
    }
}
