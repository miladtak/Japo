package com.miladtak.japo.video

import com.miladtak.japo.layers.Layer
import com.miladtak.japo.timeline.TimelineClip

data class VideoProject(
    val id: String,
    val name: String,
    val sourceUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val clips: List<TimelineClip> = emptyList(),
    val layers: List<Layer> = emptyList()
)
