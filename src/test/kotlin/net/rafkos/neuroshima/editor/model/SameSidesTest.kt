package net.rafkos.neuroshima.editor.model

import net.rafkos.neuroshima.editor.command.AddTokenCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SameSidesTest {
    @Test
    fun `default is false`() {
        val bag = TokenBag()
        AddTokenCommand(TokenKind.UNIT, 0).execute(bag)
        assertFalse(bag.tokens[0].sameSides)
    }

    @Test
    fun `updateSameSides changes value and fires event`() {
        val bag = TokenBag()
        AddTokenCommand(TokenKind.UNIT, 0).execute(bag)
        val tokenId = bag.tokens[0].id
        val events = mutableListOf<ModelEvent>()
        bag.addListener { events += it }
        bag.updateSameSides(tokenId, true)
        assertTrue(bag.tokens[0].sameSides)
        assertEquals(1, events.count { it is ModelEvent.SameSidesChanged })
    }

    @Test
    fun `updateSameSides to same value is a no-op`() {
        val bag = TokenBag()
        AddTokenCommand(TokenKind.UNIT, 0).execute(bag)
        val events = mutableListOf<ModelEvent>()
        bag.addListener { events += it }
        bag.updateSameSides(bag.tokens[0].id, false)
        assertEquals(0, events.count { it is ModelEvent.SameSidesChanged })
    }
}
