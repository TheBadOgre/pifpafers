package net.rafkos.neuroshima.editor.model

import net.rafkos.neuroshima.editor.command.AddTokenCommand
import net.rafkos.neuroshima.editor.command.DuplicateTokenCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MaskIdAssignmentTest {
    @Test
    fun `first token gets maskId 0`() {
        val bag = TokenBag()
        AddTokenCommand(TokenKind.UNIT, 0).execute(bag)
        assertEquals(0, bag.tokens[0].maskId)
    }

    @Test
    fun `second token gets maskId 1`() {
        val bag = TokenBag()
        AddTokenCommand(TokenKind.UNIT, 0).execute(bag)
        AddTokenCommand(TokenKind.MODIFIER, 1).execute(bag)
        assertEquals(0, bag.tokens[0].maskId)
        assertEquals(1, bag.tokens[1].maskId)
    }

    @Test
    fun `deleting a token does not free its maskId`() {
        val bag = TokenBag()
        val a = AddTokenCommand(TokenKind.UNIT, 0).apply { execute(bag) }
        AddTokenCommand(TokenKind.UNIT, 1).apply { execute(bag) }
        bag.removeToken(a.createdId!!)
        val c = AddTokenCommand(TokenKind.UNIT, 1).apply { execute(bag) }
        assertEquals(2, bag.findToken(c.createdId!!)!!.maskId)
    }

    @Test
    fun `redo of AddTokenCommand preserves original maskId`() {
        val bag = TokenBag()
        val cmd = AddTokenCommand(TokenKind.UNIT, 0)
        cmd.execute(bag)
        val original = bag.tokens[0].maskId
        cmd.undo(bag)
        AddTokenCommand(TokenKind.UNIT, 0).execute(bag)
        cmd.execute(bag)
        val redone = bag.findToken(cmd.createdId!!)!!.maskId
        assertEquals(original, redone)
    }

    @Test
    fun `DuplicateTokenCommand assigns fresh maskId different from source`() {
        val bag = TokenBag()
        val a = AddTokenCommand(TokenKind.UNIT, 0).apply { execute(bag) }
        val dup = DuplicateTokenCommand(a.createdId!!).apply { execute(bag) }
        val srcId = bag.findToken(a.createdId!!)!!.maskId
        val dupId = bag.findToken(dup.newTokenId!!)!!.maskId
        assertNotEquals(srcId, dupId)
    }
}
