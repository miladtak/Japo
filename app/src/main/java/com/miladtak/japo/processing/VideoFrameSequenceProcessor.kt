package com.miladtak.japo.processing

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Sequential, bounded-memory frame walker.
 *
 * It deliberately never keeps the whole video in RAM. Each decoded frame is
 * handed to the supplied FrameProcessingPipeline and immediately released
 * after the callback returns. The caller can cancel through the returned job.
 *
 * This is the bridge between the per-frame processing engine and a future
 * MediaCodec/MediaMuxer export path; it is not a fake "full video" shortcut.
 */
class VideoFrameSequenceProcessor(
    private val context: Context,
    private val pipeline: FrameProcessingPipeline
) {
    data class Request(
        val source: Uri,
        val startMs: Long = 0L,
        val endMs: Long? = null,
        val frameStepMs: Long = 33L,
        val config: FrameProcessingConfig = FrameProcessingConfig()
    ) {
        init {
            require(frameStepMs > 0L) { "frameStepMs must be > 0" }
            require(startMs >= 0L) { "startMs must be >= 0" }
            require(endMs == null || endMs > startMs) { "endMs must be > startMs" }
        }
    }

    data class Progress(
        val timestampMs: Long,
        val processedFrames: Long,
        val durationMs: Long,
        val fraction: Float
    )

    interface Listener {
        fun onFrame(progress: Progress, frame: Bitmap, alphaMask: Bitmap?)
        fun onComplete(processedFrames: Long)
        fun onError(error: Exception)
    }

    fun start(request: Request, listener: Listener): Thread {
        val worker = Thread {
            val retriever = MediaMetadataRetriever()
            var processed = 0L
            try {
                retriever.setDataSource(context, request.source)
                val duration = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                val end = (request.endMs ?: duration).coerceAtMost(duration)
                require(end > request.startMs) { "Video range is empty." }

                pipeline.resetTemporalState()
                var timestamp = request.startMs
                while (!Thread.currentThread().isInterrupted && timestamp < end) {
                    val frame = retriever.getFrameAtTime(
                        timestamp * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST
                    ) ?: throw IllegalStateException("Unable to decode frame at $timestamp ms")

                    try {
                        val result = kotlinx.coroutines.runBlocking {
                            pipeline.process(frame, request.config)
                        }
                        processed++
                        val fraction = ((timestamp - request.startMs).toFloat() /
                            (end - request.startMs).toFloat()).coerceIn(0f, 1f)
                        listener.onFrame(
                            Progress(timestamp, processed, end - request.startMs, fraction),
                            result.bitmap,
                            result.alphaMask
                        )
                        if (result.bitmap !== frame && !result.bitmap.isRecycled) {
                            result.bitmap.recycle()
                        }
                        result.alphaMask?.let {
                            if (!it.isRecycled) it.recycle()
                        }
                    } finally {
                        if (!frame.isRecycled) frame.recycle()
                    }
                    timestamp += request.frameStepMs
                }
                if (Thread.currentThread().isInterrupted) return@Thread
                listener.onComplete(processed)
            } catch (e: Exception) {
                if (!Thread.currentThread().isInterrupted) listener.onError(e)
            } finally {
                retriever.release()
            }
        }.apply {
            name = "Japo-VideoFrameProcessor"
            start()
        }
        return worker
    }
}
