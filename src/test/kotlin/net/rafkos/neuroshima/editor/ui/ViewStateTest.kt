package net.rafkos.neuroshima.editor.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ViewStateTest {

    @Test
    fun `setActiveToken fires single listener notification`() {
        val vs = ViewState()
        var fires = 0
        vs.addListener { fires++ }
        val id = UUID.randomUUID()
        vs.setActiveToken(id)
        assertEquals(id, vs.activeTokenId)
        assertEquals(1, fires)
    }

    @Test
    fun `setting same active token does not re-fire`() {
        val vs = ViewState()
        val id = UUID.randomUUID()
        vs.setActiveToken(id)
        var fires = 0
        vs.addListener { fires++ }
        vs.setActiveToken(id)
        assertEquals(0, fires)
    }

    @Test
    fun `selected layers default to empty set`() {
        assertEquals(emptySet<UUID>(), ViewState().selectedLayers)
    }

    @Test
    fun `selectLayer adds deselectLayer removes clearSelection empties`() {
        val vs = ViewState()
        val a = UUID.randomUUID(); val b = UUID.randomUUID()
        vs.selectLayer(a)
        vs.selectLayer(b)
        assertEquals(setOf(a, b), vs.selectedLayers)
        vs.deselectLayer(a)
        assertEquals(setOf(b), vs.selectedLayers)
        vs.clearSelection()
        assertEquals(emptySet<UUID>(), vs.selectedLayers)
    }

    @Test
    fun `setActiveTool fires`() {
        val vs = ViewState()
        var fires = 0
        vs.addListener { fires++ }
        vs.setActiveTool(ToolId.MOVE)
        assertEquals(ToolId.MOVE, vs.activeTool)
        assertEquals(1, fires)
    }

    @Test
    fun `setZoom clamps to bounds`() {
        val vs = ViewState()
        vs.setZoom(100f)
        assertEquals(8f, vs.zoom)
        vs.setZoom(0.001f)
        assertEquals(0.25f, vs.zoom)
        vs.setZoom(2f)
        assertEquals(2f, vs.zoom)
    }

    @Test
    fun `activeSide defaults to FRONT`() {
        val vs = ViewState()
        assertEquals(net.rafkos.neuroshima.editor.model.TokenSide.FRONT, vs.activeSide)
    }

    @Test
    fun `setActiveSide clears selection and fires`() {
        val vs = ViewState()
        vs.selectLayer(UUID.randomUUID())
        var fired = 0
        vs.addListener { fired++ }
        vs.setActiveSide(net.rafkos.neuroshima.editor.model.TokenSide.BACK)
        assertEquals(net.rafkos.neuroshima.editor.model.TokenSide.BACK, vs.activeSide)
        assertTrue(vs.selectedLayers.isEmpty())
        assertEquals(1, fired)
    }

    @Test
    fun `setActiveToken resets activeSide to FRONT`() {
        val vs = ViewState()
        vs.setActiveSide(net.rafkos.neuroshima.editor.model.TokenSide.BACK)
        val tokenId = UUID.randomUUID()
        vs.setActiveToken(tokenId)
        assertEquals(net.rafkos.neuroshima.editor.model.TokenSide.FRONT, vs.activeSide)
    }
}
