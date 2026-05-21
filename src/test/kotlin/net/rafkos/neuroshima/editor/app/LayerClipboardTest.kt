package net.rafkos.neuroshima.editor.app

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.LayerSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LayerClipboardTest {

    @Test
    fun `isEmpty is true when no copy performed`() {
        val cb = LayerClipboard()
        assertTrue(cb.isEmpty)
    }

    @Test
    fun `copy stores snapshots and isEmpty becomes false`() {
        val cb = LayerClipboard()
        val snap = LayerSnapshot(AssetPath.Bundled("a.png"), LayerProperties())
        cb.copy(listOf(snap))
        assertFalse(cb.isEmpty)
        assertEquals(listOf(snap), cb.contents)
    }

    @Test
    fun `copy fires listeners`() {
        val cb = LayerClipboard()
        var fired = 0
        cb.addListener { fired++ }
        cb.copy(listOf(LayerSnapshot(AssetPath.Bundled("a.png"), LayerProperties())))
        assertEquals(1, fired)
    }

    @Test
    fun `copy replaces previous contents`() {
        val cb = LayerClipboard()
        val first = LayerSnapshot(AssetPath.Bundled("a.png"), LayerProperties())
        val second = LayerSnapshot(AssetPath.Bundled("b.png"), LayerProperties(rotation = 45f))
        cb.copy(listOf(first))
        cb.copy(listOf(second))
        assertEquals(listOf(second), cb.contents)
    }

    @Test
    fun `removeListener stops receiving updates`() {
        val cb = LayerClipboard()
        var fired = 0
        val listener: () -> Unit = { fired++ }
        cb.addListener(listener)
        cb.removeListener(listener)
        cb.copy(listOf(LayerSnapshot(AssetPath.Bundled("a.png"), LayerProperties())))
        assertEquals(0, fired)
    }
}
