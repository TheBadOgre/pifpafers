package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import java.awt.event.MouseEvent

class OpacityTool : Tool {
    private var startX: Int = 0
    private var startTargets: List<MultiLayerPropertyCommand.Target> = emptyList()

    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        startX = e.x
        startTargets = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            val v = layer.props.opacity.toDouble()
            MultiLayerPropertyCommand.Target(tokenId, id, oldValue = v, newValue = v)
        }
    }

    override fun onMouseDragged(ctx: AppContext, e: MouseEvent) {
        if (startTargets.isEmpty()) return
        val delta = (e.x - startX) / 200.0
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(
            property = LayerProperty.OPACITY,
            targets = startTargets.map { it.copy(newValue = (it.oldValue + delta).coerceIn(0.0, 1.0)) },
        ))
    }

    override fun onMouseReleased(ctx: AppContext, e: MouseEvent) { startTargets = emptyList() }
}
