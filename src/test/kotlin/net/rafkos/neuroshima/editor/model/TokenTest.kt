package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TokenTest {

    @Test
    fun `unit token has UNIT kind and empty sides`() {
        val t = Token.createUnit()
        assertEquals(TokenKind.UNIT, t.kind)
        assertEquals(0, t.layers(TokenSide.FRONT).size)
        assertEquals(0, t.layers(TokenSide.BACK).size)
    }

    @Test
    fun `modifier token has MODIFIER kind`() {
        assertEquals(TokenKind.MODIFIER, Token.createModifier().kind)
    }

    @Test
    fun `factory generates unique ids`() {
        assertNotEquals(Token.createUnit().id, Token.createUnit().id)
    }

    @Test
    fun `addLayer to FRONT does not affect BACK`() {
        val t = Token.createUnit()
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(TokenSide.FRONT, l)
        assertEquals(listOf(l), t.layers(TokenSide.FRONT))
        assertEquals(emptyList<Layer>(), t.layers(TokenSide.BACK))
    }

    @Test
    fun `addLayer to BACK does not affect FRONT`() {
        val t = Token.createUnit()
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(TokenSide.BACK, l)
        assertEquals(listOf(l), t.layers(TokenSide.BACK))
        assertEquals(emptyList<Layer>(), t.layers(TokenSide.FRONT))
    }

    @Test
    fun `removeLayer by id throws if not on that side`() {
        val t = Token.createUnit()
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(TokenSide.FRONT, l)
        assertThrows(NoSuchElementException::class.java) {
            t.removeLayer(TokenSide.BACK, l.id)
        }
        assertEquals(1, t.layers(TokenSide.FRONT).size)
    }

    @Test
    fun `reorderLayer moves layer to new index on given side`() {
        val t = Token.createUnit()
        val a = Layer.create(AssetPath.Bundled("a.png"))
        val b = Layer.create(AssetPath.Bundled("b.png"))
        val c = Layer.create(AssetPath.Bundled("c.png"))
        t.addLayer(TokenSide.FRONT, a); t.addLayer(TokenSide.FRONT, b); t.addLayer(TokenSide.FRONT, c)
        t.reorderLayer(TokenSide.FRONT, a.id, 2)
        assertEquals(listOf(b, c, a), t.layers(TokenSide.FRONT))
    }

    @Test
    fun `updateLayerProps replaces props on the right side`() {
        val t = Token.createUnit()
        val l = Layer.create(AssetPath.Bundled("a.png"))
        t.addLayer(TokenSide.BACK, l)
        t.updateLayerProps(TokenSide.BACK, l.id, LayerProperties(offsetX = 7))
        assertEquals(7, t.layers(TokenSide.BACK).first().props.offsetX)
    }

    @Test
    fun `findLayerAnywhere returns side and layer`() {
        val t = Token.createUnit()
        val front = Layer.create(AssetPath.Bundled("f.png"))
        val back = Layer.create(AssetPath.Bundled("b.png"))
        t.addLayer(TokenSide.FRONT, front)
        t.addLayer(TokenSide.BACK, back)
        assertEquals(TokenSide.FRONT to front, t.findLayerAnywhere(front.id))
        assertEquals(TokenSide.BACK to back, t.findLayerAnywhere(back.id))
        assertNull(t.findLayerAnywhere(java.util.UUID.randomUUID()))
    }
}
