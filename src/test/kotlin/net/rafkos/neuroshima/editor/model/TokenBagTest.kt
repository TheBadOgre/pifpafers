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
        val added = events.filterIsInstance<ModelEvent.TokenAdded>().single()
        assertEquals(t.id, added.tokenId)
    }

    @Test
    fun `addLayer fires LayerAdded with side`() {
        val bag = TokenBag()
        val t = Token.createUnit()
        bag.addToken(t)
        val events = mutableListOf<ModelEvent>()
        bag.addListener { events += it }
        val layer = Layer.create(AssetPath.Bundled("a.png"))
        bag.addLayer(t.id, TokenSide.BACK, layer)
        val ev = events.single() as ModelEvent.LayerAdded
        assertEquals(TokenSide.BACK, ev.side)
        assertEquals(layer.id, ev.layerId)
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
        assertEquals(2, events.size) // TokenAdded + MaskIdAssigned
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
