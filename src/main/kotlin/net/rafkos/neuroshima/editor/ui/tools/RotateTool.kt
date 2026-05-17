package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import java.awt.Cursor
import java.awt.event.MouseEvent

class RotateTool : Tool {
    private var startX: Int = 0
    private var startTargets: List<MultiLayerPropertyCommand.Target> = emptyList()

    override fun cursor(): Cursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)

    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        startX = e.x
        startTargets = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            MultiLayerPropertyCommand.Target(tokenId, id, layer.props.rotation.toDouble())
        }
    }

    override fun onMouseDragged(ctx: AppContext, e: MouseEvent) {
        if (startTargets.isEmpty()) return
        val degrees = (e.x - startX).toDouble()
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(
            property = LayerProperty.ROTATION,
            newValue = startTargets.first().oldValue + degrees,
            targets = startTargets,
        ))
    }

    override fun onMouseReleased(ctx: AppContext, e: MouseEvent) {
        startTargets = emptyList()
    }
}
