package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class LayerRendererTest {

    private fun solid(color: Color, w: Int = 4, h: Int = 4): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, w, h)
        g.dispose()
        return img
    }

    @Test
    fun `identity props returns the same image instance`() {
        val src = solid(Color.RED)
        val out = LayerRenderer.applyPixelOps(src, LayerProperties())
        assertSame(src, out)
    }

    @Test
    fun `opacity 0_5 halves alpha of fully opaque pixel`() {
        val src = solid(Color.RED)
        val out = LayerRenderer.applyPixelOps(src, LayerProperties(opacity = 0.5f))
        val argb = out.getRGB(0, 0)
        val alpha = (argb ushr 24) and 0xff
        assert(alpha in 120..136) { "expected ~128, got $alpha" }
    }

    @Test
    fun `brightness 0 yields black RGB`() {
        val src = solid(Color.RED)
        val out = LayerRenderer.applyPixelOps(src, LayerProperties(brightness = 0f))
        val argb = out.getRGB(0, 0)
        val r = (argb ushr 16) and 0xff
        val g = (argb ushr 8) and 0xff
        val b = argb and 0xff
        assertEquals(0, r); assertEquals(0, g); assertEquals(0, b)
    }

    @Test
    fun `saturation 0 yields gray`() {
        val src = solid(Color.RED)
        val out = LayerRenderer.applyPixelOps(src, LayerProperties(saturation = 0f))
        val argb = out.getRGB(0, 0)
        val r = (argb ushr 16) and 0xff
        val g = (argb ushr 8) and 0xff
        val b = argb and 0xff
        assertEquals(r, g)
        assertEquals(g, b)
    }
}
