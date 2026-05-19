package net.rafkos.neuroshima.editor.ui.canvas

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.model.AssetPath
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
import java.awt.Transparency
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.UUID
import javax.swing.JPanel
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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

    private var compositeBuf: BufferedImage? = null
    private var compositeValid: Boolean = false
    private var compositeForTokenId: UUID? = null

    private var selectionBuf: BufferedImage? = null
    private var selectionValid: Boolean = false

    private val tintCache: MutableMap<AssetPath, BufferedImage> = HashMap()

    private var lastActiveToken: UUID? = null
    private var lastSelection: Set<UUID> = emptySet()

    init {
        preferredSize = Dimension(LOGICAL_CANVAS_W / 2, LOGICAL_CANVAS_H / 2)
        background = Color(60, 60, 60)
        ctx.addBagListener { event ->
            val activeId = ctx.viewState.activeTokenId
            when (event) {
                is ModelEvent.LayerAdded ->
                    if (event.tokenId == activeId) { compositeValid = false; selectionValid = false }
                is ModelEvent.LayerRemoved ->
                    if (event.tokenId == activeId) { compositeValid = false; selectionValid = false }
                is ModelEvent.LayerReordered ->
                    if (event.tokenId == activeId) { compositeValid = false; selectionValid = false }
                is ModelEvent.LayerPropsChanged ->
                    if (event.tokenId == activeId) {
                        compositeValid = false
                        if (event.layerId in ctx.viewState.selectedLayers) selectionValid = false
                    }
                is ModelEvent.TokenRemoved ->
                    if (event.tokenId == activeId) { compositeValid = false; selectionValid = false }
                else -> {}
            }
            repaint()
        }
        ctx.viewState.addListener {
            val newActive = ctx.viewState.activeTokenId
            val newSelection = ctx.viewState.selectedLayers
            if (newActive != lastActiveToken) {
                compositeValid = false
                selectionValid = false
                compositeForTokenId = null
                tintCache.clear()
                lastActiveToken = newActive
            }
            if (newSelection != lastSelection) {
                selectionValid = false
                lastSelection = newSelection.toSet()
            }
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

    private fun newManagedArgb(w: Int, h: Int): BufferedImage {
        val gc = graphicsConfiguration
        return gc?.createCompatibleImage(w, h, Transparency.TRANSLUCENT)
            ?: BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    }

    private fun ensureComposite(tokenId: UUID): BufferedImage {
        val current = compositeBuf
        if (current != null && compositeValid && compositeForTokenId == tokenId) return current
        val buf = current ?: newManagedArgb(LOGICAL_CANVAS_W, LOGICAL_CANVAS_H).also { compositeBuf = it }
        val g = buf.createGraphics()
        try {
            g.composite = AlphaComposite.Clear
            g.fillRect(0, 0, buf.width, buf.height)
            g.composite = AlphaComposite.SrcOver
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
        compositeForTokenId = tokenId
        compositeValid = true
        return buf
    }

    private fun ensureSelection(tokenId: UUID): BufferedImage? {
        if (ctx.viewState.suppressSelectionTint) return null
        val selected = ctx.viewState.selectedLayers
        if (selected.isEmpty()) { selectionValid = true; return null }
        val existing = selectionBuf
        if (existing != null && selectionValid) return existing
        val token = ctx.bag.findToken(tokenId) ?: return null
        val buf = existing ?: newManagedArgb(LOGICAL_CANVAS_W, LOGICAL_CANVAS_H).also { selectionBuf = it }
        val g = buf.createGraphics()
        try {
            g.composite = AlphaComposite.Clear
            g.fillRect(0, 0, buf.width, buf.height)
            g.composite = AlphaComposite.SrcOver
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            for (layer in token.layers) {
                if (layer.id !in selected) continue
                val source = ctx.imageCache.get(layer.assetPath) ?: continue
                val tinted = tintBlueOf(layer.assetPath, source)
                val xform = AffineBuilder.build(
                    props = layer.props,
                    canvasCenterX = LOGICAL_CENTER_X,
                    canvasCenterY = LOGICAL_CENTER_Y,
                    imageWidth = source.width,
                    imageHeight = source.height,
                )
                g.drawImage(tinted, xform, null)
            }
        } finally {
            g.dispose()
        }
        selectionValid = true
        return buf
    }

    private fun tintBlueOf(assetPath: AssetPath, src: BufferedImage): BufferedImage {
        tintCache[assetPath]?.takeIf { it.width == src.width && it.height == src.height }?.let { return it }
        val out = newManagedArgb(src.width, src.height)
        val g = out.createGraphics()
        try {
            g.composite = AlphaComposite.Src
            g.color = Color(0, 0x55, 0xff)
            g.fillRect(0, 0, out.width, out.height)
            g.composite = AlphaComposite.DstIn
            g.drawImage(src, 0, 0, null)
        } finally {
            g.dispose()
        }
        tintCache[assetPath] = out
        return out
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        if (width <= 0 || height <= 0) return
        val g2 = (graphics.create() as Graphics2D)
        try {
            g2.clipRect(0, 0, width, height)
            paintCanvas(g2)
        } finally {
            g2.dispose()
        }
    }

    private fun paintCanvas(g2: Graphics2D) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val zoom = ctx.viewState.zoom
        g2.translate(width / 2.0 + panX, height / 2.0 + panY)
        g2.scale(zoom.toDouble(), zoom.toDouble())
        g2.translate(-LOGICAL_CENTER_X, -LOGICAL_CENTER_Y)

        val tokenId = ctx.viewState.activeTokenId
        val token   = if (tokenId != null) ctx.bag.findToken(tokenId) else null

        if (token == null) return

        val shape = TokenShape.forKind(token.kind)
        val clip  = if (ctx.viewState.showOverlay) shape.bleedShape() else shape.clipShape()
        g2.clip(clip)

        g2.color = Color(220, 220, 220)
        g2.fillRect(0, 0, LOGICAL_CANVAS_W, LOGICAL_CANVAS_H)

        blitLogical(g2, ensureComposite(tokenId!!))

        if (ctx.viewState.showOverlay) {
            OverlayPainter.forKind(token.kind).paint(g2)
        }

        val sel = ensureSelection(tokenId)
        if (sel != null) {
            val saved = g2.composite
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f)
            blitLogical(g2, sel)
            g2.composite = saved
        }
    }

    private fun blitLogical(g2: Graphics2D, img: BufferedImage) {
        val zoom = ctx.viewState.zoom.toDouble()
        if (zoom == 0.0) return
        val lx0 = (-width / 2.0 - panX) / zoom + LOGICAL_CENTER_X
        val ly0 = (-height / 2.0 - panY) / zoom + LOGICAL_CENTER_Y
        val lx1 = (width / 2.0 - panX) / zoom + LOGICAL_CENTER_X
        val ly1 = (height / 2.0 - panY) / zoom + LOGICAL_CENTER_Y
        val pad = 1
        val sx1 = (floor(min(lx0, lx1)).toInt() - pad).coerceIn(0, img.width)
        val sy1 = (floor(min(ly0, ly1)).toInt() - pad).coerceIn(0, img.height)
        val sx2 = (ceil(max(lx0, lx1)).toInt() + pad).coerceIn(0, img.width)
        val sy2 = (ceil(max(ly0, ly1)).toInt() + pad).coerceIn(0, img.height)
        if (sx2 <= sx1 || sy2 <= sy1) return
        g2.drawImage(img, sx1, sy1, sx2, sy2, sx1, sy1, sx2, sy2, null)
    }
}
