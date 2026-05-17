package net.rafkos.neuroshima.editor.model

import java.util.UUID

class TokenBag {
    var schemaVersion: Int = 1
        internal set

    var name: String = ""
        set(value) {
            if (field != value) {
                field = value
                fire(ModelEvent.NameChanged)
            }
        }

    private val _tokens: MutableList<Token> = mutableListOf()
    val tokens: List<Token> get() = _tokens

    private val listeners: MutableList<(ModelEvent) -> Unit> = mutableListOf()

    fun addListener(l: (ModelEvent) -> Unit) { listeners += l }
    fun removeListener(l: (ModelEvent) -> Unit) { listeners -= l }

    private fun fire(event: ModelEvent) {
        for (l in listeners.toList()) l(event)
    }

    fun addToken(token: Token, index: Int = _tokens.size) {
        _tokens.add(index, token)
        fire(ModelEvent.TokenAdded(token.id, index))
    }

    fun removeToken(tokenId: UUID) {
        val idx = _tokens.indexOfFirst { it.id == tokenId }
        if (idx < 0) throw NoSuchElementException("Token $tokenId not in bag")
        _tokens.removeAt(idx)
        fire(ModelEvent.TokenRemoved(tokenId))
    }

    fun findToken(tokenId: UUID): Token? = _tokens.firstOrNull { it.id == tokenId }

    fun addLayer(tokenId: UUID, layer: Layer, index: Int? = null) {
        val token = requireToken(tokenId)
        val effective = index ?: token.layers.size
        token.addLayer(layer, effective)
        fire(ModelEvent.LayerAdded(tokenId, layer.id, effective))
    }

    fun removeLayer(tokenId: UUID, layerId: UUID) {
        val token = requireToken(tokenId)
        token.removeLayer(layerId)
        fire(ModelEvent.LayerRemoved(tokenId, layerId))
    }

    fun reorderLayer(tokenId: UUID, layerId: UUID, newIndex: Int) {
        val token = requireToken(tokenId)
        token.reorderLayer(layerId, newIndex)
        fire(ModelEvent.LayerReordered(tokenId, layerId, newIndex))
    }

    fun updateLayerProps(tokenId: UUID, layerId: UUID, newProps: LayerProperties) {
        val token = requireToken(tokenId)
        token.updateLayerProps(layerId, newProps)
        fire(ModelEvent.LayerPropsChanged(tokenId, layerId))
    }

    private fun requireToken(tokenId: UUID): Token =
        findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId not in bag")
}
