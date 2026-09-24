package com.miladtak.japo.processing

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Timestamp-addressable background source. Implementations must return only
 * the requested frame and must not retain a complete video in memory.
 */
interface BackgroundFrameProvider {
    fun frameAt(timestampMs: Long): Bitmap?
    fun release()
}

class MediaMetadataBackgroundFrameProvider(
    private val retriever: MediaMetadataRetriever
) : BackgroundFrameProvider {
    override fun frameAt(timestampMs: Long): Bitmap? =
        retriever.getFrameAtTime(
            timestampMs.coerceAtLeast(0L) * 1000L,
            MediaMetadataRetriever.OPTION_CLOSEST
        )

    override fun release() = retriever.release()

    companion object {
        fun open(context: android.content.Context, uri: Uri): MediaMetadataBackgroundFrameProvider {
            return MediaMetadataBackgroundFrameProvider(
                MediaMetadataRetriever().also { it.setDataSource(context, uri) }
            )
        }
    }
}
