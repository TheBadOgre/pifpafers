package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_H
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_W
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PageRendererTest {
    private fun fakeAsset(color: Color): BufferedImage =
        BufferedImage(LOGICAL_CANVAS_W, LOGICAL_CANVAS_H, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics()
            g.color = color
            g.fillRect(0, 0, width, height)
            g.dispose()
        }

    private fun fakeBag(): Triple<ImageCache, Token, PageRenderer> {
        val cache = ImageCache(maxEntries = 16)
        val asset = AssetPath.parse("bundled://test/x.png")
        cache.put(asset, fakeAsset(Color.RED))
        val token = Token(UUID.randomUUID(), TokenKind.UNIT, maskId = 0)
        token.addLayer(TokenSide.FRONT, Layer(UUID.randomUUID(), asset, LayerProperties(0, 0, 0f, 1f, 1f, 0f, 1f, 1f, false)))
        return Triple(cache, token, PageRenderer(cache))
    }

    @Test
    fun `renderBleed=false leaves area beyond clipShape transparent`() {
        val (_, token, renderer) = fakeBag()
        val img = renderer.renderTokenForPrint(token, TokenSide.FRONT, 256, renderBleed = false, drawCutLine = false)
        val alpha = (img.getRGB(0, 0) shr 24) and 0xFF
        assertEquals(0, alpha)
    }

    @Test
    fun `renderBleed=true renders token content at center`() {
        val (_, token, renderer) = fakeBag()
        val img = renderer.renderTokenForPrint(token, TokenSide.FRONT, 256, renderBleed = true, drawCutLine = false)
        val alpha = (img.getRGB(128, 128) shr 24) and 0xFF
        assertTrue(alpha > 0, "Center pixel must be non-transparent")
    }

    @Test
    fun `drawCutLine=true introduces dark pixels along token edge`() {
        val (_, token, renderer) = fakeBag()
        val with    = renderer.renderTokenForPrint(token, TokenSide.FRONT, 256, renderBleed = true, drawCutLine = true)
        val without = renderer.renderTokenForPrint(token, TokenSide.FRONT, 256, renderBleed = true, drawCutLine = false)
        assertNotEquals(with.hashCode(), without.hashCode())
    }

    @Test
    fun `sameSides token BACK render equals FRONT render`() {
        val cache = ImageCache(maxEntries = 16)
        val front = AssetPath.parse("bundled://test/f.png")
        val back  = AssetPath.parse("bundled://test/b.png")
        cache.put(front, fakeAsset(Color.RED))
        cache.put(back,  fakeAsset(Color.BLUE))
        val t = Token(UUID.randomUUID(), TokenKind.UNIT, sameSides = true)
        t.addLayer(TokenSide.FRONT, Layer(UUID.randomUUID(), front, LayerProperties(0, 0, 0f, 1f, 1f, 0f, 1f, 1f, false)))
        t.addLayer(TokenSide.BACK,  Layer(UUID.randomUUID(), back,  LayerProperties(0, 0, 0f, 1f, 1f, 0f, 1f, 1f, false)))
        val renderer = PageRenderer(cache)
        val a = renderer.renderTokenForPrint(t, TokenSide.FRONT, 128, true, false)
        val b = renderer.renderTokenForPrint(t, TokenSide.BACK,  128, true, false)
        assertEquals(a.getRGB(64, 64), b.getRGB(64, 64))
    }
}
