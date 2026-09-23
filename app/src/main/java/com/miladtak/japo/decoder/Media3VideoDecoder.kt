package com.miladtak.japo.decoder

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

class Media3VideoDecoder(context: Context) : VideoDecoder {
    private val player = ExoPlayer.Builder(context).build()
    private var currentUri: Uri? = null
    fun player(): Player = player
    fun attach(uri: Uri) { currentUri = uri; player.setMediaItem(MediaItem.fromUri(uri)); player.prepare() }
    fun currentUri(): Uri? = currentUri
    fun play() = player.play()
    fun pause() = player.pause()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun restart() = player.seekTo(0L)
    fun duration(): Long = player.duration.coerceAtLeast(0L)
    fun position(): Long = player.currentPosition.coerceAtLeast(0L)
    fun release() = player.release()
    fun isPlaying(): Boolean = player.isPlaying
    override fun open(source: String) = attach(Uri.parse(source))
    override fun close() = release()
}
