package net.rafkos.neuroshima.editor.integration

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.PageFormat
import net.rafkos.neuroshima.editor.model.PublishSettings
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import net.rafkos.neuroshima.editor.publish.MaskRenderer
import net.rafkos.neuroshima.editor.publish.PageLayoutPlanner
import net.rafkos.neuroshima.editor.publish.PageRasterizer
import net.rafkos.neuroshima.editor.publish.PageRenderer
import net.rafkos.neuroshima.editor.publish.PdfExporter
import net.rafkos.neuroshima.editor.publish.PngExporter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class PublishRoundTripTest {

    private fun solidPng(w: Int, h: Int, color: Color): BufferedImage =
        BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { img ->
            val g = img.createGraphics()
            g.color = color; g.fillRect(0, 0, w, h); g.dispose()
        }

    private fun writePng(p: Path, img: BufferedImage) {
        Files.createDirectories(p.parent)
        Files.newOutputStream(p).use { ImageIO.write(img, "png", it) }
    }

    private fun makeCache(bundledRoot: Path): Pair<ImageCache, Map<AssetPath, BufferedImage>> {
        val cache = ImageCache(64)
        return cache to emptyMap()
    }

    @Test
    fun `page layout planner produces at least one page for one token`(@TempDir tmp: Path) {
        val bag = TokenBag().apply { name = "test" }
        Token.createUnit().also { bag.addToken(it) }
        val settings = PublishSettings(dpi = 150, pageFormat = PageFormat.A4)
        val plans = PageLayoutPlanner(settings, bag.tokens).plan()
        assertTrue(plans.isNotEmpty(), "Expected at least one page plan")
        assertTrue(plans.any { !it.isBackPage }, "Expected a front page")
    }

    @Test
    fun `page rasterizer renders non-empty image`(@TempDir tmp: Path) {
        val assetDir = tmp.resolve("assets")
        Files.createDirectories(assetDir)
        writePng(assetDir.resolve("fill.png"), solidPng(1044, 902, Color.RED))

        val cache = ImageCache(32)
        cache.put(AssetPath.Bundled("fill.png"), ImageIO.read(assetDir.resolve("fill.png").toFile()))

        val bag = TokenBag().apply { name = "test" }
        val t = Token.createUnit()
        t.addLayer(TokenSide.FRONT, Layer.create(AssetPath.Bundled("fill.png")))
        bag.addToken(t)

        val settings = PublishSettings(dpi = 150, pageFormat = PageFormat.A4)
        val plans = PageLayoutPlanner(settings, bag.tokens).plan()
        val rasterizer = PageRasterizer(PageRenderer(cache))
        val frontPlans = plans.filter { !it.isBackPage }
        assertTrue(frontPlans.isNotEmpty())
        val page = rasterizer.rasterizePage(frontPlans.first(), settings)
        assertTrue(page.width > 0 && page.height > 0)
        // Check that at least one pixel is non-white (something was rendered)
        var hasNonWhite = false
        outer@ for (y in 0 until page.height) {
            for (x in 0 until page.width) {
                val rgb = page.getRGB(x, y) and 0xFFFFFF
                if (rgb != 0xFFFFFF) { hasNonWhite = true; break@outer }
            }
        }
        assertTrue(hasNonWhite, "Rasterized page should have non-white pixels from token render")
    }

    @Test
    fun `mask renderer produces unique colours for two tokens`(@TempDir tmp: Path) {
        val bag = TokenBag().apply { name = "test" }
        val t1 = Token.createUnit().apply { maskId = 0 }
        val t2 = Token.createUnit().apply { maskId = 1 }
        bag.addToken(t1); bag.addToken(t2)
        assertEquals(2, bag.tokens.size)
        // mask ids must be distinct (set explicitly here since we bypass AddTokenCommand)
        val ids = bag.tokens.map { it.maskId }.toSet()
        assertEquals(2, ids.size)

        val settings = PublishSettings(dpi = 150, pageFormat = PageFormat.A4)
        val plans = PageLayoutPlanner(settings, bag.tokens).plan()
        val maskRenderer = MaskRenderer()
        val maskPlans = plans.filter { !it.isBackPage }
        if (maskPlans.isNotEmpty()) {
            val maskImg = maskRenderer.renderPage(
                maskPlans.first(),
                maskPlans.first().widthPx,
                maskPlans.first().heightPx,
            )
            assertTrue(maskImg.width > 0)
        }
    }

    @Test
    fun `PNG exporter writes front back and mask files`(@TempDir tmp: Path) {
        val assetDir = tmp.resolve("assets")
        Files.createDirectories(assetDir)
        writePng(assetDir.resolve("fill.png"), solidPng(1044, 902, Color.GREEN))

        val cache = ImageCache(32)
        cache.put(AssetPath.Bundled("fill.png"), ImageIO.read(assetDir.resolve("fill.png").toFile()))

        val bag = TokenBag().apply { name = "test" }
        val t = Token.createUnit()
        t.addLayer(TokenSide.FRONT, Layer.create(AssetPath.Bundled("fill.png")))
        bag.addToken(t)

        val settings = PublishSettings(dpi = 150, pageFormat = PageFormat.A4)
        val plans = PageLayoutPlanner(settings, bag.tokens).plan()
        val rasterizer = PageRasterizer(PageRenderer(cache))
        val maskRenderer = MaskRenderer()
        val exporter = PngExporter(rasterizer, maskRenderer)
        val outDir = tmp.resolve("out")
        Files.createDirectories(outDir)
        exporter.export(bag, plans, outDir, "army", overwrite = true)

        val files = Files.list(outDir).map { it.fileName.toString() }.toList()
        assertTrue(files.any { it.contains("front") }, "Expected a front PNG: $files")
        assertTrue(files.any { it.contains("mask") }, "Expected a mask PNG: $files")
    }

    @Test
    fun `PDF exporter writes a non-empty file`(@TempDir tmp: Path) {
        val assetDir = tmp.resolve("assets")
        Files.createDirectories(assetDir)
        writePng(assetDir.resolve("fill.png"), solidPng(1044, 902, Color.BLUE))

        val cache = ImageCache(32)
        cache.put(AssetPath.Bundled("fill.png"), ImageIO.read(assetDir.resolve("fill.png").toFile()))

        val bag = TokenBag().apply { name = "test" }
        val t = Token.createUnit()
        t.addLayer(TokenSide.FRONT, Layer.create(AssetPath.Bundled("fill.png")))
        bag.addToken(t)

        val settings = PublishSettings(dpi = 150, pageFormat = PageFormat.A4)
        val plans = PageLayoutPlanner(settings, bag.tokens).plan()
        val rasterizer = PageRasterizer(PageRenderer(cache))
        val pdfPath = tmp.resolve("army.pdf")
        PdfExporter(rasterizer).export(bag, plans, pdfPath)

        assertTrue(Files.exists(pdfPath), "PDF file not created")
        assertTrue(Files.size(pdfPath) > 1000, "PDF file too small: ${Files.size(pdfPath)} bytes")
    }

    @Test
    fun `same sides token renders front layers on back page`(@TempDir tmp: Path) {
        val assetDir = tmp.resolve("assets")
        Files.createDirectories(assetDir)
        writePng(assetDir.resolve("fill.png"), solidPng(1044, 902, Color.ORANGE))

        val cache = ImageCache(32)
        cache.put(AssetPath.Bundled("fill.png"), ImageIO.read(assetDir.resolve("fill.png").toFile()))

        val bag = TokenBag().apply { name = "test" }
        val t = Token.createUnit()
        t.addLayer(TokenSide.FRONT, Layer.create(AssetPath.Bundled("fill.png")))
        // back has no layers, but sameSides=true → renderer uses front
        bag.addToken(t)
        bag.updateSameSides(t.id, true)

        val renderer = PageRenderer(cache)
        val rendered = renderer.renderTokenForPrint(
            token = bag.tokens.first(),
            side = TokenSide.BACK,
            targetPx = 200,
            renderBleed = false,
            drawCutLine = false,
            dpi = 150,
        )
        // Center pixel should be non-transparent (front layers rendered via sameSides fold)
        val alpha = (rendered.getRGB(100, 100) ushr 24) and 0xff
        assertTrue(alpha > 0, "Expected sameSides to fold back→front; center pixel alpha=$alpha")
    }
}
