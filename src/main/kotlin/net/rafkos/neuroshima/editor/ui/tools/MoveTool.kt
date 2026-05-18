package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.MoveLayerCommand
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.util.UUID

class MoveTool : Tool {

    private var startX: Int = 0
    private var startY: Int = 0
    private var startTokenId: UUID? = null
    private var startTargets: List<MoveLayerCommand.Target> = emptyList()

    override fun cursor(): Cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        startX = e.x; startY = e.y; startTokenId = tokenId
        startTargets = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            MoveLayerCommand.Target(tokenId, id, oldX = layer.props.offsetX, oldY = layer.props.offsetY,
                newX = layer.props.offsetX, newY = layer.props.offsetY)
        }
    }

    override fun onMouseDragged(ctx: AppContext, e: MouseEvent) {
        startTokenId ?: return
        if (startTargets.isEmpty()) return
        val dx = ((e.x - startX) / ctx.viewState.zoom).toInt()
        val dy = ((e.y - startY) / ctx.viewState.zoom).toInt()
        ctx.history.execute(ctx.bag, MoveLayerCommand(
            targets = startTargets.map { it.copy(newX = it.oldX + dx, newY = it.oldY + dy) }
        ))
    }

    override fun onMouseReleased(ctx: AppContext, e: MouseEvent) {
        startTokenId = null
    }
}
