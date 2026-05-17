package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.ui.ToolId
import net.rafkos.neuroshima.editor.ui.ViewState
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.SwingConstants

class ToolPalettePanel(private val viewState: ViewState) : JPanel() {
    init {
        layout = GridLayout(0, 1, 0, 2)
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        val group = ButtonGroup()
        for (id in ToolId.entries) {
            val button = JToggleButton(id.shortLabel(), id == viewState.activeTool).apply {
                horizontalAlignment = SwingConstants.CENTER
                addActionListener { viewState.setActiveTool(id) }
            }
            group.add(button)
            add(button)
        }
        viewState.addListener {
            for (i in 0 until componentCount) {
                val btn = getComponent(i) as JToggleButton
                btn.isSelected = ToolId.entries[i] == viewState.activeTool
            }
        }
    }

    private fun ToolId.shortLabel(): String = when (this) {
        ToolId.SELECT -> "Sel"
        ToolId.MOVE -> "Mov"
        ToolId.ROTATE -> "Rot"
        ToolId.SCALE -> "Scl"
        ToolId.OPACITY -> "Opa"
        ToolId.COLORIZE -> "Col"
    }
}
