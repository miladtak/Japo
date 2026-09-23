package com.miladtak.japo.encoder

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

class BitmapH264Encoder : VideoEncoder {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var started = false
    private var width = 0
    private var height = 0
    private var colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible

    override fun start(output: String, width: Int, height: Int, frameRate: Int) {
        require(width > 0 && height > 0 && frameRate > 0)
        check(codec == null)
        this.width = width and 1.inv()
        this.height = height and 1.inv()

        val encoderInfo = MediaCodecListCompat.findAvcEncoder()
            ?: error("No hardware AVC encoder is available on this device.")
        colorFormat = MediaCodecListCompat.chooseYuv420Format(encoderInfo)
            ?: error("AVC encoder has no supported YUV420 input format.")

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, this.width, this.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, (this.width * this.height * frameRate * 0.08f).toInt().coerceAtLeast(256_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        codec = MediaCodec.createByCodecName(encoderInfo.name).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            it.start()
        }
        muxer = MediaMuxer(File(output).apply { parentFile?.mkdirs() }.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    override fun encode(frame: Bitmap, presentationTimeUs: Long) {
        val c = codec ?: error("Encoder has not been started.")
        require(frame.width == width && frame.height == height) {
            "Frame size does not match encoder size."
        }
        val yuv = bitmapToYuv420(frame, colorFormat)
        val deadline = System.nanoTime() + 2_000_000_000L
        var queued = false
        while (!queued && System.nanoTime() < deadline) {
            val index = c.dequeueInputBuffer(50_000)
            if (index >= 0) {
                c.getInputBuffer(index)?.let {
                    it.clear()
                    it.put(yuv)
                    c.queueInputBuffer(index, 0, yuv.size, presentationTimeUs, 0)
                    queued = true
                }
            }
            drain(false)
        }
        check(queued) { "Timed out while queuing a video frame." }
        drain(false)
    }

    override fun stop() {
        val c = codec ?: return
        try {
            val index = c.dequeueInputBuffer(500_000)
            if (index >= 0) c.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drain(true)
        } finally {
            runCatching { c.stop() }
            runCatching { c.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            codec = null
            muxer = null
            trackIndex = -1
            started = false
        }
    }

    private fun drain(endOfStream: Boolean) {
        val c = codec ?: return
        val info = MediaCodec.BufferInfo()
        var idle = 0
        while (idle < if (endOfStream) 80 else 3) {
            when (val index = c.dequeueOutputBuffer(info, 20_000)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> idle++
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!started)
                    trackIndex = muxer?.addTrack(c.outputFormat) ?: error("Muxer unavailable.")
                    muxer?.start()
                    started = true
                    idle = 0
                }
                else -> if (index >= 0) {
                    val buffer = c.getOutputBuffer(index)
                    if (buffer != null && info.size > 0 && started) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer?.writeSampleData(trackIndex, buffer, info)
                    }
                    c.releaseOutputBuffer(index, false)
                    idle = 0
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    private fun bitmapToYuv420(bitmap: Bitmap, format: Int): ByteArray {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val ySize = width * height
        val uvSize = ySize / 4
        val out = ByteArray(ySize + uvSize * 2)
        var yIndex = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = pixels[j * width + i]
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255
                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                out[yIndex++] = y.toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val uv = (j / 2) * (width / 2) + i / 2
                    if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                        out[ySize + uv] = u.toByte()
                        out[ySize + uvSize + uv] = v.toByte()
                    } else {
                        out[ySize + uv * 2] = u.toByte()
                        out[ySize + uv * 2 + 1] = v.toByte()
                    }
                }
            }
        }
        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            val planar = ByteArray(ySize + uvSize * 2)
            System.arraycopy(out, 0, planar, 0, ySize)
            for (i in 0 until uvSize) {
                planar[ySize + i * 2] = out[ySize + i]
                planar[ySize + i * 2 + 1] = out[ySize + uvSize + i]
            }
            return planar
        }
        return out
    }
}

private object MediaCodecListCompat {
    fun findAvcEncoder(): MediaCodecInfo? =
        android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }
        }

    fun chooseYuv420Format(info: MediaCodecInfo): Int? =
        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats.firstOrNull {
            it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible ||
                it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar ||
                it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        }
}
