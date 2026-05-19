package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class DualSideRenderTest {

    private fun solid(c: Color, w: Int = 1044, h: Int = 902) =
        BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also {
            val g = it.createGraphics(); g.color = c; g.fillRect(0, 0, w, h); g.dispose()
        }

    @Test
    fun `renderDual output is square with the requested size`() {
        val cache = ImageCache(8)
        val front = AssetPath.Bundled("front.png")
        val back  = AssetPath.Bundled("back.png")
        cache.put(front, solid(Color.RED))
        cache.put(back, solid(Color.BLUE))
        val token = Token.createUnit().apply {
            addLayer(TokenSide.FRONT, Layer.create(front))
            addLayer(TokenSide.BACK, Layer.create(back))
        }
        val renderer = TokenRenderer(cache, ProcessedLayerCache(8))
        val img = renderer.renderDual(token, sizePx = 100)
        assertEquals(100, img.width)
        assertEquals(100, img.height)
    }

    @Test
    fun `renderDual shows front at top-left and back at bottom-right`() {
        val cache = ImageCache(8)
        val front = AssetPath.Bundled("front.png")
        val back  = AssetPath.Bundled("back.png")
        cache.put(front, solid(Color.RED))
        cache.put(back, solid(Color.BLUE))
        val token = Token.createUnit().apply {
            addLayer(TokenSide.FRONT, Layer.create(front))
            addLayer(TokenSide.BACK, Layer.create(back))
        }
        val renderer = TokenRenderer(cache, ProcessedLayerCache(8))
        val img = renderer.renderDual(token, sizePx = 200)
        val tl = img.getRGB(80, 80)
        // (180, 150): x=180 is outside the front image [0,159], y=150 maps to back-local (140,110)
        // → logical ≈ (852, 616) which is well inside the hex shape.
        val br = img.getRGB(180, 150)
        assertTrue(((tl ushr 16) and 0xff) > 200) { "top-left should be red-ish, got 0x${Integer.toHexString(tl)}" }
        assertTrue((br and 0xff) > 200) { "right side should be blue-ish (back side, outside front overlay), got 0x${Integer.toHexString(br)}" }
    }

}
