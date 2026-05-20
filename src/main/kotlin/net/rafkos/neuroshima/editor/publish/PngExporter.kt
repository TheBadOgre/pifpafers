package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.model.TokenBag
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

class PngExporter(
    private val rasterizer: PageRasterizer,
    private val maskRenderer: MaskRenderer,
) {
    fun export(
        bag: TokenBag,
        plans: List<PagePlan>,
        outputDir: Path,
        baseName: String,
        overwrite: Boolean,
    ): ExportResult {
        val settings = bag.printSettings
        var written = 0
        var skipped = 0
        val failed = mutableListOf<String>()
        val pxPerMm = settings.dpi / 25.4

        var i = 0
        while (i < plans.size) {
            val front = plans[i]
            val back = plans.getOrNull(i + 1)?.takeIf { it.isBackPage && it.pageIndex == front.pageIndex }
            val pair = front.pageIndex + 1

            fun writePng(img: java.awt.image.BufferedImage, suffix: String) {
                val target = outputDir.resolve("${baseName}_${pair}_${suffix}.png")
                if (Files.exists(target) && !overwrite) { skipped += 1; return }
                val tmp = target.resolveSibling("${target.fileName}.tmp")
                try {
                    Files.newOutputStream(tmp).use { ImageIO.write(img, "PNG", it) }
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
                    written += 1
                } catch (ex: Exception) {
                    failed += target.fileName.toString()
                    try { Files.deleteIfExists(tmp) } catch (_: Exception) {}
                }
            }

            writePng(rasterizer.rasterizePage(front, settings), "front")
            if (back != null) writePng(rasterizer.rasterizePage(back, settings), "back")
            writePng(maskRenderer.renderPage(front, front.widthPx, front.heightPx, pxPerMm), "mask")

            i += if (back != null) 2 else 1
        }
        return ExportResult(written, skipped, failed)
    }
}
