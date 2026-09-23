package com.miladtak.japo.encoder

interface VideoEncoder {
    fun start(output: String, width: Int, height: Int, frameRate: Int = 30)
    fun encode(frame: android.graphics.Bitmap, presentationTimeUs: Long)
    fun stop()
}

