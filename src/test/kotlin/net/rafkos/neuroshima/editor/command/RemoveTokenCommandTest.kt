package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RemoveTokenCommandTest {

    @Test
    fun `executes removes token at known id`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        t.addLayer(TokenSide.FRONT, Layer.create(AssetPath.Bundled("a.png")))
        bag.addToken(t)
        val cmd = RemoveTokenCommand(t.id)
        cmd.execute(bag)
        assertEquals(0, bag.tokens.size)
    }

    @Test
    fun `undo restores token at original index with original layers`() {
        val bag = TokenBag()
        val before = Token.createUnit()
        before.addLayer(TokenSide.FRONT, Layer.create(AssetPath.Bundled("a.png")))
        bag.addToken(Token.createUnit())
        bag.addToken(before)
        bag.addToken(Token.createUnit())
        val cmd = RemoveTokenCommand(before.id)
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(before.id, bag.tokens[1].id)
        assertEquals(1, bag.tokens[1].layers(TokenSide.FRONT).size)
        assertEquals(AssetPath.Bundled("a.png"), bag.tokens[1].layers(TokenSide.FRONT).first().assetPath)
    }

    @Test
    fun `undo restores both FRONT and BACK layers`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        t.addLayer(TokenSide.FRONT,
            Layer.create(AssetPath.Bundled("f.png")))
        t.addLayer(TokenSide.BACK,
            Layer.create(AssetPath.Bundled("b.png")))
        bag.addToken(t)
        val cmd = RemoveTokenCommand(t.id)
        cmd.execute(bag)
        cmd.undo(bag)
        val restored = bag.tokens.single()
        assertEquals(1, restored.layers(TokenSide.FRONT).size)
        assertEquals(1, restored.layers(TokenSide.BACK).size)
    }
}
