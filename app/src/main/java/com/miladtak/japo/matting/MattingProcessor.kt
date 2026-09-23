package com.miladtak.japo.matting

import android.graphics.Bitmap

interface MattingProcessor {
    fun refine(source: Bitmap, alphaMask: Bitmap, edgeSoftness: Float = 0.08f): Bitmap
}
