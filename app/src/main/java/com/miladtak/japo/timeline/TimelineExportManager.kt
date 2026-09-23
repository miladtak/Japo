package com.miladtak.japo.timeline

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

class TimelineExportManager(private val context: Context) {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun export(
        clips: List<TimelineClip>,
        output: File,
        onProgress: (Int) -> Unit,
        onComplete: (File) -> Unit,
        onError: (Exception) -> Unit
    ): Transformer {
        require(clips.isNotEmpty()) { "Timeline is empty." }
        val ordered = clips.sortedBy { it.order }
        val edited = ordered.map { clip ->
            val item = MediaItem.Builder()
                .setUri(Uri.parse(clip.sourceUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                )
                .build()
            EditedMediaItem.Builder(item).build()
        }
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(edited)
        val composition = Composition.Builder(listOf(sequence)).build()
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        lateinit var transformer: Transformer
        transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    result: ExportResult
                ) {
                    onProgress(100)
                    onComplete(output)
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exportException: ExportException
                ) {
                    onError(exportException)
                }
            })
            .build()

        transformer.start(composition, output.absolutePath)
        return transformer
    }
}
