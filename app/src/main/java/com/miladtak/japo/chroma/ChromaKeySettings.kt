package com.miladtak.japo.chroma

data class ChromaKeySettings(
    val similarity: Float = .25f,
    val smoothness: Float = .10f,
    val spillSuppression: Float = .50f,
    val edgeSoftness: Float = .10f,
    val threshold: Float = .10f,
    val feather: Float = .05f,
    val despill: Float = .50f
)
