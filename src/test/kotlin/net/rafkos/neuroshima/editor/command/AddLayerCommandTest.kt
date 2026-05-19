package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AddLayerCommandTest {

    @Test
    fun `execute adds layer to FRONT by default usage`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, TokenSide.FRONT, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        assertEquals(1, t.layers(TokenSide.FRONT).size)
        assertEquals(0, t.layers(TokenSide.BACK).size)
    }

    @Test
    fun `execute adds layer to BACK when side BACK`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, TokenSide.BACK, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        assertEquals(0, t.layers(TokenSide.FRONT).size)
        assertEquals(1, t.layers(TokenSide.BACK).size)
    }

    @Test
    fun `undo removes the same layer from same side`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, TokenSide.BACK, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(0, t.layers(TokenSide.BACK).size)
    }

    @Test
    fun `redo reuses same layer id`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, TokenSide.FRONT, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        val originalId = t.layers(TokenSide.FRONT).first().id
        cmd.undo(bag)
        cmd.execute(bag)
        assertEquals(originalId, t.layers(TokenSide.FRONT).first().id)
    }
}
