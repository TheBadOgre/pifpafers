package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.render.AffineBuilder
import net.rafkos.neuroshima.editor.ui.canvas.LOGICAL_CENTER_X
import net.rafkos.neuroshima.editor.ui.canvas.LOGICAL_CENTER_Y
import java.awt.event.MouseEvent
import java.awt.geom.Point2D

private const val ALPHA_HIT_THRESHOLD: Int = 8

class SelectTool : Tool {
    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) return
        val mapper = ctx.canvasMapper ?: return
        val token = ctx.viewState.activeTokenId?.let { ctx.bag.findToken(it) } ?: return
        val side = ctx.viewState.activeSide
        val logical = mapper.screenToLogical(e.point)
        val hitId = token.layers(side).asReversed().firstOrNull { layer ->
            val img = ctx.imageCache.get(layer.assetPath) ?: return@firstOrNull false
            val xform = AffineBuilder.build(layer.props, LOGICAL_CENTER_X, LOGICAL_CENTER_Y, img.width, img.height)
            val inv = runCatching { xform.createInverse() }.getOrNull() ?: return@firstOrNull false
            val src = Point2D.Double()
            inv.transform(logical, src)
            val sx = src.x.toInt(); val sy = src.y.toInt()
            if (sx !in 0 until img.width || sy !in 0 until img.height) return@firstOrNull false
            val alpha = (img.getRGB(sx, sy) ushr 24) and 0xff
            alpha > ALPHA_HIT_THRESHOLD
        }?.id
        if (e.isControlDown) {
            if (hitId != null) {
                if (hitId in ctx.viewState.selectedLayers) ctx.viewState.deselectLayer(hitId)
                else ctx.viewState.selectLayer(hitId)
            }
        } else {
            if (hitId == null) ctx.viewState.clearSelection()
            else ctx.viewState.replaceSelection(listOf(hitId))
        }
    }
}
