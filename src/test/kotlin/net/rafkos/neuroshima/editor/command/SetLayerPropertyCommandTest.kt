package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
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
        t.addLayer(TokenSide.FRONT, l)

        val cmd = SetLayerPropertyCommand(
            tokenId = t.id, side = TokenSide.FRONT, layerId = l.id,
            property = LayerProperty.OFFSET_X, oldValue = 0.0, newValue = 25.0
        )
        cmd.execute(bag)
        assertEquals(25, t.layers(TokenSide.FRONT).first().props.offsetX)
        cmd.undo(bag)
        assertEquals(0, t.layers(TokenSide.FRONT).first().props.offsetX)
    }

    @Test
    fun `mergeWith collapses consecutive same-prop edits on same layer`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(TokenSide.FRONT, l)

        val first = SetLayerPropertyCommand(t.id, TokenSide.FRONT, l.id, LayerProperty.OPACITY, 1.0, 0.5)
        val second = SetLayerPropertyCommand(t.id, TokenSide.FRONT, l.id, LayerProperty.OPACITY, 0.5, 0.25)
        val merged = first.mergeWith(second)
        assertNotNull(merged)
        merged!!.execute(bag)
        assertEquals(0.25f, t.layers(TokenSide.FRONT).first().props.opacity)
        merged.undo(bag)
        assertEquals(1f, t.layers(TokenSide.FRONT).first().props.opacity)
    }

    @Test
    fun `mergeWith refuses different property`() {
        val first = SetLayerPropertyCommand(java.util.UUID.randomUUID(), TokenSide.FRONT, java.util.UUID.randomUUID(),
            LayerProperty.OPACITY, 1.0, 0.5)
        val second = SetLayerPropertyCommand(first.tokenId, TokenSide.FRONT, first.layerId,
            LayerProperty.SCALE, 1.0, 0.5)
        assertNull(first.mergeWith(second))
    }

    @Test
    fun `mergeWith refuses different layer`() {
        val first = SetLayerPropertyCommand(java.util.UUID.randomUUID(), TokenSide.FRONT, java.util.UUID.randomUUID(),
            LayerProperty.OPACITY, 1.0, 0.5)
        val second = SetLayerPropertyCommand(first.tokenId, TokenSide.FRONT, java.util.UUID.randomUUID(),
            LayerProperty.OPACITY, 0.5, 0.25)
        assertNull(first.mergeWith(second))
    }
}
