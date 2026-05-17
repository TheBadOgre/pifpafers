package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import org.junit.jupiter.api.Assertions.assertEquals
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
}
