package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
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
        t.addLayer(a); t.addLayer(b); t.addLayer(c)

        val cmd = RemoveLayerCommand(t.id, b.id)
        cmd.execute(bag)
        assertEquals(listOf(a.id, c.id), t.layers.map { it.id })

        cmd.undo(bag)
        assertEquals(listOf(a.id, b.id, c.id), t.layers.map { it.id })
        assertEquals(7, t.layers[1].props.offsetX)
    }
}
