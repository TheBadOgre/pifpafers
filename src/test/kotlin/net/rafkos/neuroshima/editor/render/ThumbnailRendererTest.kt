package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class ThumbnailRendererTest {

    private fun solid(c: Color, w: Int = 50, h: Int = 50) =
        BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also {
            val g = it.createGraphics(); g.color = c; g.fillRect(0, 0, w, h); g.dispose()
        }

    @Test
    fun `same token at same size returns cached dual instance`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png")
        cache.put(asset, solid(Color.RED))
        val tok = Token.createUnit().apply { addLayer(TokenSide.FRONT, Layer.create(asset)) }
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
        val tok = Token.createUnit().apply { addLayer(TokenSide.FRONT, Layer.create(asset)) }
        val renderer = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(8)))
        val small = renderer.tokenThumbnail(tok, 64)
        val big = renderer.tokenThumbnail(tok, 128)
        assertEquals(64, small.width); assertEquals(64, small.height)
        assertEquals(128, big.width); assertEquals(128, big.height)
    }

    @Test
    fun `invalidateToken drops both token and per-side layer entries`() {
        val cache = ImageCache(16)
        val asset = AssetPath.Bundled("a.png")
        cache.put(asset, solid(Color.RED))
        val token = Token.createUnit().apply { addLayer(TokenSide.FRONT, Layer.create(asset)) }
        val r = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(16)))

        val tFirst = r.tokenThumbnail(token, 64)
        val lFirst = r.layerThumbnail(token, TokenSide.FRONT, token.layers(TokenSide.FRONT)[0], 64)
        assertSame(tFirst, r.tokenThumbnail(token, 64))
        assertSame(lFirst, r.layerThumbnail(token, TokenSide.FRONT, token.layers(TokenSide.FRONT)[0], 64))

        r.invalidateToken(token.id)
        assertNotSame(tFirst, r.tokenThumbnail(token, 64))
        assertNotSame(lFirst, r.layerThumbnail(token, TokenSide.FRONT, token.layers(TokenSide.FRONT)[0], 64))
    }

    @Test
    fun `layer thumbnails on FRONT and BACK are cached independently`() {
        val cache = ImageCache(16)
        val a = AssetPath.Bundled("a.png")
        val b = AssetPath.Bundled("b.png")
        cache.put(a, solid(Color.RED))
        cache.put(b, solid(Color.BLUE))
        val token = Token.createUnit().apply {
            addLayer(TokenSide.FRONT, Layer.create(a))
            addLayer(TokenSide.BACK, Layer.create(b))
        }
        val r = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(16)))
        val front = r.layerThumbnail(token, TokenSide.FRONT, token.layers(TokenSide.FRONT)[0], 64)
        val back  = r.layerThumbnail(token, TokenSide.BACK,  token.layers(TokenSide.BACK)[0],  64)
        assertNotSame(front, back)
    }
}
