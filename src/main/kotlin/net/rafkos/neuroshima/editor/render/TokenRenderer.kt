package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenSide
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

const val LOGICAL_CANVAS_W: Int = 1044
const val LOGICAL_CANVAS_H: Int = 902

private val OUTLINE_COLOR: Color = Color(64, 64, 64)
private const val OUTLINE_ALPHA: Float = 0.8f
private const val DUAL_SIDE_SCALE: Double = 0.8
private const val ALPHA_EDGE_THRESHOLD: Int = 16

class TokenRenderer(
    private val imageCache: ImageCache,
    private val processedCache: ProcessedLayerCache,
) {
    fun render(token: Token, side: TokenSide, sizePx: Int): BufferedImage {
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
            for (layer in token.layers(side)) {
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
        val innerSize = (sizePx * DUAL_SIDE_SCALE).toInt().coerceAtLeast(1)
        val offset = sizePx - innerSize

        val front = render(token, TokenSide.FRONT, innerSize)
        val back  = render(token, TokenSide.BACK,  innerSize)

        val out = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(back, offset, offset, null)
            val outline = buildAlphaEdgeOutline(front)
            g.drawImage(outline, 0, 0, null)
            g.drawImage(front, 0, 0, null)
        } finally {
            g.dispose()
        }
        return out
    }

    private fun buildAlphaEdgeOutline(src: BufferedImage): BufferedImage {
        val w = src.width
        val h = src.height
        val edgeAlpha = (OUTLINE_ALPHA * 255).toInt().coerceIn(0, 255)
        val outARGB = (edgeAlpha shl 24) or
            (OUTLINE_COLOR.red shl 16) or (OUTLINE_COLOR.green shl 8) or OUTLINE_COLOR.blue
        val alphaIn = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            alphaIn[y * w + x] = (src.getRGB(x, y) ushr 24) and 0xff
        }
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) for (x in 0 until w) {
            val a = alphaIn[y * w + x]
            if (a >= ALPHA_EDGE_THRESHOLD) continue
            var hasInsideNeighbor = false
            outer@ for (dy in -1..1) {
                val ny = y + dy
                if (ny !in 0 until h) continue
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    if (nx !in 0 until w) continue
                    if (alphaIn[ny * w + nx] >= ALPHA_EDGE_THRESHOLD) {
                        hasInsideNeighbor = true
                        break@outer
                    }
                }
            }
            if (hasInsideNeighbor) out.setRGB(x, y, outARGB)
        }
        @Suppress("UNUSED_VARIABLE") val _composite = AlphaComposite.SrcOver
        return out
    }
}
