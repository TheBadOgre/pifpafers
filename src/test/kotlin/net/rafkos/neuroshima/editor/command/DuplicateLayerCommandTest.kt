package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DuplicateLayerCommandTest {

    @Test
    fun `execute inserts a copy directly above the source with new id and same props`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val src = Layer.create(AssetPath.Bundled("a.png"), LayerProperties(rotation = 90f))
        t.addLayer(TokenSide.FRONT, src)

        val cmd = DuplicateLayerCommand(t.id, TokenSide.FRONT, src.id)
        cmd.execute(bag)
        assertEquals(2, t.layers(TokenSide.FRONT).size)
        assertEquals(src.id, t.layers(TokenSide.FRONT)[0].id)
        assertNotEquals(src.id, t.layers(TokenSide.FRONT)[1].id)
        assertEquals(90f, t.layers(TokenSide.FRONT)[1].props.rotation)
        assertEquals(AssetPath.Bundled("a.png"), t.layers(TokenSide.FRONT)[1].assetPath)
    }

    @Test
    fun `undo removes the duplicate`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val src = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(TokenSide.FRONT, src)
        val cmd = DuplicateLayerCommand(t.id, TokenSide.FRONT, src.id)
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(1, t.layers(TokenSide.FRONT).size)
        assertEquals(src.id, t.layers(TokenSide.FRONT).first().id)
    }
}
