package net.rafkos.neuroshima.editor.model

import java.util.UUID

enum class TokenKind { UNIT, MODIFIER }

class Token(
    val id: UUID,
    val kind: TokenKind,
) {
    private val _layers: MutableList<Layer> = mutableListOf()
    val layers: List<Layer> get() = _layers

    fun addLayer(layer: Layer, index: Int = _layers.size) {
        _layers.add(index, layer)
    }

    fun removeLayer(layerId: UUID) {
        val idx = _layers.indexOfFirst { it.id == layerId }
        if (idx < 0) throw NoSuchElementException("Layer $layerId not in token $id")
        _layers.removeAt(idx)
    }

    fun reorderLayer(layerId: UUID, newIndex: Int) {
        val cur = _layers.indexOfFirst { it.id == layerId }
        if (cur < 0) throw NoSuchElementException("Layer $layerId not in token $id")
        val layer = _layers.removeAt(cur)
        _layers.add(newIndex.coerceIn(0, _layers.size), layer)
    }

    fun updateLayerProps(layerId: UUID, newProps: LayerProperties) {
        val idx = _layers.indexOfFirst { it.id == layerId }
        if (idx < 0) throw NoSuchElementException("Layer $layerId not in token $id")
        _layers[idx] = _layers[idx].copy(props = newProps)
    }

    fun findLayer(layerId: UUID): Layer? = _layers.firstOrNull { it.id == layerId }

    companion object {
        fun createUnit() = Token(UUID.randomUUID(), TokenKind.UNIT)
        fun createModifier() = Token(UUID.randomUUID(), TokenKind.MODIFIER)
    }
}
