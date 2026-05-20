package net.rafkos.neuroshima.editor.model

import java.util.UUID

class TokenBag {
    var schemaVersion: Int = 3
        internal set

    var name: String = ""
        set(value) {
            if (field != value) {
                field = value
                fire(ModelEvent.NameChanged)
            }
        }

    var printSettings: PublishSettings = PublishSettings()
        private set

    private val _tokens: MutableList<Token> = mutableListOf()
    val tokens: List<Token> get() = _tokens

    private val listeners: MutableList<(ModelEvent) -> Unit> = mutableListOf()

    fun addListener(l: (ModelEvent) -> Unit) { listeners += l }
    fun removeListener(l: (ModelEvent) -> Unit) { listeners -= l }

    private fun fire(event: ModelEvent) {
        for (l in listeners.toList()) l(event)
    }

    fun nextMaskId(): Int = (_tokens.maxOfOrNull { it.maskId } ?: -1) + 1

    fun addToken(token: Token, index: Int = _tokens.size) {
        _tokens.add(index, token)
        fire(ModelEvent.TokenAdded(token.id, index))
        fire(ModelEvent.MaskIdAssigned(token.id, token.maskId))
    }

    fun removeToken(tokenId: UUID) {
        val idx = _tokens.indexOfFirst { it.id == tokenId }
        if (idx < 0) throw NoSuchElementException("Token $tokenId not in bag")
        _tokens.removeAt(idx)
        fire(ModelEvent.TokenRemoved(tokenId))
    }

    fun findToken(tokenId: UUID): Token? = _tokens.firstOrNull { it.id == tokenId }

    fun updateSameSides(tokenId: UUID, newValue: Boolean) {
        val token = requireToken(tokenId)
        if (token.sameSides == newValue) return
        token.sameSides = newValue
        fire(ModelEvent.SameSidesChanged(tokenId))
    }

    fun updatePrintSettings(newSettings: PublishSettings) {
        if (printSettings == newSettings) return
        printSettings = newSettings
        fire(ModelEvent.PrintSettingsChanged)
    }

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

    private fun requireToken(tokenId: UUID): Token =
        findToken(tokenId) ?: throw NoSuchElementException("Token $tokenId not in bag")
}
