package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import java.awt.RenderingHints
import java.awt.image.BufferedImage

const val LOGICAL_CANVAS_W: Int = 1044
const val LOGICAL_CANVAS_H: Int = 902

private const val DUAL_SIDE_SCALE_UNIT: Double = 0.8
private const val DUAL_SIDE_SCALE_MODIFIER: Double = 0.55

class TokenRenderer(
    private val imageCache: ImageCache,
    private val processedCache: ProcessedLayerCache,
) {
    fun render(token: Token, side: TokenSide, sizePx: Int): BufferedImage {
        val effectiveSide = if (token.sameSides && side == TokenSide.BACK) TokenSide.FRONT else side
        val shape  = TokenShape.forKind(token.kind)
        val srcW   = shape.boundingW
        val srcH   = shape.boundingH
        val srcX0  = shape.boundingX0
        val srcY0  = shape.boundingY0

        val out = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g   = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val fit     = sizePx.toDouble() / srcW
            val scaledH = srcH * fit
            val offsetY = (sizePx - scaledH) / 2.0
            g.translate(-srcX0 * fit, offsetY - srcY0 * fit)
            g.scale(fit, fit)
            g.clip = shape.clipShape()
            val centerX = LOGICAL_CANVAS_W / 2.0
            val centerY = LOGICAL_CANVAS_H / 2.0
            for (layer in token.layers(effectiveSide)) {
                val source = imageCache.get(layer.assetPath) ?: continue
                val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
                val processed = processedCache.get(key)
                    ?: LayerRenderer.applyPixelOps(source, layer.props).also { processedCache.put(key, it) }
                val xform = AffineBuilder.build(
                    props = layer.props,
                    canvasCenterX = centerX,
                    canvasCenterY = centerY,
                    imageWidth = processed.width,
                    imageHeight = processed.height,
                )
                g.drawImage(processed, xform, null)
            }
        } finally {
            g.dispose()
        }
        return out
    }

    fun renderDual(token: Token, sizePx: Int): BufferedImage {
        val scale = if (token.kind == TokenKind.MODIFIER) DUAL_SIDE_SCALE_MODIFIER else DUAL_SIDE_SCALE_UNIT
        val innerSize = (sizePx * scale).toInt().coerceAtLeast(1)
        val offset = sizePx - innerSize

        val front = render(token, TokenSide.FRONT, innerSize)
        val back  = render(token, TokenSide.BACK,  innerSize)

        val out = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(back, offset, offset, null)
            g.drawImage(front, 0, 0, null)
        } finally {
            g.dispose()
        }
        return out
    }

}
