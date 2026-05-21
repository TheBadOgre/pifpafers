package net.rafkos.neuroshima.editor.app

import net.rafkos.neuroshima.editor.model.LayerSnapshot

class LayerClipboard {
    var contents: List<LayerSnapshot> = emptyList()
        private set

    val isEmpty: Boolean get() = contents.isEmpty()

    private val listeners: MutableList<() -> Unit> = mutableListOf()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    fun copy(layers: List<LayerSnapshot>) {
        contents = layers
        listeners.forEach { it() }
    }
}
