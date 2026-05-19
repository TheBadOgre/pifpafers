package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.ColorizeCommand
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.ui.dialogs.ColorizeDialog
import java.awt.Component
import java.awt.event.MouseEvent
import java.util.UUID

class ColorizeTool : Tool {
    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        run(ctx, e.component)
    }

    companion object {
        fun run(ctx: AppContext, dialogParent: Component) {
            val tokenId = ctx.viewState.activeTokenId ?: return
            val token = ctx.bag.findToken(tokenId) ?: return
            val selected = ctx.viewState.selectedLayers
            if (selected.isEmpty()) return

            val originalProps: Map<UUID, LayerProperties> = selected
                .mapNotNull { id -> token.findLayer(id)?.let { id to it.props } }
                .toMap()
            if (originalProps.isEmpty()) return

            // Init from existing colorize state if present, otherwise neutral (no effect).
            val first = originalProps.values.first()
            val initHue: Float
            val initSat: Float
            val initBright: Float
            if (first.colorize) {
                initHue = first.hue
                initSat = (first.saturation / 2f).coerceIn(0f, 1f)
                initBright = first.brightness
            } else {
                initHue = 0f; initSat = 0f; initBright = 1f
            }

            fun propsFor(orig: LayerProperties, h: Float, s: Float, b: Float) = orig.copy(
                colorize = true,
                hue = h,
                saturation = s * 2f,
                brightness = b,
            )

            fun applyPreview(h: Float, s: Float, b: Float) {
                for ((id, orig) in originalProps) ctx.bag.updateLayerProps(tokenId, id, propsFor(orig, h, s, b))
            }

            fun revertPreview() {
                for ((id, orig) in originalProps) ctx.bag.updateLayerProps(tokenId, id, orig)
            }

            ctx.viewState.setSuppressSelectionTint(true)
            val dlg = try {
                ColorizeDialog.show(dialogParent, ctx.locale, initHue, initSat, initBright) { h, s, b ->
                    applyPreview(h, s, b)
                }
            } finally {
                ctx.viewState.setSuppressSelectionTint(false)
            }

            if (!dlg.accepted) {
                revertPreview()
                return
            }

            val fh = dlg.hue; val fs = dlg.saturation; val fb = dlg.brightness
            revertPreview()
            val changes = originalProps.map { (id, orig) ->
                ColorizeCommand.LayerChange(id, orig, propsFor(orig, fh, fs, fb))
            }
            if (changes.isNotEmpty()) ctx.history.execute(ctx.bag, ColorizeCommand(tokenId, changes))
        }
    }
}
