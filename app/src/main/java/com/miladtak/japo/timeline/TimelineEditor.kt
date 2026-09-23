package com.miladtak.japo.timeline

class TimelineEditor(initial: List<TimelineClip> = emptyList()) {
    private val clips = initial.sortedBy { it.order }.toMutableList()

    fun clips(): List<TimelineClip> = clips.mapIndexed { index, clip -> clip.copy(order = index) }

    fun add(clip: TimelineClip) {
        require(clip.endMs > clip.startMs)
        clips.add(clip)
        normalize()
    }

    fun remove(id: String): Boolean {
        val removed = clips.removeAll { it.id == id }
        if (removed) normalize()
        return removed
    }

    fun move(id: String, newIndex: Int): Boolean {
        val index = clips.indexOfFirst { it.id == id }
        if (index < 0) return false
        val item = clips.removeAt(index)
        clips.add(newIndex.coerceIn(0, clips.size), item)
        normalize()
        return true
    }

    fun split(id: String, atMs: Long): List<TimelineClip> {
        val index = clips.indexOfFirst { it.id == id }
        if (index < 0) return emptyList()
        val original = clips[index]
        require(atMs > original.startMs && atMs < original.endMs)
        val first = original.copy(id = original.id + "-a", endMs = atMs)
        val second = original.copy(id = original.id + "-b", startMs = atMs)
        clips[index] = first
        clips.add(index + 1, second)
        normalize()
        return listOf(first, second)
    }

    fun trim(id: String, startMs: Long, endMs: Long): Boolean {
        val index = clips.indexOfFirst { it.id == id }
        if (index < 0 || startMs < 0L || endMs <= startMs) return false
        val old = clips[index]
        if (startMs < old.startMs || endMs > old.endMs) return false
        clips[index] = old.copy(startMs = startMs, endMs = endMs)
        return true
    }

    private fun normalize() {
        for (i in clips.indices) clips[i] = clips[i].copy(order = i)
    }
}
