package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import java.awt.Cursor
import java.awt.event.MouseEvent

interface Tool {
    fun cursor(): Cursor = Cursor.getDefaultCursor()
    fun onMousePressed(ctx: AppContext, e: MouseEvent) {}
    fun onMouseDragged(ctx: AppContext, e: MouseEvent) {}
    fun onMouseReleased(ctx: AppContext, e: MouseEvent) {}
}
