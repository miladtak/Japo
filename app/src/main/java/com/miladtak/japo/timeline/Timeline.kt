package com.miladtak.japo.timeline

data class TimelineClip(
    val id: String,
    val sourceUri: String,
    val startMs: Long,
    val endMs: Long,
    val order: Int = 0
) {
    init {
        require(sourceUri.isNotBlank())
        require(startMs >= 0L && endMs > startMs)
    }

    val durationMs: Long
        get() = endMs - startMs
}
