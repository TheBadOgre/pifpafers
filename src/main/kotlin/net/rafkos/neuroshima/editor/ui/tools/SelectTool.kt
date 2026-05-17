package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import java.awt.event.MouseEvent

class SelectTool : Tool {
    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        if (!e.isControlDown && e.button == MouseEvent.BUTTON1) ctx.viewState.clearSelection()
    }
}
