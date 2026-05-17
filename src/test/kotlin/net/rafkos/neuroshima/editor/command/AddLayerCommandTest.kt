package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AddLayerCommandTest {

    @Test
    fun `execute adds layer on top by default`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        assertEquals(1, t.layers.size)
        assertEquals(AssetPath.Bundled("x.png"), t.layers.first().assetPath)
    }

    @Test
    fun `undo removes the same layer`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(0, t.layers.size)
    }

    @Test
    fun `redo reuses same layer id`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val cmd = AddLayerCommand(t.id, AssetPath.Bundled("x.png"))
        cmd.execute(bag)
        val originalId = t.layers.first().id
        cmd.undo(bag)
        cmd.execute(bag)
        assertEquals(originalId, t.layers.first().id)
    }
}
