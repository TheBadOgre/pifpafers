package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.render.TokenShape
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

class MaskRenderer {

    fun renderPage(page: PagePlan, pageWidthPx: Int, pageHeightPx: Int, pxPerMm: Double = 1.0): BufferedImage {
        val out = BufferedImage(pageWidthPx, pageHeightPx, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
            g.color = Color.BLACK
            g.fillRect(0, 0, pageWidthPx, pageHeightPx)

            for (placement in page.placements) {
                drawTokenMask(g, placement, pxPerMm)
            }
        } finally {
            g.dispose()
        }
        return out
    }

    private fun drawTokenMask(g: Graphics2D, placement: PagePlan.Placement, pxPerMm: Double) {
        val token = placement.token
        val shape = TokenShape.forKind(token.kind)
        val clip = shape.clipShape()
        val bounds = clip.bounds
        val tightMm = when (token.kind) {
            TokenKind.UNIT -> PhysicalSize.HEX_TIGHT_WIDTH_MM
            TokenKind.MODIFIER -> PhysicalSize.CIRCLE_DIAMETER_MM
        }
        val scale = (tightMm * pxPerMm) / bounds.width.toDouble()

        val savedTx = g.transform
        try {
            val tx = AffineTransform()
            tx.translate(placement.centerXPx, placement.centerYPx)
            if (token.kind == TokenKind.UNIT) tx.rotate(Math.PI / 2)
            tx.scale(scale, scale)
            tx.translate(-(bounds.x + bounds.width / 2.0), -(bounds.y + bounds.height / 2.0))
            g.transform(tx)
            g.color = Color(MaskIdCodec.encode(token.maskId))
            g.fill(clip)
        } finally {
            g.transform = savedTx
        }
    }
}
