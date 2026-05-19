package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MoveLayerCommandTest {

    private fun setup(): Triple<TokenBag, Token, Layer> {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(TokenSide.FRONT, l)
        bag.updateLayerProps(t.id, TokenSide.FRONT, l.id, l.props.copy(offsetX = 10, offsetY = 20))
        return Triple(bag, t, l)
    }

    @Test
    fun `execute applies both X and Y - undo restores both`() {
        val (bag, t, l) = setup()
        val cmd = MoveLayerCommand(listOf(
            MoveLayerCommand.Target(t.id, TokenSide.FRONT, l.id, oldX = 10, oldY = 20, newX = 30, newY = 50)
        ))
        cmd.execute(bag)
        assertEquals(30, t.findLayer(TokenSide.FRONT, l.id)!!.props.offsetX)
        assertEquals(50, t.findLayer(TokenSide.FRONT, l.id)!!.props.offsetY)
        cmd.undo(bag)
        assertEquals(10, t.findLayer(TokenSide.FRONT, l.id)!!.props.offsetX)
        assertEquals(20, t.findLayer(TokenSide.FRONT, l.id)!!.props.offsetY)
    }

    @Test
    fun `merge keeps first old values and last new values`() {
        val tId = java.util.UUID.randomUUID()
        val lId = java.util.UUID.randomUUID()
        val first = MoveLayerCommand(listOf(MoveLayerCommand.Target(tId, TokenSide.FRONT, lId, 0, 0, 10, 15)))
        val second = MoveLayerCommand(listOf(MoveLayerCommand.Target(tId, TokenSide.FRONT, lId, 10, 15, 25, 30)))
        val merged = first.mergeWith(second) as MoveLayerCommand
        assertEquals(0, merged.targets[0].oldX)
        assertEquals(0, merged.targets[0].oldY)
        assertEquals(25, merged.targets[0].newX)
        assertEquals(30, merged.targets[0].newY)
    }

    @Test
    fun `merge rejects different layer sets`() {
        val tId = java.util.UUID.randomUUID()
        val l1 = java.util.UUID.randomUUID()
        val l2 = java.util.UUID.randomUUID()
        val first = MoveLayerCommand(listOf(MoveLayerCommand.Target(tId, TokenSide.FRONT, l1, 0, 0, 5, 5)))
        val second = MoveLayerCommand(listOf(MoveLayerCommand.Target(tId, TokenSide.FRONT, l2, 0, 0, 5, 5)))
        assertNull(first.mergeWith(second))
    }

    @Test
    fun `merge rejects non-MoveLayerCommand`() {
        val cmd = MoveLayerCommand(listOf(
            MoveLayerCommand.Target(java.util.UUID.randomUUID(), TokenSide.FRONT, java.util.UUID.randomUUID(), 0, 0, 1, 1)
        ))
        assertNull(cmd.mergeWith(object : Command {
            override val label = "other"
            override fun execute(bag: TokenBag) {}
            override fun undo(bag: TokenBag) {}
            override fun mergeWith(next: Command): Command? = null
        }))
    }
}
