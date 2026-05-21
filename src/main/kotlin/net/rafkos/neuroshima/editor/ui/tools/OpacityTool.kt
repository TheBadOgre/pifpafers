package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.ui.dialogs.OpacityDialog
import java.awt.Component
import java.util.UUID

object OpacityTool {
    fun run(ctx: AppContext, dialogParent: Component) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val side = ctx.viewState.activeSide
        val selected = ctx.viewState.selectedLayers
        if (selected.isEmpty()) return

        val originalProps: Map<UUID, LayerProperties> = selected
            .mapNotNull { id -> token.findLayer(side, id)?.let { id to it.props } }
            .toMap()
        if (originalProps.isEmpty()) return

        val initOpacity = originalProps.values.first().opacity

        fun applyPreview(o: Float) {
            for ((id, orig) in originalProps)
                ctx.bag.updateLayerProps(tokenId, side, id, orig.copy(opacity = o))
        }

        fun revertPreview() {
            for ((id, orig) in originalProps)
                ctx.bag.updateLayerProps(tokenId, side, id, orig)
        }

        val dlg = OpacityDialog.show(dialogParent, ctx.locale, initOpacity) { o -> applyPreview(o) }

        if (!dlg.accepted) {
            revertPreview()
            return
        }

        val fo = dlg.opacityValue
        revertPreview()
        val targets = originalProps.map { (id, orig) ->
            MultiLayerPropertyCommand.Target(tokenId, side, id,
                oldValue = orig.opacity.toDouble(), newValue = fo.toDouble())
        }
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(LayerProperty.OPACITY, targets))
    }
}
