package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemoveTokenCommandTest {

    @Test
    fun `executes removes token at known id`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("a.png")))
        bag.addToken(t)
        val cmd = RemoveTokenCommand(t.id)
        cmd.execute(bag)
        assertEquals(0, bag.tokens.size)
    }

    @Test
    fun `undo restores token at original index with original layers`() {
        val bag = TokenBag()
        val before = Token.createUnit()
        before.addLayer(Layer.create(AssetPath.Bundled("a.png")))
        bag.addToken(Token.createUnit())
        bag.addToken(before)
        bag.addToken(Token.createUnit())
        val cmd = RemoveTokenCommand(before.id)
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(before.id, bag.tokens[1].id)
        assertEquals(1, bag.tokens[1].layers.size)
        assertEquals(AssetPath.Bundled("a.png"), bag.tokens[1].layers.first().assetPath)
    }
}
