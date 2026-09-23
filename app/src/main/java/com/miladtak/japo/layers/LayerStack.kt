package com.miladtak.japo.layers

class LayerStack(initial: List<Layer> = emptyList()) {
    private val layers = initial.toMutableList()

    fun all(): List<Layer> = layers.toList()

    fun add(layer: Layer) {
        require(layer.id.isNotBlank())
        layers.add(layer.copy(opacity = layer.opacity.coerceIn(0f, 1f)))
    }

    fun remove(id: String): Boolean = layers.removeAll { it.id == id }

    fun setVisible(id: String, visible: Boolean): Boolean {
        val i = layers.indexOfFirst { it.id == id }
        if (i < 0) return false
        layers[i] = layers[i].copy(visible = visible)
        return true
    }

    fun setOpacity(id: String, opacity: Float): Boolean {
        val i = layers.indexOfFirst { it.id == id }
        if (i < 0) return false
        layers[i] = layers[i].copy(opacity = opacity.coerceIn(0f, 1f))
        return true
    }

    fun move(id: String, newIndex: Int): Boolean {
        val i = layers.indexOfFirst { it.id == id }
        if (i < 0) return false
        val layer = layers.removeAt(i)
        layers.add(newIndex.coerceIn(0, layers.size), layer)
        return true
    }
}
