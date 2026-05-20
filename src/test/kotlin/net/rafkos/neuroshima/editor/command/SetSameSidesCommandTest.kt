package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetSameSidesCommandTest {
    private fun freshBagWithToken(): Pair<TokenBag, java.util.UUID> {
        val bag = TokenBag()
        val add = AddTokenCommand(TokenKind.UNIT, 0)
        add.execute(bag)
        return bag to add.createdId!!
    }

    @Test
    fun `do toggles to true`() {
        val (bag, id) = freshBagWithToken()
        SetSameSidesCommand(id, newValue = true).execute(bag)
        assertTrue(bag.findToken(id)!!.sameSides)
    }

    @Test
    fun `undo restores prior value`() {
        val (bag, id) = freshBagWithToken()
        bag.updateSameSides(id, true)
        val cmd = SetSameSidesCommand(id, newValue = false)
        cmd.execute(bag)
        assertFalse(bag.findToken(id)!!.sameSides)
        cmd.undo(bag)
        assertTrue(bag.findToken(id)!!.sameSides)
    }

    @Test
    fun `does not merge with another SetSameSidesCommand`() {
        val (bag, id) = freshBagWithToken()
        val a = SetSameSidesCommand(id, true)
        val b = SetSameSidesCommand(id, false)
        assertNull(a.mergeWith(b))
    }
}
