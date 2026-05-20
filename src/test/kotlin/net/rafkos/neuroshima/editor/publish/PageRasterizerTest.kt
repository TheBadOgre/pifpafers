package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.PageFormat
import net.rafkos.neuroshima.editor.model.PublishSettings
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

class PageRasterizerTest {

    @Test
    fun `rasterizePage produces page-sized image with white background`() {
        val asset = AssetPath.parse("bundled://test/x.png")
        val cache = ImageCache(maxEntries = 16)
        val src = BufferedImage(LOGICAL_CANVAS_W, LOGICAL_CANVAS_H, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics(); g.color = Color.RED; g.fillRect(0, 0, width, height); g.dispose()
        }
        cache.put(asset, src)
        val token = Token(UUID.randomUUID(), TokenKind.UNIT, maskId = 0)
        token.addLayer(TokenSide.FRONT, Layer(UUID.randomUUID(), asset, LayerProperties(0, 0, 0f, 1f, 1f, 0f, 1f, 1f, false)))

        val settings = PublishSettings(dpi = 150, pageFormat = PageFormat.A5, renderOverlay = false)
        val plans = PageLayoutPlanner(settings, listOf(token)).plan()
        val front = plans[0]

        val raster = PageRasterizer(PageRenderer(cache))
        val img = raster.rasterizePage(front, settings)
        assertEquals(front.widthPx, img.width)
        assertEquals(front.heightPx, img.height)
        assertEquals(0xFFFFFF, img.getRGB(0, 0) and 0xFFFFFF)
    }
}
