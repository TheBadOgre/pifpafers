package net.rafkos.neuroshima.editor.model

import java.util.UUID

class TokenBag {
    var schemaVersion: Int = 2
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

    fun addLayer(tokenId: UUID, side: TokenSide, layer: Layer, index: Int? = null) {
        val token = requireToken(tokenId)
        val effective = index ?: token.layers(side).size
        token.addLayer(side, layer, effective)
        fire(ModelEvent.LayerAdded(tokenId, side, layer.id, effective))
    }

    fun removeLayer(tokenId: UUID, side: TokenSide, layerId: UUID) {
        val token = requireToken(tokenId)
        token.removeLayer(side, layerId)
        fire(ModelEvent.LayerRemoved(tokenId, side, layerId))
    }

    fun reorderLayer(tokenId: UUID, side: TokenSide, layerId: UUID, newIndex: Int) {
        val token = requireToken(tokenId)
        token.reorderLayer(side, layerId, newIndex)
        fire(ModelEvent.LayerReordered(tokenId, side, layerId, newIndex))
    }

    fun updateLayerProps(tokenId: UUID, side: TokenSide, layerId: UUID, newProps: LayerProperties) {
        val token = requireToken(tokenId)
        token.updateLayerProps(side, layerId, newProps)
        fire(ModelEvent.LayerPropsChanged(tokenId, side, layerId))
    }

    // ---------------------------------------------------------------------------
    // Backward-compat overloads — default to FRONT side. Removed in later tasks.
    // ---------------------------------------------------------------------------

    @Deprecated("Pass side explicitly", ReplaceWith("addLayer(tokenId, TokenSide.FRONT, layer, index)"))
    fun addLayer(tokenId: UUID, layer: Layer, index: Int? = null) =
        addLayer(tokenId, TokenSide.FRONT, layer, index)

    @Deprecated("Pass side explicitly", ReplaceWith("removeLayer(tokenId, TokenSide.FRONT, layerId)"))
    fun removeLayer(tokenId: UUID, layerId: UUID) =
        removeLayer(tokenId, TokenSide.FRONT, layerId)

    @Deprecated("Pass side explicitly", ReplaceWith("reorderLayer(tokenId, TokenSide.FRONT, layerId, newIndex)"))
    fun reorderLayer(tokenId: UUID, layerId: UUID, newIndex: Int) =
        reorderLayer(tokenId, TokenSide.FRONT, layerId, newIndex)

    @Deprecated("Pass side explicitly", ReplaceWith("updateLayerProps(tokenId, TokenSide.FRONT, layerId, newProps)"))
    fun updateLayerProps(tokenId: UUID, layerId: UUID, newProps: LayerProperties) =
        updateLayerProps(tokenId, TokenSide.FRONT, layerId, newProps)

    private fun requireToken(tokenId: UUID): Token =
        findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId not in bag")
}
