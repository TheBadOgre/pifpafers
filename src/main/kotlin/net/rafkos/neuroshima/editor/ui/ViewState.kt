package net.rafkos.neuroshima.editor.ui

import java.util.UUID

enum class ToolId { SELECT, MOVE, ROTATE, SCALE, OPACITY, COLORIZE }

class ViewState {

    var activeTokenId: UUID? = null
        private set

    private val _selectedLayers: MutableSet<UUID> = linkedSetOf()
    val selectedLayers: Set<UUID> get() = _selectedLayers

    var activeTool: ToolId = ToolId.SELECT
        private set

    var zoom: Float = 1f
        private set

    var showOverlay: Boolean = true
        private set

    var collectionThumbSize: Int = 96
        private set
    var layersThumbSize: Int = 64
        private set
    var assetsThumbSize: Int = 64
        private set

    private val listeners: MutableList<() -> Unit> = mutableListOf()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun fire() { for (l in listeners.toList()) l() }

    fun setActiveToken(id: UUID?) {
        if (activeTokenId == id) return
        activeTokenId = id
        _selectedLayers.clear()
        fire()
    }

    fun selectLayer(id: UUID) {
        if (_selectedLayers.add(id)) fire()
    }

    fun deselectLayer(id: UUID) {
        if (_selectedLayers.remove(id)) fire()
    }

    fun replaceSelection(ids: Collection<UUID>) {
        _selectedLayers.clear()
        _selectedLayers += ids
        fire()
    }

    fun clearSelection() {
        if (_selectedLayers.isNotEmpty()) {
            _selectedLayers.clear()
            fire()
        }
    }

    fun setActiveTool(t: ToolId) {
        if (activeTool == t) return
        activeTool = t
        fire()
    }

    fun setZoom(z: Float) {
        val clamped = z.coerceIn(0.25f, 8f)
        if (clamped == zoom) return
        zoom = clamped
        fire()
    }

    fun setShowOverlay(b: Boolean) {
        if (showOverlay == b) return
        showOverlay = b
        fire()
    }

    fun setCollectionThumbSize(px: Int) {
        val clamped = px.coerceIn(48, 192)
        if (clamped == collectionThumbSize) return
        collectionThumbSize = clamped
        fire()
    }

    fun setLayersThumbSize(px: Int) {
        val clamped = px.coerceIn(48, 192)
        if (clamped == layersThumbSize) return
        layersThumbSize = clamped
        fire()
    }

    fun setAssetsThumbSize(px: Int) {
        val clamped = px.coerceIn(48, 192)
        if (clamped == assetsThumbSize) return
        assetsThumbSize = clamped
        fire()
    }
}
