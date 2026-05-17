package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.util.UUID

class MoveTool : Tool {

    private var startX: Int = 0
    private var startY: Int = 0
    private var startTokenId: UUID? = null
    private var startTargetsX: List<MultiLayerPropertyCommand.Target> = emptyList()
    private var startTargetsY: List<MultiLayerPropertyCommand.Target> = emptyList()

    override fun cursor(): Cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        startX = e.x; startY = e.y; startTokenId = tokenId
        startTargetsX = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            val v = layer.props.offsetX.toDouble()
            MultiLayerPropertyCommand.Target(tokenId, id, oldValue = v, newValue = v)
        }
        startTargetsY = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            val v = layer.props.offsetY.toDouble()
            MultiLayerPropertyCommand.Target(tokenId, id, oldValue = v, newValue = v)
        }
    }

    override fun onMouseDragged(ctx: AppContext, e: MouseEvent) {
        startTokenId ?: return
        if (startTargetsX.isEmpty()) return
        val dx = ((e.x - startX) / ctx.viewState.zoom).toInt()
        val dy = ((e.y - startY) / ctx.viewState.zoom).toInt()
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(
            property = LayerProperty.OFFSET_X,
            targets = startTargetsX.map { it.copy(newValue = it.oldValue + dx) },
        ))
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(
            property = LayerProperty.OFFSET_Y,
            targets = startTargetsY.map { it.copy(newValue = it.oldValue + dy) },
        ))
    }

    override fun onMouseReleased(ctx: AppContext, e: MouseEvent) {
        startTokenId = null
    }
}
