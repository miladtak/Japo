package com.miladtak.japo.processing

import android.graphics.Bitmap
import com.miladtak.japo.segmentation.MlKitPersonSegmenter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class MlKitSegmenterAdapter(
    private val delegate: MlKitPersonSegmenter = MlKitPersonSegmenter()
) {
    suspend fun segment(frame: Bitmap): Bitmap? = suspendCancellableCoroutine { continuation ->
        delegate.segment(
            frame,
            onSuccess = { mask ->
                if (continuation.isActive) continuation.resume(mask)
                else if (!mask.isRecycled) mask.recycle()
            },
            onFailure = { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        )
    }
}
