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
        val br = img.getRGB(180, 180)
        assertTrue(((tl ushr 16) and 0xff) > 200) { "top-left should be red-ish, got 0x${Integer.toHexString(tl)}" }
        assertTrue((br and 0xff) > 200) { "bottom-right should be blue-ish, got 0x${Integer.toHexString(br)}" }
    }

    @Test
    fun `renderDual draws dark grey outline pixels around front shape`() {
        val cache = ImageCache(8)
        val front = AssetPath.Bundled("front.png")
        cache.put(front, solid(Color.RED))
        val token = Token.createUnit().apply { addLayer(TokenSide.FRONT, Layer.create(front)) }
        val renderer = TokenRenderer(cache, ProcessedLayerCache(8))
        val img = renderer.renderDual(token, sizePx = 200)
        var found = false
        for (y in 0 until 160) {
            for (x in 0 until 160) {
                val argb = img.getRGB(x, y)
                val a = (argb ushr 24) and 0xff
                if (a == 0) continue
                val r = (argb ushr 16) and 0xff
                val g = (argb ushr 8) and 0xff
                val b = argb and 0xff
                if (r < 120 && g < 120 && b < 120 && kotlin.math.abs(r - g) < 16 && kotlin.math.abs(g - b) < 16
                    && a in 150..230) {
                    found = true; break
                }
            }
            if (found) break
        }
        assertTrue(found, "expected at least one dark-grey outline pixel inside the front quadrant")
    }
}
