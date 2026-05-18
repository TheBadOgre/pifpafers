package net.rafkos.neuroshima.editor.ui.canvas

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.model.ModelEvent
import net.rafkos.neuroshima.editor.render.AffineBuilder
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_H
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_W
import net.rafkos.neuroshima.editor.render.LayerRenderer
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.TokenShape
import net.rafkos.neuroshima.editor.render.overlay.OverlayPainter
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.UUID
import javax.swing.JPanel

class TokenCanvasPanel(private val ctx: AppContext) : JPanel() {

    var panX: Int = 0
    var panY: Int = 0
    private var panning: Boolean = false
    private var panOriginX: Int = 0
    private var panOriginY: Int = 0

    val mapper: CanvasMapper = CanvasMapper(
        widthSupplier  = { width },
        heightSupplier = { height },
        zoomSupplier   = { ctx.viewState.zoom },
        panXSupplier   = { panX },
        panYSupplier   = { panY },
    )

    private var compositeCache: BufferedImage? = null
    private var compositeForTokenId: UUID? = null

    init {
        preferredSize = Dimension(LOGICAL_CANVAS_W / 2, LOGICAL_CANVAS_H / 2)
        background = Color(60, 60, 60)
        ctx.addBagListener { event ->
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

    private fun ensureComposite(tokenId: UUID): BufferedImage {
        val cached = compositeCache
        if (cached != null && compositeForTokenId == tokenId) return cached
        val out = BufferedImage(LOGICAL_CANVAS_W, LOGICAL_CANVAS_H, BufferedImage.TYPE_INT_ARGB)
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
                        canvasCenterX = LOGICAL_CENTER_X,
                        canvasCenterY = LOGICAL_CENTER_Y,
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
        val g2 = (graphics.create() as Graphics2D)
        try {
            paintCanvas(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun paintCanvas(g2: Graphics2D) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val zoom = ctx.viewState.zoom
        g2.translate(width / 2.0 + panX, height / 2.0 + panY)
        g2.scale(zoom.toDouble(), zoom.toDouble())
        g2.translate(-LOGICAL_CENTER_X, -LOGICAL_CENTER_Y)

        val tokenId = ctx.viewState.activeTokenId
        val token   = if (tokenId != null) ctx.bag.findToken(tokenId) else null

        if (token == null) return  // dark grey background already painted by super

        val shape = TokenShape.forKind(token.kind)
        val clip  = if (ctx.viewState.showOverlay) shape.bleedShape() else shape.clipShape()
        g2.clip = clip

        g2.color = Color(220, 220, 220)
        g2.fillRect(0, 0, LOGICAL_CANVAS_W, LOGICAL_CANVAS_H)

        g2.drawImage(ensureComposite(tokenId!!), 0, 0, null)

        if (ctx.viewState.showOverlay) {
            OverlayPainter.forKind(token.kind).paint(g2)
        }

        drawSelectionHue(g2, tokenId)
    }

    private fun drawSelectionHue(g2: Graphics2D, tokenId: UUID) {
        val selected = ctx.viewState.selectedLayers
        if (selected.isEmpty()) return
        val token = ctx.bag.findToken(tokenId) ?: return
        val savedComposite = g2.composite
        for (layer in token.layers) {
            if (layer.id !in selected) continue
            val source = ctx.imageCache.get(layer.assetPath) ?: continue
            val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
            val processed = ctx.processedCache.get(key)
                ?: LayerRenderer.applyPixelOps(source, layer.props).also { ctx.processedCache.put(key, it) }
            val xform = AffineBuilder.build(layer.props, LOGICAL_CENTER_X, LOGICAL_CENTER_Y, processed.width, processed.height)
            val blue = tintBlue(processed)
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f)
            g2.drawImage(blue, xform, null)
        }
        g2.composite = savedComposite
    }

    private fun tintBlue(src: BufferedImage): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val argb = src.getRGB(x, y)
                val a = (argb ushr 24) and 0xff
                if (a > 0) out.setRGB(x, y, (a shl 24) or 0x0055ff)
            }
        }
        return out
    }
}
