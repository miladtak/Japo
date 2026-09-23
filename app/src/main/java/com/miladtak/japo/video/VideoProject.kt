package com.miladtak.japo.video

data class VideoProject(
    val id: String,
    val name: String,
    val sourceUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
)
