package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class ThumbnailRendererTest {

    private fun solid(c: Color, w: Int = 50, h: Int = 50) = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also {
        val g = it.createGraphics(); g.color = c; g.fillRect(0, 0, w, h); g.dispose()
    }

    @Test
    fun `same token at same size returns cached instance`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png")
        cache.put(asset, solid(Color.RED))
        val tok = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        val renderer = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(8)))
        val first = renderer.tokenThumbnail(tok, 64)
        val second = renderer.tokenThumbnail(tok, 64)
        assertSame(first, second)
    }

    @Test
    fun `same token at different size returns distinct instances of correct size`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png")
        cache.put(asset, solid(Color.RED))
        val tok = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        val renderer = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(8)))
        val small = renderer.tokenThumbnail(tok, 64)
        val big = renderer.tokenThumbnail(tok, 128)
        assertEquals(64, small.width); assertEquals(64, small.height)
        assertEquals(128, big.width); assertEquals(128, big.height)
    }

    @Test
    fun `invalidate forces re-render`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png")
        cache.put(asset, solid(Color.RED))
        val tok = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        val renderer = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(8)))
        val first = renderer.tokenThumbnail(tok, 64)
        renderer.invalidateToken(tok.id)
        val second = renderer.tokenThumbnail(tok, 64)
        assert(first !== second) { "expected new instance after invalidate" }
    }

    @Test
    fun `layerThumbnail returns single-layer render distinct from full token thumbnail`() {
        val cache = ImageCache(16)
        val red = AssetPath.Bundled("red.png")
        val blue = AssetPath.Bundled("blue.png")
        cache.put(red, solid(Color.RED))
        cache.put(blue, solid(Color.BLUE))
        val token = Token.createUnit().apply {
            addLayer(Layer.create(red))
            addLayer(Layer.create(blue)) // top
        }
        val r = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(16)))

        val full = r.tokenThumbnail(token, 98)
        val redOnly = r.layerThumbnail(token, token.layers[0], 98)
        val blueOnly = r.layerThumbnail(token, token.layers[1], 98)

        assertEquals(255, (full.getRGB(49, 49) and 0xff), "full thumbnail top layer is blue")
        assertEquals(255, ((redOnly.getRGB(49, 49) ushr 16) and 0xff),
            "red-only thumbnail center is red")
        assertEquals(255, (blueOnly.getRGB(49, 49) and 0xff),
            "blue-only thumbnail center is blue")
    }

    @Test
    fun `invalidateToken drops both token and layer cache entries`() {
        val cache = ImageCache(16)
        val asset = AssetPath.Bundled("a.png")
        cache.put(asset, solid(Color.RED))
        val token = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        val r = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(16)))

        val tFirst = r.tokenThumbnail(token, 64)
        val lFirst = r.layerThumbnail(token, token.layers[0], 64)
        assertSame(tFirst, r.tokenThumbnail(token, 64))
        assertSame(lFirst, r.layerThumbnail(token, token.layers[0], 64))

        r.invalidateToken(token.id)
        assertNotSame(tFirst, r.tokenThumbnail(token, 64))
        assertNotSame(lFirst, r.layerThumbnail(token, token.layers[0], 64))
    }
}
