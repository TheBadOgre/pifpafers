package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.model.PublishSettings
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

class PageRasterizer(private val pageRenderer: PageRenderer) {

    fun rasterizePage(plan: PagePlan, settings: PublishSettings): BufferedImage {
        val out = BufferedImage(plan.widthPx, plan.heightPx, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.color = Color.WHITE
            g.fillRect(0, 0, plan.widthPx, plan.heightPx)

            val side = if (plan.isBackPage) TokenSide.BACK else TokenSide.FRONT
            for (placement in plan.placements) {
                val token = placement.token
                val tokenBleedMm = when (token.kind) {
                    TokenKind.UNIT -> PhysicalSize.HEX_BLEED_WIDTH_MM
                    TokenKind.MODIFIER -> PhysicalSize.CIRCLE_BLEED_DIAMETER_MM
                }
                val targetPx = PhysicalSize.mmToPx(tokenBleedMm, settings.dpi)
                val img = pageRenderer.renderTokenForPrint(
                    token, side, targetPx,
                    renderBleed = settings.renderOverlay,
                    drawCutLine = settings.renderOverlay,
                    dpi = settings.dpi,
                )
                val x = (placement.centerXPx - img.width / 2.0).toInt()
                val y = (placement.centerYPx - img.height / 2.0).toInt()
                g.drawImage(img, x, y, null)
            }
        } finally {
            g.dispose()
        }
        return out
    }
}
