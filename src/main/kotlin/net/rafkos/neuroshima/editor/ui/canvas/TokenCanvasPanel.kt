package net.rafkos.neuroshima.editor.ui.canvas

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.model.ModelEvent
import net.rafkos.neuroshima.editor.render.AffineBuilder
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_H
import net.rafkos.neuroshima.editor.render.LOGICAL_CANVAS_W
import net.rafkos.neuroshima.editor.render.LayerRenderer
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.TokenShape
import net.rafkos.neuroshima.editor.render.UnitTokenShape
import net.rafkos.neuroshima.editor.render.overlay.OverlayPainter
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Line2D
import java.awt.geom.Point2D
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
        val zoom = ctx.viewState.zoom
        g2.translate(width / 2.0 + panX, height / 2.0 + panY)
        g2.scale(zoom.toDouble(), zoom.toDouble())
        g2.translate(-LOGICAL_CENTER_X, -LOGICAL_CENTER_Y)

        val tokenId = ctx.viewState.activeTokenId
        val token   = if (tokenId != null) ctx.bag.findToken(tokenId) else null
        val shape   = if (token != null) TokenShape.forKind(token.kind) else UnitTokenShape
        val clip    = if (ctx.viewState.showOverlay) shape.bleedShape() else shape.clipShape()
        g2.clip = clip

        g2.color = Color(220, 220, 220)
        g2.fillRect(0, 0, LOGICAL_CANVAS_W, LOGICAL_CANVAS_H)

        if (tokenId != null) {
            g2.drawImage(ensureComposite(tokenId), 0, 0, null)
        }

        if (ctx.viewState.showOverlay && token != null) {
            OverlayPainter.forKind(token.kind).paint(g2)
        }

        if (tokenId != null) {
            drawSelectionMarkers(g2, tokenId)
        }
    }

    private fun drawSelectionMarkers(g2: Graphics2D, tokenId: UUID) {
        val selected = ctx.viewState.selectedLayers
        if (selected.isEmpty()) return
        val token     = ctx.bag.findToken(tokenId) ?: return
        val composite = compositeCache ?: return
        g2.stroke = BasicStroke(1.5f)
        for (layer in token.layers) {
            if (layer.id !in selected) continue
            val img   = ctx.imageCache.get(layer.assetPath) ?: continue
            val xform = AffineBuilder.build(layer.props, LOGICAL_CENTER_X, LOGICAL_CENTER_Y, img.width, img.height)
            val corners = arrayOf(
                Point2D.Double(0.0, 0.0),
                Point2D.Double(img.width.toDouble(), 0.0),
                Point2D.Double(img.width.toDouble(), img.height.toDouble()),
                Point2D.Double(0.0, img.height.toDouble()),
            )
            val projected = corners.map { xform.transform(it, null) as Point2D.Double }
            for (i in 0 until 4) {
                drawDashedEdge(g2, projected[i], projected[(i + 1) % 4], composite)
            }
        }
    }

    private fun drawDashedEdge(g2: Graphics2D, a: Point2D.Double, b: Point2D.Double, src: BufferedImage) {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len = kotlin.math.hypot(dx, dy)
        if (len < 1.0) return
        val steps = kotlin.math.max(2, (len / 8.0).toInt())
        val nx = dx / steps; val ny = dy / steps
        var t = 0
        while (t < steps) {
            if (t % 2 == 0) {
                val x0 = a.x + nx * t; val y0 = a.y + ny * t
                val x1 = a.x + nx * (t + 1); val y1 = a.y + ny * (t + 1)
                val mx = ((x0 + x1) / 2).toInt().coerceIn(0, src.width - 1)
                val my = ((y0 + y1) / 2).toInt().coerceIn(0, src.height - 1)
                val argb = src.getRGB(mx, my)
                g2.color = Color(255 - ((argb ushr 16) and 0xff),
                                 255 - ((argb ushr  8) and 0xff),
                                 255 - (argb and 0xff))
                g2.draw(Line2D.Double(x0, y0, x1, y1))
            }
            t++
        }
    }
}
