package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.Token
import java.awt.RenderingHints
import java.awt.image.BufferedImage

const val LOGICAL_CANVAS_W: Int = 1044
const val LOGICAL_CANVAS_H: Int = 902

class TokenRenderer(
    private val imageCache: ImageCache,
    private val processedCache: ProcessedLayerCache,
) {
    fun render(token: Token, sizePx: Int): BufferedImage {
        val shape  = TokenShape.forKind(token.kind)
        val srcW   = shape.boundingW
        val srcH   = shape.boundingH
        val srcX0  = shape.boundingX0
        val srcY0  = shape.boundingY0

        val out = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g   = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            val fit     = sizePx.toDouble() / srcW
            val scaledH = srcH * fit
            val offsetY = (sizePx - scaledH) / 2.0
            g.translate(-srcX0 * fit, offsetY - srcY0 * fit)
            g.scale(fit, fit)
            g.clip = shape.clipShape()
            val centerX = LOGICAL_CANVAS_W / 2.0
            val centerY = LOGICAL_CANVAS_H / 2.0
            for (layer in token.layers) {
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
}
