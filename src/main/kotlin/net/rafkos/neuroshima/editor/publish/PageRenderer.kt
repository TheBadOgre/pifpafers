package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import net.rafkos.neuroshima.editor.render.AffineBuilder
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_H
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_W
import net.rafkos.neuroshima.editor.render.LayerRenderer
import net.rafkos.neuroshima.editor.render.TokenShape
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.max

class PageRenderer(private val imageCache: ImageCache) {

    fun renderTokenForPrint(
        token: Token,
        side: TokenSide,
        targetPx: Int,
        renderBleed: Boolean,
        drawCutLine: Boolean,
        dpi: Int = 300,
    ): BufferedImage {
        val effectiveSide = if (token.sameSides && side == TokenSide.BACK) TokenSide.FRONT else side
        val shape = TokenShape.forKind(token.kind)
        val bleedBounds = shape.bleedShape().bounds
        val srcW = bleedBounds.width
        val srcH = bleedBounds.height
        val srcX0 = bleedBounds.x
        val srcY0 = bleedBounds.y
        val clip = if (renderBleed) shape.bleedShape() else shape.clipShape()

        val out = BufferedImage(targetPx, targetPx, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)

            val fit = targetPx.toDouble() / srcW
            val scaledH = srcH * fit
            val offsetY = (targetPx - scaledH) / 2.0
            g.translate(-srcX0 * fit, offsetY - srcY0 * fit)
            g.scale(fit, fit)

            val savedClip = g.clip
            g.clip = clip

            val centerX = LOGICAL_CANVAS_W / 2.0
            val centerY = LOGICAL_CANVAS_H / 2.0
            for (layer in token.layers(effectiveSide)) {
                val source = imageCache.get(layer.assetPath) ?: continue
                val processed = LayerRenderer.applyPixelOps(source, layer.props)
                val xform = AffineBuilder.build(
                    props = layer.props,
                    canvasCenterX = centerX,
                    canvasCenterY = centerY,
                    imageWidth = processed.width,
                    imageHeight = processed.height,
                )
                g.drawImage(processed, xform, null)
            }

            g.clip = savedClip

            if (drawCutLine) {
                val cutPx = max(1, dpi / 150)
                val saved = g.composite
                g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
                g.color = Color.BLACK
                g.stroke = BasicStroke(cutPx.toFloat())
                g.draw(shape.clipShape())
                g.composite = saved
            }
        } finally {
            g.dispose()
        }
        if (token.kind != TokenKind.UNIT) return out
        val rotated = BufferedImage(targetPx, targetPx, BufferedImage.TYPE_INT_ARGB)
        val gr = rotated.createGraphics()
        try {
            gr.rotate(Math.PI / 2, targetPx / 2.0, targetPx / 2.0)
            gr.drawImage(out, 0, 0, null)
        } finally {
            gr.dispose()
        }
        return rotated
    }
}
