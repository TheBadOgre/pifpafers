package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TokenRendererSameSidesTest {
    private fun fakeAsset(color: Color): BufferedImage =
        BufferedImage(LOGICAL_CANVAS_W, LOGICAL_CANVAS_H, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics()
            g.color = color
            g.fillRect(0, 0, width, height)
            g.dispose()
        }

    @Test
    fun `sameSides=true makes BACK render equal FRONT render`() {
        val imageCache = ImageCache(maxEntries = 16)
        val processedCache = ProcessedLayerCache(maxEntries = 16)
        val frontAsset = AssetPath.parse("bundled://test/front.png")
        val backAsset = AssetPath.parse("bundled://test/back.png")
        imageCache.put(frontAsset, fakeAsset(Color.RED))
        imageCache.put(backAsset, fakeAsset(Color.BLUE))

        val token = Token(UUID.randomUUID(), TokenKind.UNIT, sameSides = true)
        token.addLayer(TokenSide.FRONT, Layer(UUID.randomUUID(), frontAsset, LayerProperties(0, 0, 0f, 1f, 1f, 0f, 1f, 1f, false)))
        token.addLayer(TokenSide.BACK,  Layer(UUID.randomUUID(), backAsset,  LayerProperties(0, 0, 0f, 1f, 1f, 0f, 1f, 1f, false)))

        val renderer = TokenRenderer(imageCache, processedCache)
        val front = renderer.render(token, TokenSide.FRONT, 128)
        val back  = renderer.render(token, TokenSide.BACK,  128)
        assertEquals(front.getRGB(64, 64), back.getRGB(64, 64))
    }
}
