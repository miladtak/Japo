package com.miladtak.japo.timeline

/**
 * Transactional timeline controller. UI layers can edit a draft and commit
 * one immutable snapshot to the project store, avoiding partially applied
 * timeline operations.
 */
class TimelineController(initial: List<TimelineClip> = emptyList()) {
    private var editor = TimelineEditor(initial)
    private val history = ArrayDeque<List<TimelineClip>>()
    private val redo = ArrayDeque<List<TimelineClip>>()

    fun snapshot(): List<TimelineClip> = editor.clips()

    fun add(clip: TimelineClip) = mutate { add(clip) }
    fun remove(id: String): Boolean = mutateResult { remove(id) }
    fun move(id: String, index: Int): Boolean = mutateResult { move(id, index) }
    fun trim(id: String, startMs: Long, endMs: Long): Boolean = mutateResult { trim(id, startMs, endMs) }
    fun split(id: String, atMs: Long): List<TimelineClip> = mutateResult { split(id, atMs) }

    fun undo(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        redo.addLast(editor.clips())
        editor = TimelineEditor(previous)
        return true
    }

    fun redo(): Boolean {
        val next = redo.removeLastOrNull() ?: return false
        history.addLast(editor.clips())
        editor = TimelineEditor(next)
        return true
    }

    private fun mutate(block: TimelineEditor.() -> Unit) {
        history.addLast(editor.clips())
        redo.clear()
        editor.block()
    }

    private inline fun <T> mutateResult(block: TimelineEditor.() -> T): T {
        history.addLast(editor.clips())
        redo.clear()
        return editor.block()
    }
}
