package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemoveLayerCommandTest {

    @Test
    fun `execute removes layer - undo restores at original index with same props`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val a = Layer.create(AssetPath.Bundled("a.png"))
        val b = Layer.create(AssetPath.Bundled("b.png"), LayerProperties(offsetX = 7))
        val c = Layer.create(AssetPath.Bundled("c.png"))
        t.addLayer(TokenSide.FRONT, a); t.addLayer(TokenSide.FRONT, b); t.addLayer(TokenSide.FRONT, c)

        val cmd = RemoveLayerCommand(t.id, TokenSide.FRONT, b.id)
        cmd.execute(bag)
        assertEquals(listOf(a.id, c.id), t.layers(TokenSide.FRONT).map { it.id })

        cmd.undo(bag)
        assertEquals(listOf(a.id, b.id, c.id), t.layers(TokenSide.FRONT).map { it.id })
        assertEquals(7, t.layers(TokenSide.FRONT)[1].props.offsetX)
    }

    @Test
    fun `removing layer from BACK does not touch FRONT`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val fl = Layer.create(AssetPath.Bundled("a.png"))
        val bl = Layer.create(AssetPath.Bundled("b.png"))
        bag.addLayer(t.id, TokenSide.FRONT, fl)
        bag.addLayer(t.id, TokenSide.BACK, bl)
        RemoveLayerCommand(t.id, TokenSide.BACK, bl.id).execute(bag)
        org.junit.jupiter.api.Assertions.assertEquals(1, t.layers(TokenSide.FRONT).size)
        org.junit.jupiter.api.Assertions.assertEquals(0, t.layers(TokenSide.BACK).size)
    }
}
