package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.PageFormat
import net.rafkos.neuroshima.editor.model.PublishSettings
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_H
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_W
import org.apache.pdfbox.Loader
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfExporterTest {

    @Test
    fun `produces a valid PDF with one page per plan`(@TempDir tmp: Path) {
        val cache = ImageCache(maxEntries = 16)
        val asset = AssetPath.parse("bundled://test/x.png")
        cache.put(asset, BufferedImage(LOGICAL_CANVAS_W, LOGICAL_CANVAS_H, BufferedImage.TYPE_INT_ARGB).apply {
            val g = createGraphics(); g.color = Color.RED; g.fillRect(0, 0, width, height); g.dispose()
        })
        val bag = TokenBag().apply {
            updatePrintSettings(PublishSettings(dpi = 150, pageFormat = PageFormat.A5))
        }
        val t = Token(UUID.randomUUID(), TokenKind.UNIT, maskId = 0)
        t.addLayer(TokenSide.FRONT, Layer(UUID.randomUUID(), asset, LayerProperties(0, 0, 0f, 1f, 1f, 0f, 1f, 1f, false)))
        bag.addToken(t)

        val plans = PageLayoutPlanner(bag.printSettings, bag.tokens).plan()
        val raster = PageRasterizer(PageRenderer(cache))

        val target = tmp.resolve("army.pdf")
        PdfExporter(raster).export(bag, plans, target)

        val bytes = target.readBytes()
        assertEquals('%'.code.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        Loader.loadPDF(target.toFile()).use { doc ->
            assertEquals(plans.size, doc.numberOfPages)
        }
    }
}
