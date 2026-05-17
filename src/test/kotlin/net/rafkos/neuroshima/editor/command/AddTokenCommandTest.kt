package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AddTokenCommandTest {

    @Test
    fun `executes adds a unit token at given index`() {
        val bag = TokenBag()
        val cmd = AddTokenCommand(TokenKind.UNIT, atIndex = 0)
        cmd.execute(bag)
        assertEquals(1, bag.tokens.size)
        assertEquals(TokenKind.UNIT, bag.tokens.first().kind)
    }

    @Test
    fun `undo removes the same token`() {
        val bag = TokenBag()
        val cmd = AddTokenCommand(TokenKind.MODIFIER, atIndex = 0)
        cmd.execute(bag)
        cmd.undo(bag)
        assertEquals(0, bag.tokens.size)
    }

    @Test
    fun `redo re-adds with same id`() {
        val bag = TokenBag()
        val cmd = AddTokenCommand(TokenKind.UNIT, atIndex = 0)
        cmd.execute(bag)
        val id = bag.tokens.first().id
        cmd.undo(bag)
        cmd.execute(bag)
        assertEquals(id, bag.tokens.first().id)
    }
}
