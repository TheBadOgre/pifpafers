package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TokenTest {

    @Test
    fun `unit token has UNIT kind`() {
        val t = Token.createUnit()
        assertEquals(TokenKind.UNIT, t.kind)
        assertEquals(0, t.layers.size)
    }

    @Test
    fun `modifier token has MODIFIER kind`() {
        val t = Token.createModifier()
        assertEquals(TokenKind.MODIFIER, t.kind)
    }

    @Test
    fun `factory generates unique ids`() {
        assertNotEquals(Token.createUnit().id, Token.createUnit().id)
    }

    @Test
    fun `addLayer appends to top`() {
        val t = Token.createUnit()
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        val l2 = Layer.create(AssetPath.Bundled("b.png"))
        t.addLayer(l1)
        t.addLayer(l2)
        assertEquals(listOf(l1, l2), t.layers.toList())
    }

    @Test
    fun `removeLayer by id removes matching layer`() {
        val t = Token.createUnit()
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(l1)
        t.removeLayer(l1.id)
        assertEquals(0, t.layers.size)
    }

    @Test
    fun `removeLayer with unknown id throws`() {
        val t = Token.createUnit()
        assertThrows(NoSuchElementException::class.java) {
            t.removeLayer(java.util.UUID.randomUUID())
        }
    }

    @Test
    fun `reorderLayer moves layer to new index`() {
        val t = Token.createUnit()
        val l1 = Layer.create(AssetPath.Bundled("a.png"))
        val l2 = Layer.create(AssetPath.Bundled("b.png"))
        val l3 = Layer.create(AssetPath.Bundled("c.png"))
        t.addLayer(l1); t.addLayer(l2); t.addLayer(l3)
        t.reorderLayer(l1.id, 2)
        assertEquals(listOf(l2, l3, l1), t.layers.toList())
    }

    @Test
    fun `updateLayerProps replaces the layer's properties`() {
        val t = Token.createUnit()
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(l)
        t.updateLayerProps(l.id, LayerProperties(offsetX = 50))
        assertEquals(50, t.layers.first().props.offsetX)
    }
}
