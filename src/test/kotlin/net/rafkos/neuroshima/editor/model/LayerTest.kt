package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class LayerTest {

    @Test
    fun `layer holds id, asset path, and properties`() {
        val id = UUID.randomUUID()
        val asset = AssetPath.Bundled("a.png")
        val props = LayerProperties(offsetX = 10)
        val layer = Layer(id = id, assetPath = asset, props = props)
        assertEquals(id, layer.id)
        assertEquals(asset, layer.assetPath)
        assertEquals(10, layer.props.offsetX)
    }

    @Test
    fun `factory generates unique ids`() {
        val a = Layer.create(AssetPath.Bundled("x.png"))
        val b = Layer.create(AssetPath.Bundled("x.png"))
        assertNotEquals(a.id, b.id)
    }
}
