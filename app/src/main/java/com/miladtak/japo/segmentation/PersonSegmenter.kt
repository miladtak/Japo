package com.miladtak.japo.segmentation

import android.graphics.Bitmap

interface PersonSegmenter {
    fun segment(
        frame: Bitmap,
        onSuccess: (Bitmap) -> Unit,
        onFailure: (Exception) -> Unit
    )
}
