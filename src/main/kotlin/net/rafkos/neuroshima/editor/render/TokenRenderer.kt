package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.Token
import java.awt.RenderingHints
import java.awt.image.BufferedImage

const val LOGICAL_TOKEN_SIZE_PX: Int = 490

class TokenRenderer(
    private val imageCache: ImageCache,
    private val processedCache: ProcessedLayerCache,
) {
    fun render(token: Token, sizePx: Int): BufferedImage {
        val out = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            val fit = sizePx.toDouble() / LOGICAL_TOKEN_SIZE_PX
            g.scale(fit, fit)
            val center = LOGICAL_TOKEN_SIZE_PX / 2.0
            for (layer in token.layers) {
                val source = imageCache.get(layer.assetPath) ?: continue
                val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
                val processed = processedCache.get(key)
                    ?: LayerRenderer.applyPixelOps(source, layer.props).also {
                        processedCache.put(key, it)
                    }
                val xform = AffineBuilder.build(
                    props = layer.props,
                    canvasCenterX = center,
                    canvasCenterY = center,
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
