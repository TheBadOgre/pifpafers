package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.model.TokenBag
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
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
            val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
            for (plan in plans) {
                val pdPage = PDPage(PDRectangle(widthPt, heightPt))
                doc.addPage(pdPage)
                val raster = rasterizer.rasterizePage(plan, settings)
                val pdImg = LosslessFactory.createFromImage(doc, raster)
                PDPageContentStream(doc, pdPage).use { cs ->
                    cs.drawImage(pdImg, 0f, 0f, widthPt, heightPt)
                    drawScaleBar(cs, font, widthPt, PhysicalSize.mmToPt(settings.marginMm).toFloat())
                }
            }
            doc.save(tmp.toFile())
        }
        Files.move(tmp, outputFile, StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * Draws two side-by-side scale rulers in the bottom-right corner.
     * Entirely within the bottom margin strip (y ∈ [0, marginPt]) so no overlap with tokens.
     */
    private fun drawScaleBar(cs: PDPageContentStream, font: PDType1Font, widthPt: Float, marginPt: Float) {
        val fontSize = 7f
        val tickH = 3.5f
        val lineWidth = 0.6f
        val barGap = 10f

        val cmLenPt = PhysicalSize.mmToPt(10.0).toFloat()
        val inchLenPt = 72f  // 1 inch = 72pt by PDF spec

        // total element height: ticks below + ticks above + gap + label
        val elementH = tickH + tickH + 2f + fontSize
        // center vertically in the bottom margin strip
        val barY = (marginPt - elementH) / 2f + tickH
        val labelY = barY + tickH + 2f

        // center both bars horizontally on the page
        val totalWidth = cmLenPt + barGap + inchLenPt
        val cmLeftX    = (widthPt - totalWidth) / 2f
        val cmRightX   = cmLeftX + cmLenPt
        val inchLeftX  = cmRightX + barGap
        val inchRightX = inchLeftX + inchLenPt

        cs.setLineWidth(lineWidth)
        cs.setStrokingColor(0f, 0f, 0f)
        cs.setNonStrokingColor(0f, 0f, 0f)

        // 1 cm bar
        cs.moveTo(cmLeftX,  barY - tickH); cs.lineTo(cmLeftX,  barY + tickH)
        cs.moveTo(cmLeftX,  barY);         cs.lineTo(cmRightX, barY)
        cs.moveTo(cmRightX, barY - tickH); cs.lineTo(cmRightX, barY + tickH)
        cs.stroke()
        cs.beginText()
        cs.setFont(font, fontSize)
        cs.newLineAtOffset(cmLeftX, labelY)
        cs.showText("1 cm")
        cs.endText()

        // 1" bar
        cs.moveTo(inchLeftX,  barY - tickH); cs.lineTo(inchLeftX,  barY + tickH)
        cs.moveTo(inchLeftX,  barY);         cs.lineTo(inchRightX, barY)
        cs.moveTo(inchRightX, barY - tickH); cs.lineTo(inchRightX, barY + tickH)
        cs.stroke()
        cs.beginText()
        cs.setFont(font, fontSize)
        cs.newLineAtOffset(inchLeftX, labelY)
        cs.showText("1\"")
        cs.endText()
    }
}
