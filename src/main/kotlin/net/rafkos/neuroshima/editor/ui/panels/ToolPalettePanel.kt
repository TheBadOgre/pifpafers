package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.ui.ToolId
import net.rafkos.neuroshima.editor.ui.icon.Icons
import net.rafkos.neuroshima.editor.ui.tools.ColorizeTool
import net.rafkos.neuroshima.editor.ui.tools.OpacityTool
import java.awt.Dimension
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JToggleButton
import javax.swing.SwingConstants

private const val BTN = 32

class ToolPalettePanel(private val ctx: AppContext) : JPanel() {
    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        val group = ButtonGroup()
        val toggleBtns = mutableMapOf<ToolId, JToggleButton>()

        for (id in ToolId.entries.filter { it != ToolId.COLORIZE }) {
            val btn = JToggleButton(id.icon()).apply {
                toolTipText = ctx.locale.t(id.tooltipKey())
                isSelected  = id == ctx.viewState.activeTool
                preferredSize = Dimension(BTN, BTN)
                minimumSize   = Dimension(BTN, BTN)
                maximumSize   = Dimension(BTN, BTN)
                margin        = Insets(2, 2, 2, 2)
                addActionListener { ctx.viewState.setActiveTool(id) }
            }
            group.add(btn)
            toggleBtns[id] = btn
            add(btn)
            add(Box.createVerticalStrut(2))
        }

        add(Box.createVerticalStrut(4))
        add(JSeparator(SwingConstants.HORIZONTAL).apply { maximumSize = Dimension(BTN + 8, 4) })
        add(Box.createVerticalStrut(4))

        val colorizeBtn = JButton(ToolId.COLORIZE.icon()).apply {
            toolTipText = ctx.locale.t(ToolId.COLORIZE.tooltipKey())
            preferredSize = Dimension(BTN, BTN)
            minimumSize   = Dimension(BTN, BTN)
            maximumSize   = Dimension(BTN, BTN)
            margin        = Insets(2, 2, 2, 2)
            addActionListener { ColorizeTool.run(ctx, this) }
        }
        add(colorizeBtn)
        add(Box.createVerticalStrut(2))

        val opacityBtn = JButton(Icons.toolOpacity).apply {
            toolTipText = ctx.locale.t("tool.opacity")
            preferredSize = Dimension(BTN, BTN)
            minimumSize   = Dimension(BTN, BTN)
            maximumSize   = Dimension(BTN, BTN)
            margin        = Insets(2, 2, 2, 2)
            addActionListener { OpacityTool.run(ctx, this) }
        }
        add(opacityBtn)

        fun updateState() {
            val hasToken     = ctx.viewState.activeTokenId != null
            val hasSelection = ctx.viewState.selectedLayers.isNotEmpty()
            toggleBtns[ToolId.SELECT]?.isEnabled  = hasToken
            toggleBtns[ToolId.MOVE]?.isEnabled    = hasSelection
            toggleBtns[ToolId.ROTATE]?.isEnabled  = hasSelection
            toggleBtns[ToolId.SCALE]?.isEnabled   = hasSelection
            colorizeBtn.isEnabled = hasSelection
            opacityBtn.isEnabled  = hasSelection
            toggleBtns.forEach { (id, btn) -> btn.isSelected = id == ctx.viewState.activeTool }
        }

        ctx.viewState.addListener { updateState() }
        updateState()
    }

    private fun ToolId.icon(): ImageIcon = when (this) {
        ToolId.SELECT   -> Icons.toolSelect
        ToolId.MOVE     -> Icons.toolMove
        ToolId.ROTATE   -> Icons.toolRotate
        ToolId.SCALE    -> Icons.toolScale
        ToolId.COLORIZE -> Icons.toolColorize
    }

    private fun ToolId.tooltipKey(): String = when (this) {
        ToolId.SELECT   -> "tool.select"
        ToolId.MOVE     -> "tool.move"
        ToolId.ROTATE   -> "tool.rotate"
        ToolId.SCALE    -> "tool.scale"
        ToolId.COLORIZE -> "tool.colorize"
    }
}
