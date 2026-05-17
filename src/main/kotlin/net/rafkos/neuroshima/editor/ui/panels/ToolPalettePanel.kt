package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.i18n.LocaleService
import net.rafkos.neuroshima.editor.ui.ToolId
import net.rafkos.neuroshima.editor.ui.ViewState
import net.rafkos.neuroshima.editor.ui.icon.Icons
import java.awt.Dimension
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.ImageIcon
import javax.swing.JPanel
import javax.swing.JToggleButton

private const val BTN = 32

class ToolPalettePanel(private val viewState: ViewState, private val locale: LocaleService) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        val loc   = locale
        val group = ButtonGroup()
        val buttons = mutableListOf<JToggleButton>()
        for (id in ToolId.entries) {
            val btn = JToggleButton(id.icon()).apply {
                toolTipText   = loc.t(id.tooltipKey())
                isSelected    = id == viewState.activeTool
                preferredSize = Dimension(BTN, BTN)
                minimumSize   = Dimension(BTN, BTN)
                maximumSize   = Dimension(BTN, BTN)
                margin        = Insets(2, 2, 2, 2)
                addActionListener { viewState.setActiveTool(id) }
            }
            group.add(btn)
            buttons.add(btn)
            add(btn)
            add(Box.createVerticalStrut(2))
        }
        viewState.addListener {
            ToolId.entries.forEachIndexed { i, id -> buttons[i].isSelected = id == viewState.activeTool }
        }
    }

    private fun ToolId.icon(): ImageIcon = when (this) {
        ToolId.SELECT   -> Icons.toolSelect
        ToolId.MOVE     -> Icons.toolMove
        ToolId.ROTATE   -> Icons.toolRotate
        ToolId.SCALE    -> Icons.toolScale
        ToolId.OPACITY  -> Icons.toolOpacity
        ToolId.COLORIZE -> Icons.toolColorize
    }

    private fun ToolId.tooltipKey(): String = when (this) {
        ToolId.SELECT   -> "tool.select"
        ToolId.MOVE     -> "tool.move"
        ToolId.ROTATE   -> "tool.rotate"
        ToolId.SCALE    -> "tool.scale"
        ToolId.OPACITY  -> "tool.opacity"
        ToolId.COLORIZE -> "tool.colorize"
    }
}
