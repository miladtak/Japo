package com.miladtak.japo.timeline

data class TimelineClip(
    val id: String,
    val startMs: Long,
    val endMs: Long
) {
    init { require(startMs >= 0L && endMs >= startMs) }
}
