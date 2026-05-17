package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.ui.ToolId
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

class ToolController(private val ctx: AppContext, private val attachTo: Component) {

    private val tools: Map<ToolId, Tool> = mapOf(
        ToolId.SELECT   to SelectTool(),
        ToolId.MOVE     to MoveTool(),
        ToolId.ROTATE   to RotateTool(),
        ToolId.SCALE    to ScaleTool(),
        ToolId.OPACITY  to OpacityTool(),
        ToolId.COLORIZE to ColorizeTool(),
    )

    init {
        attachTo.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) current().onMousePressed(ctx, e)
            }
            override fun mouseReleased(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) current().onMouseReleased(ctx, e)
            }
        })
        attachTo.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) { current().onMouseDragged(ctx, e) }
        })
        ctx.viewState.addListener { attachTo.cursor = current().cursor() }
    }

    private fun current(): Tool = tools.getValue(ctx.viewState.activeTool)
}
