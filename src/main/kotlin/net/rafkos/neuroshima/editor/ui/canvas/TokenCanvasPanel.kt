package net.rafkos.neuroshima.editor.ui.canvas

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.render.AffineBuilder
import net.rafkos.neuroshima.editor.render.LayerRenderer
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JPanel

const val LOGICAL_SIZE_PX: Int = 490

class TokenCanvasPanel(private val ctx: AppContext) : JPanel() {

    private val overlay: BufferedImage? = loadOverlay()
    var panX: Int = 0
    var panY: Int = 0

    init {
        preferredSize = Dimension(LOGICAL_SIZE_PX, LOGICAL_SIZE_PX)
        background = Color(60, 60, 60)
        ctx.bag.addListener { repaint() }
        ctx.viewState.addListener { repaint() }

        addMouseWheelListener { e ->
            val factor = if (e.wheelRotation < 0) 1.1f else 1f / 1.1f
            ctx.viewState.setZoom(ctx.viewState.zoom * factor)
        }

        val panOrigin = intArrayOf(0, 0)
        val panOffset = intArrayOf(0, 0)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON2) {
                    panOrigin[0] = e.x; panOrigin[1] = e.y
                }
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (java.awt.event.MouseEvent.getModifiersExText(e.modifiersEx).contains("Button2")) {
                    panOffset[0] += e.x - panOrigin[0]
                    panOffset[1] += e.y - panOrigin[1]
                    panOrigin[0] = e.x; panOrigin[1] = e.y
                    panX = panOffset[0]
                    panY = panOffset[1]
                    repaint()
                }
            }
        })
    }

    private fun loadOverlay(): BufferedImage? {
        val resource = TokenCanvasPanel::class.java.classLoader.getResource("overlay/HEX_template_lines.png")
            ?: return null
        return resource.openStream().use { ImageIO.read(it) }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val zoom = ctx.viewState.zoom
        val centerX = width / 2.0
        val centerY = height / 2.0
        g2.translate(centerX + panX, centerY + panY)
        g2.scale(zoom.toDouble(), zoom.toDouble())
        g2.translate(-LOGICAL_SIZE_PX / 2.0, -LOGICAL_SIZE_PX / 2.0)
        g2.color = Color(220, 220, 220)
        g2.fillRect(0, 0, LOGICAL_SIZE_PX, LOGICAL_SIZE_PX)
        val tokenId = ctx.viewState.activeTokenId
        val token = tokenId?.let { ctx.bag.findToken(it) }
        if (token != null) {
            for (layer in token.layers) {
                val source = ctx.imageCache.get(layer.assetPath) ?: continue
                val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
                val processed = ctx.processedCache.get(key)
                    ?: LayerRenderer.applyPixelOps(source, layer.props).also { ctx.processedCache.put(key, it) }
                val xform = AffineBuilder.build(
                    props = layer.props,
                    canvasCenterX = LOGICAL_SIZE_PX / 2.0,
                    canvasCenterY = LOGICAL_SIZE_PX / 2.0,
                    imageWidth = processed.width,
                    imageHeight = processed.height,
                )
                g2.drawImage(processed, xform, null)
            }
        }
        if (ctx.viewState.showOverlay && overlay != null) {
            val ox = (LOGICAL_SIZE_PX - overlay.width) / 2
            val oy = (LOGICAL_SIZE_PX - overlay.height) / 2
            g2.drawImage(overlay, ox, oy, null)
        }
    }
}
