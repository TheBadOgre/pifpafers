package net.rafkos.neuroshima.editor.model

import java.util.UUID

enum class TokenKind { UNIT, MODIFIER }

class Token(
    val id: UUID,
    val kind: TokenKind,
) {
    private val _frontLayers: MutableList<Layer> = mutableListOf()
    private val _backLayers: MutableList<Layer> = mutableListOf()

    fun layers(side: TokenSide): List<Layer> = sideList(side)

    private fun sideList(side: TokenSide): MutableList<Layer> =
        if (side == TokenSide.FRONT) _frontLayers else _backLayers

    fun addLayer(side: TokenSide, layer: Layer, index: Int = sideList(side).size) {
        sideList(side).add(index, layer)
    }

    fun removeLayer(side: TokenSide, layerId: UUID) {
        val list = sideList(side)
        val idx = list.indexOfFirst { it.id == layerId }
        if (idx < 0) throw NoSuchElementException("Layer $layerId not in token $id ($side)")
        list.removeAt(idx)
    }

    fun reorderLayer(side: TokenSide, layerId: UUID, newIndex: Int) {
        val list = sideList(side)
        val cur = list.indexOfFirst { it.id == layerId }
        if (cur < 0) throw NoSuchElementException("Layer $layerId not in token $id ($side)")
        val layer = list.removeAt(cur)
        list.add(newIndex.coerceIn(0, list.size), layer)
    }

    fun updateLayerProps(side: TokenSide, layerId: UUID, newProps: LayerProperties) {
        val list = sideList(side)
        val idx = list.indexOfFirst { it.id == layerId }
        if (idx < 0) throw NoSuchElementException("Layer $layerId not in token $id ($side)")
        list[idx] = list[idx].copy(props = newProps)
    }

    fun findLayer(side: TokenSide, layerId: UUID): Layer? =
        sideList(side).firstOrNull { it.id == layerId }

    /** Convenience lookup that searches both sides. Returns (side, layer) or null. */
    fun findLayerAnywhere(layerId: UUID): Pair<TokenSide, Layer>? {
        for (side in TokenSide.values()) {
            sideList(side).firstOrNull { it.id == layerId }?.let { return side to it }
        }
        return null
    }

    companion object {
        fun createUnit() = Token(UUID.randomUUID(), TokenKind.UNIT)
        fun createModifier() = Token(UUID.randomUUID(), TokenKind.MODIFIER)
    }
}
