package net.rafkos.neuroshima.editor.ui.canvas

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.app.AppDirs
import net.rafkos.neuroshima.editor.model.ModelEvent
import net.rafkos.neuroshima.editor.render.AffineBuilder
import net.rafkos.neuroshima.editor.render.LOGICAL_TOKEN_SIZE_PX
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
import java.util.UUID
import javax.imageio.ImageIO
import javax.swing.JPanel

const val LOGICAL_SIZE_PX: Int = LOGICAL_TOKEN_SIZE_PX

class TokenCanvasPanel(private val ctx: AppContext) : JPanel() {

    private val overlay: BufferedImage? = loadOverlay()
    var panX: Int = 0
    var panY: Int = 0
    private var panning: Boolean = false
    private var panOriginX: Int = 0
    private var panOriginY: Int = 0

    val mapper: CanvasMapper = CanvasMapper(
        widthSupplier = { width },
        heightSupplier = { height },
        zoomSupplier = { ctx.viewState.zoom },
        panXSupplier = { panX },
        panYSupplier = { panY },
    )

    private var compositeCache: BufferedImage? = null
    private var compositeForTokenId: UUID? = null

    init {
        preferredSize = Dimension(LOGICAL_SIZE_PX, LOGICAL_SIZE_PX)
        background = Color(60, 60, 60)
        ctx.bag.addListener { event ->
            when (event) {
                is ModelEvent.LayerAdded,
                is ModelEvent.LayerRemoved,
                is ModelEvent.LayerReordered,
                is ModelEvent.LayerPropsChanged -> compositeCache = null
                else -> {}
            }
            repaint()
        }
        ctx.viewState.addListener {
            if (ctx.viewState.activeTokenId != compositeForTokenId) compositeCache = null
            repaint()
        }

        addMouseWheelListener { e ->
            val factor = if (e.wheelRotation < 0) 1.1f else 1f / 1.1f
            ctx.viewState.setZoom(ctx.viewState.zoom * factor)
        }
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON2) {
                    panning = true; panOriginX = e.x; panOriginY = e.y
                }
            }
            override fun mouseReleased(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON2) panning = false
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (!panning) return
                panX += e.x - panOriginX
                panY += e.y - panOriginY
                panOriginX = e.x; panOriginY = e.y
                repaint()
            }
        })
    }

    private fun loadOverlay(): BufferedImage? {
        val file = AppDirs.overlayRoot.resolve("overlay.png")
        if (!file.isFile) return null
        return file.inputStream().use { ImageIO.read(it) }
    }

    private fun ensureComposite(tokenId: UUID): BufferedImage {
        val cached = compositeCache
        if (cached != null && compositeForTokenId == tokenId) return cached
        val out = BufferedImage(LOGICAL_SIZE_PX, LOGICAL_SIZE_PX, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            val token = ctx.bag.findToken(tokenId)
            if (token != null) {
                for (layer in token.layers) {
                    val source = ctx.imageCache.get(layer.assetPath) ?: continue
                    val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
                    val processed = ctx.processedCache.get(key)
                        ?: LayerRenderer.applyPixelOps(source, layer.props).also { ctx.processedCache.put(key, it) }
                    val xform = AffineBuilder.build(
                        props = layer.props,
                        canvasCenterX = LOGICAL_CENTER,
                        canvasCenterY = LOGICAL_CENTER,
                        imageWidth = processed.width,
                        imageHeight = processed.height,
                    )
                    g.drawImage(processed, xform, null)
                }
            }
        } finally {
            g.dispose()
        }
        compositeCache = out
        compositeForTokenId = tokenId
        return out
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g2 = graphics as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val zoom = ctx.viewState.zoom
        val centerX = width / 2.0
        val centerY = height / 2.0
        g2.translate(centerX + panX, centerY + panY)
        g2.scale(zoom.toDouble(), zoom.toDouble())
        g2.translate(-LOGICAL_CENTER, -LOGICAL_CENTER)
        g2.color = Color(220, 220, 220)
        g2.fillRect(0, 0, LOGICAL_SIZE_PX, LOGICAL_SIZE_PX)
        val tokenId = ctx.viewState.activeTokenId
        if (tokenId != null) {
            g2.drawImage(ensureComposite(tokenId), 0, 0, null)
        }
        if (ctx.viewState.showOverlay && overlay != null) {
            val ox = (LOGICAL_SIZE_PX - overlay.width) / 2
            val oy = (LOGICAL_SIZE_PX - overlay.height) / 2
            g2.drawImage(overlay, ox, oy, null)
        }
    }
}
