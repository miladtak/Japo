package com.miladtak.japo.export

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer

data class ExportRequest(
    val source: Uri,
    val output: java.io.File,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val filter: ExportFilter = ExportFilter.NONE
)

enum class ExportFilter { NONE, GRAYSCALE, INVERT, BRIGHT, CONTRAST }

class VideoExportManager(private val context: Context) {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun export(
        request: ExportRequest,
        onProgress: (Int) -> Unit,
        onComplete: (java.io.File) -> Unit,
        onError: (Exception) -> Unit
    ): Transformer {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(request.startMs.coerceAtLeast(0L))
            .apply { request.endMs?.let { setEndPositionMs(it.coerceAtLeast(request.startMs + 1L)) } }
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(request.source)
            .setClippingConfiguration(clipping)
            .build()

        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), filterEffects(request.filter)))
            .build()

        request.output.parentFile?.mkdirs()
        if (request.output.exists()) request.output.delete()

        val handler = Handler(Looper.getMainLooper())
        var finished = false
        lateinit var transformer: Transformer

        val poller = object : Runnable {
            override fun run() {
                if (finished) return
                val holder = ProgressHolder()
                val state = runCatching { transformer.getProgress(holder) }.getOrNull()
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress.coerceIn(0, 100))
                }
                handler.postDelayed(this, 300L)
            }
        }

        transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: androidx.media3.transformer.Composition,
                    result: ExportResult
                ) {
                    finished = true
                    handler.removeCallbacks(poller)
                    onProgress(100)
                    onComplete(request.output)
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    result: ExportResult,
                    exportException: ExportException
                ) {
                    finished = true
                    handler.removeCallbacks(poller)
                    onError(exportException)
                }
            })
            .build()

        transformer.start(edited, request.output.absolutePath)
        handler.post(poller)
        return transformer
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun filterEffects(filter: ExportFilter): List<Effect> = when (filter) {
        ExportFilter.NONE -> emptyList()
        ExportFilter.GRAYSCALE -> listOf(RgbFilter.createGrayscaleFilter())
        ExportFilter.INVERT -> listOf(RgbFilter.createInvertedFilter())
        ExportFilter.BRIGHT -> listOf(Brightness(0.12f))
        ExportFilter.CONTRAST -> listOf(Contrast(0.25f))
    }
}
