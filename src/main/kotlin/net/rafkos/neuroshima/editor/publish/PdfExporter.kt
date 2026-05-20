package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.model.TokenBag
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class PdfExporter(private val rasterizer: PageRasterizer) {
    fun export(bag: TokenBag, plans: List<PagePlan>, outputFile: Path) {
        val settings = bag.printSettings
        val widthPt  = PhysicalSize.mmToPt(settings.pageFormat.widthMm).toFloat()
        val heightPt = PhysicalSize.mmToPt(settings.pageFormat.heightMm).toFloat()
        val tmp = outputFile.resolveSibling("${outputFile.fileName}.tmp")

        PDDocument().use { doc ->
            for (plan in plans) {
                val pdPage = PDPage(PDRectangle(widthPt, heightPt))
                doc.addPage(pdPage)
                val raster = rasterizer.rasterizePage(plan, settings)
                val pdImg = LosslessFactory.createFromImage(doc, raster)
                PDPageContentStream(doc, pdPage).use { cs ->
                    cs.drawImage(pdImg, 0f, 0f, widthPt, heightPt)
                }
            }
            doc.save(tmp.toFile())
        }
        Files.move(tmp, outputFile, StandardCopyOption.REPLACE_EXISTING)
    }
}
