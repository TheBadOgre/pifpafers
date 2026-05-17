package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.Color
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.JColorChooser

class ColorizeTool : Tool {
    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        if (selected.isEmpty()) return
        val picked = JColorChooser.showDialog(e.component, "Colorize", Color.WHITE) ?: return
        val hsb = Color.RGBtoHSB(picked.red, picked.green, picked.blue, FloatArray(3))
        apply(ctx, tokenId, selected, LayerProperty.HUE, hsb[0].toDouble()) { it.hue.toDouble() }
        apply(ctx, tokenId, selected, LayerProperty.SATURATION, hsb[1].toDouble()) { it.saturation.toDouble() }
        apply(ctx, tokenId, selected, LayerProperty.BRIGHTNESS, hsb[2].toDouble()) { it.brightness.toDouble() }
    }

    private fun apply(
        ctx: AppContext,
        tokenId: UUID,
        ids: Set<UUID>,
        property: LayerProperty,
        newValue: Double,
        oldOf: (LayerProperties) -> Double,
    ) {
        val token = ctx.bag.findToken(tokenId) ?: return
        val targets = ids.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            MultiLayerPropertyCommand.Target(tokenId, id, oldOf(layer.props))
        }
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(property, newValue, targets))
    }
}
