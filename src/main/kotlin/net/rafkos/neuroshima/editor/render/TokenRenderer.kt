package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenKind
import java.awt.RenderingHints
import java.awt.image.BufferedImage

const val LOGICAL_CANVAS_W: Int = 1044
const val LOGICAL_CANVAS_H: Int = 902

// Flat-top hex, edge=440, center=(522,451). Bounding box without bleed:
private const val HEX_INNER_W = 880   // = 2 * 440
private const val HEX_INNER_H = 762   // ≈ 440 * √3
private const val HEX_INNER_X0 = 82   // = 522 - 440
private const val HEX_INNER_Y0 = 70   // ≈ 451 - 440*√3/2

// Modifier circle: diameter=440, center=(522,451)
private const val MOD_INNER_W = 440
private const val MOD_INNER_H = 440
private const val MOD_INNER_X0 = 302  // = 522 - 220
private const val MOD_INNER_Y0 = 231  // = 451 - 220

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
            // Crop to inner shape (no bleed): hex for TOKEN, circle-bbox for MODIFIER
            val (srcW, srcH, srcX0, srcY0) = if (token.kind == TokenKind.MODIFIER)
                listOf(MOD_INNER_W, MOD_INNER_H, MOD_INNER_X0, MOD_INNER_Y0)
            else
                listOf(HEX_INNER_W, HEX_INNER_H, HEX_INNER_X0, HEX_INNER_Y0)
            val fit = sizePx.toDouble() / srcW
            val scaledH = srcH * fit
            val offsetY = (sizePx - scaledH) / 2.0
            g.translate(-srcX0 * fit, offsetY - srcY0 * fit)
            g.scale(fit, fit)
            val centerX = LOGICAL_CANVAS_W / 2.0
            val centerY = LOGICAL_CANVAS_H / 2.0
            for (layer in token.layers) {
                val source = imageCache.get(layer.assetPath) ?: continue
                val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
                val processed = processedCache.get(key)
                    ?: LayerRenderer.applyPixelOps(source, layer.props).also {
                        processedCache.put(key, it)
                    }
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
