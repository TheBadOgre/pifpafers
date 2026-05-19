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

    /** Backward-compat shim: returns FRONT side layers. Replaced by layers(side) in later tasks. */
    @Deprecated("Use layers(side) instead", ReplaceWith("layers(TokenSide.FRONT)"))
    val layers: List<Layer> get() = _frontLayers

    /** Backward-compat shim: adds to FRONT side. Replaced in later tasks. */
    @Deprecated("Use addLayer(side, layer, index) instead", ReplaceWith("addLayer(TokenSide.FRONT, layer, index)"))
    fun addLayer(layer: Layer, index: Int = _frontLayers.size) = addLayer(TokenSide.FRONT, layer, index)

    /** Backward-compat shim: searches FRONT side only. Replaced by findLayerAnywhere in later tasks. */
    @Deprecated("Use findLayerAnywhere(layerId) instead", ReplaceWith("findLayerAnywhere(layerId)?.second"))
    fun findLayer(layerId: UUID): Layer? = _frontLayers.firstOrNull { it.id == layerId }

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
