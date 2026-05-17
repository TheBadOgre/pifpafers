package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.ColorizeCommand
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import javax.swing.JColorChooser
import javax.swing.JTabbedPane

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

            val chooser = JColorChooser(Color.WHITE)
            val hsvIdx = chooser.chooserPanels.indexOfFirst { panel ->
                "HSV" in panel.displayName.uppercase() || "HSB" in panel.displayName.uppercase()
            }
            if (hsvIdx >= 0) findTabbedPane(chooser)?.selectedIndex = hsvIdx

            var picked: Color? = null
            val dialog = JColorChooser.createDialog(
                dialogParent, ctx.locale.t("tool.colorize"), true, chooser,
                { picked = chooser.color }, null,
            )
            dialog.isVisible = true
            val color = picked ?: return

            val hsb = Color.RGBtoHSB(color.red, color.green, color.blue, FloatArray(3))
            val changes = selected.mapNotNull { id ->
                val layer = token.findLayer(id) ?: return@mapNotNull null
                val newProps = layer.props.copy(colorize = true, hue = hsb[0], saturation = 2f, brightness = 1f)
                ColorizeCommand.LayerChange(id, layer.props, newProps)
            }
            if (changes.isNotEmpty()) ctx.history.execute(ctx.bag, ColorizeCommand(tokenId, changes))
        }

        private fun findTabbedPane(c: Container): JTabbedPane? {
            for (child in c.components) {
                if (child is JTabbedPane) return child
                if (child is Container) findTabbedPane(child)?.let { return it }
            }
            return null
        }
    }
}
