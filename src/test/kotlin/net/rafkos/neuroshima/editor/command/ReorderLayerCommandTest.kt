package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReorderLayerCommandTest {

    @Test
    fun `execute moves layer - undo restores original position`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val a = Layer.create(AssetPath.Bundled("a.png"))
        val b = Layer.create(AssetPath.Bundled("b.png"))
        val c = Layer.create(AssetPath.Bundled("c.png"))
        t.addLayer(TokenSide.FRONT, a); t.addLayer(TokenSide.FRONT, b); t.addLayer(TokenSide.FRONT, c)

        val cmd = ReorderLayerCommand(t.id, TokenSide.FRONT, a.id, newIndex = 2)
        cmd.execute(bag)
        assertEquals(listOf(b.id, c.id, a.id), t.layers(TokenSide.FRONT).map { it.id })

        cmd.undo(bag)
        assertEquals(listOf(a.id, b.id, c.id), t.layers(TokenSide.FRONT).map { it.id })
    }
}
