package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenBagTest {

    @Test
    fun `new bag is empty with default name`() {
        val bag = TokenBag()
        assertEquals("", bag.name)
        assertEquals(0, bag.tokens.size)
    }

    @Test
    fun `addToken appends and notifies listeners`() {
        val bag = TokenBag()
        val events = mutableListOf<ModelEvent>()
        bag.addListener { events += it }
        val t = Token.createUnit()
        bag.addToken(t)
        assertEquals(listOf(t), bag.tokens.toList())
        assertEquals(1, events.size)
        assertTrue(events.first() is ModelEvent.TokenAdded)
        assertEquals(t.id, (events.first() as ModelEvent.TokenAdded).tokenId)
    }

    @Test
    fun `removeToken notifies listeners`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val events = mutableListOf<ModelEvent>()
        bag.addListener { events += it }
        bag.removeToken(t.id)
        assertEquals(0, bag.tokens.size)
        assertTrue(events.single() is ModelEvent.TokenRemoved)
    }

    @Test
    fun `removeToken with unknown id throws`() {
        val bag = TokenBag()
        assertThrows(NoSuchElementException::class.java) {
            bag.removeToken(java.util.UUID.randomUUID())
        }
    }

    @Test
    fun `removeListener stops further notifications`() {
        val bag = TokenBag()
        val events = mutableListOf<ModelEvent>()
        val l: (ModelEvent) -> Unit = { events += it }
        bag.addListener(l)
        bag.addToken(Token.createUnit())
        bag.removeListener(l)
        bag.addToken(Token.createUnit())
        assertEquals(1, events.size)
    }

    @Test
    fun `setName fires NameChanged`() {
        val bag = TokenBag()
        val events = mutableListOf<ModelEvent>()
        bag.addListener { events += it }
        bag.name = "Army of Light"
        assertEquals(ModelEvent.NameChanged, events.single())
    }
}
