package com.miladtak.japo.encoder

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

class MediaCodecVideoEncoder(private val bitrate: Int = 4_000_000) : VideoEncoder {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var started = false
    private var width = 0
    private var height = 0

    override fun start(output: String, width: Int, height: Int, frameRate: Int) {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0)
        stop(); this.width = width; this.height = height
        File(output).parentFile?.mkdirs()
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate.coerceIn(1, 120))
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also {
            it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); it.start()
        }
        muxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    override fun encode(frame: Bitmap, presentationTimeUs: Long) {
        val c = codec ?: error("Encoder not started")
        require(frame.width == width && frame.height == height)
        val index = c.dequeueInputBuffer(10_000); require(index >= 0) { "Encoder input unavailable" }
        val input = c.getInputBuffer(index) ?: error("Encoder input buffer is null")
        input.clear(); bitmapToI420(frame, input)
        c.queueInputBuffer(index, 0, input.position(), presentationTimeUs, 0); drain(false)
    }

    override fun stop() {
        val c = codec
        if (c != null) {
            runCatching { val i=c.dequeueInputBuffer(10_000); if(i>=0) c.queueInputBuffer(i,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM); drain(true) }
            runCatching { c.stop() }; runCatching { c.release() }
        }
        runCatching { muxer?.stop() }; runCatching { muxer?.release() }
        codec=null; muxer=null; trackIndex=-1; started=false
    }

    private fun drain(end: Boolean) {
        val c=codec ?: return; val m=muxer ?: return; val info=MediaCodec.BufferInfo(); var idle=0
        while(true) {
            when(val i=c.dequeueOutputBuffer(info, if(end) 10_000 else 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> { if(!end || ++idle>3) return }
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { trackIndex=m.addTrack(c.outputFormat); m.start(); started=true }
                else -> if(i>=0) {
                    c.getOutputBuffer(i)?.let { b -> if(info.size>0 && started && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) { b.position(info.offset); b.limit(info.offset+info.size); m.writeSampleData(trackIndex,b,info) } }
                    c.releaseOutputBuffer(i,false); if((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0) return
                }
            }
        }
    }

    private fun bitmapToI420(bitmap: Bitmap, out: ByteBuffer) {
        val p=IntArray(width*height); bitmap.getPixels(p,0,width,0,0,width,height); val ySize=width*height; val uv=ySize/4
        require(out.remaining() >= ySize+uv*2)
        var y=0; var u=ySize; var v=ySize+uv
        for(row in 0 until height) for(col in 0 until width) {
            val c=p[row*width+col]; val r=c shr 16 and 255; val g=c shr 8 and 255; val b=c and 255
            out.put((((66*r+129*g+25*b+128) shr 8)+16).coerceIn(0,255).toByte())
            if(row%2==0 && col%2==0) { out.put(u++, (((-38*r-74*g+112*b+128) shr 8)+128).coerceIn(0,255).toByte()); out.put(v++, (((112*r-94*g-18*b+128) shr 8)+128).coerceIn(0,255).toByte()) }
        }
    }
}
