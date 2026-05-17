package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LayerPropertiesTest {

    @Test
    fun `default has identity values`() {
        val p = LayerProperties()
        assertEquals(0, p.offsetX)
        assertEquals(0, p.offsetY)
        assertEquals(0f, p.rotation)
        assertEquals(1f, p.scale)
        assertEquals(1f, p.opacity)
        assertEquals(0f, p.hue)
        assertEquals(1f, p.saturation)
        assertEquals(1f, p.brightness)
    }

    @Test
    fun `copy with rotation produces normalized value`() {
        val p = LayerProperties().copy(rotation = 375f).normalized()
        assertEquals(15f, p.rotation)
    }

    @Test
    fun `copy with negative rotation produces normalized value`() {
        val p = LayerProperties().copy(rotation = -45f).normalized()
        assertEquals(315f, p.rotation)
    }
}
