package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SetLayerPropertyCommandTest {

    @Test
    fun `execute sets property - undo restores`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(l)

        val cmd = SetLayerPropertyCommand(
            tokenId = t.id, layerId = l.id,
            property = LayerProperty.OFFSET_X, oldValue = 0.0, newValue = 25.0
        )
        cmd.execute(bag)
        assertEquals(25, t.layers.first().props.offsetX)
        cmd.undo(bag)
        assertEquals(0, t.layers.first().props.offsetX)
    }

    @Test
    fun `mergeWith collapses consecutive same-prop edits on same layer`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(l)

        val first = SetLayerPropertyCommand(t.id, l.id, LayerProperty.OPACITY, 1.0, 0.5)
        val second = SetLayerPropertyCommand(t.id, l.id, LayerProperty.OPACITY, 0.5, 0.25)
        val merged = first.mergeWith(second)
        assertNotNull(merged)
        merged!!.execute(bag)
        assertEquals(0.25f, t.layers.first().props.opacity)
        merged.undo(bag)
        assertEquals(1f, t.layers.first().props.opacity)
    }

    @Test
    fun `mergeWith refuses different property`() {
        val first = SetLayerPropertyCommand(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            LayerProperty.OPACITY, 1.0, 0.5)
        val second = SetLayerPropertyCommand(first.tokenId, first.layerId,
            LayerProperty.SCALE, 1.0, 0.5)
        assertNull(first.mergeWith(second))
    }

    @Test
    fun `mergeWith refuses different layer`() {
        val first = SetLayerPropertyCommand(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
            LayerProperty.OPACITY, 1.0, 0.5)
        val second = SetLayerPropertyCommand(first.tokenId, java.util.UUID.randomUUID(),
            LayerProperty.OPACITY, 0.5, 0.25)
        assertNull(first.mergeWith(second))
    }
}
