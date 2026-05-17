package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB

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

    @Test
    fun `vectorized ops match legacy per-pixel within 2 of 255 across hsb-opacity permutations`() {
        val src = BufferedImage(16, 16, TYPE_INT_ARGB)
        for (y in 0 until 16) for (x in 0 until 16) {
            val r = (x * 16); val g = (y * 16); val b = ((x + y) * 8) and 0xff
            src.setRGB(x, y, (0xff shl 24) or (r shl 16) or (g shl 8) or b)
        }
        val permutations = listOf(
            LayerProperties(opacity = 0.5f),
            LayerProperties(brightness = 0.7f),
            LayerProperties(saturation = 0.3f),
            LayerProperties(hue = 0.25f),
            LayerProperties(opacity = 0.8f, brightness = 0.9f, saturation = 0.6f, hue = 0.1f),
        )
        for (p in permutations) {
            val ref = LayerRendererLegacy.applyPixelOps(src, p)
            val out = LayerRenderer.applyPixelOps(src, p)
            var maxDelta = 0
            for (y in 0 until 16) for (x in 0 until 16) {
                val a = ref.getRGB(x, y); val b = out.getRGB(x, y)
                for (shift in intArrayOf(0, 8, 16, 24)) {
                    val d = kotlin.math.abs(((a ushr shift) and 0xff) - ((b ushr shift) and 0xff))
                    if (d > maxDelta) maxDelta = d
                }
            }
            assert(maxDelta <= 2) { "max delta $maxDelta for $p" }
        }
    }
}
