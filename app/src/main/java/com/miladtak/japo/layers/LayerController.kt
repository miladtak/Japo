package com.miladtak.japo.layers

class LayerController(initial: List<Layer> = emptyList()) {
    private var stack = LayerStack(initial)
    private val history = ArrayDeque<List<Layer>>()
    private val redo = ArrayDeque<List<Layer>>()
    private val maxHistory = 50

    fun snapshot(): List<Layer> = stack.all()
    fun canUndo(): Boolean = history.isNotEmpty()
    fun canRedo(): Boolean = redo.isNotEmpty()

    fun add(layer: Layer) = mutate { add(layer) }
    fun remove(id: String): Boolean = mutateResult { remove(id) }
    fun setVisible(id: String, visible: Boolean): Boolean = mutateResult { setVisible(id, visible) }
    fun setOpacity(id: String, opacity: Float): Boolean = mutateResult { setOpacity(id, opacity) }
    fun move(id: String, index: Int): Boolean = mutateResult { move(id, index) }

    fun undo(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        redo.addLast(stack.all())
        stack = LayerStack(previous)
        return true
    }

    fun redo(): Boolean {
        val next = redo.removeLastOrNull() ?: return false
        history.addLast(stack.all())
        stack = LayerStack(next)
        return true
    }

    fun clearHistory() {
        history.clear()
        redo.clear()
    }

    private fun mutate(block: LayerStack.() -> Unit) {
        history.addLast(stack.all())
        while (history.size > maxHistory) history.removeFirst()
        redo.clear()
        stack.block()
    }

    private inline fun <T> mutateResult(block: LayerStack.() -> T): T {
        history.addLast(stack.all())
        while (history.size > maxHistory) history.removeFirst()
        redo.clear()
        return stack.block()
    }
}
