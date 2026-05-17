package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class TokenRendererTest {

    private fun solid(color: Color, w: Int = 100, h: Int = 100): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, w, h)
        g.dispose()
        return img
    }

    @Test
    fun `renders single-layer token at output size`() {
        val cache = ImageCache(16)
        val asset = AssetPath.Bundled("solid.png")
        cache.put(asset, solid(Color.RED))
        val token = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        val renderer = TokenRenderer(cache, ProcessedLayerCache(16))
        val out = renderer.render(token, sizePx = 100)
        assertEquals(100, out.width)
        assertEquals(100, out.height)
        val argb = out.getRGB(50, 50)
        val alpha = (argb ushr 24) and 0xff
        val r = (argb ushr 16) and 0xff
        assert(alpha > 200) { "alpha=$alpha" }
        assertEquals(255, r)
    }

    @Test
    fun `renders empty token as transparent`() {
        val renderer = TokenRenderer(ImageCache(4), ProcessedLayerCache(4))
        val out = renderer.render(Token.createUnit(), sizePx = 50)
        val alpha = (out.getRGB(25, 25) ushr 24) and 0xff
        assertEquals(0, alpha)
    }

    @Test
    fun `respects layer stacking order (top wins)`() {
        val cache = ImageCache(16)
        val red = AssetPath.Bundled("red.png")
        val blue = AssetPath.Bundled("blue.png")
        cache.put(red, solid(Color.RED))
        cache.put(blue, solid(Color.BLUE))
        val token = Token.createUnit().apply {
            addLayer(Layer.create(red))
            addLayer(Layer.create(blue)) // top
        }
        val out = TokenRenderer(cache, ProcessedLayerCache(16)).render(token, 100)
        val argb = out.getRGB(50, 50)
        val r = (argb ushr 16) and 0xff
        val b = argb and 0xff
        assertEquals(0, r)
        assertEquals(255, b)
    }

    @Test
    fun `fits 490 logical token area into thumbnail square - offset layer lands proportionally`() {
        val cache = ImageCache(16)
        val asset = AssetPath.Bundled("dot.png")
        cache.put(asset, solid(Color.RED, w = 20, h = 20))
        // Layer offset +200 logical px on x axis. At sizePx=98 the fit-scale = 98/490 = 0.2.
        // Expected on-screen offset from center: 200 * 0.2 = 40 px. Image size 20 * 0.2 = 4 px.
        // So red pixel should appear near (49+40, 49) = (89, 49), not near native (49+200,49).
        val token = Token.createUnit().apply {
            addLayer(Layer.create(asset, LayerProperties(offsetX = 200, offsetY = 0)))
        }
        val out = TokenRenderer(cache, ProcessedLayerCache(16)).render(token, sizePx = 98)
        val argbHit = out.getRGB(89, 49)
        val alphaHit = (argbHit ushr 24) and 0xff
        assert(alphaHit > 200) { "expected red dot near (89,49), alpha=$alphaHit" }
        val argbCenter = out.getRGB(49, 49)
        val alphaCenter = (argbCenter ushr 24) and 0xff
        assertEquals(0, alphaCenter, "center should be transparent; got alpha=$alphaCenter")
    }
}
