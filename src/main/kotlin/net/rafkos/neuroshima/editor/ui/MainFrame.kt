package net.rafkos.neuroshima.editor.ui

import net.rafkos.neuroshima.editor.app.AppContext
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.WindowConstants

class MainFrame(val ctx: AppContext) : JFrame() {

    val toolPalette: JPanel = JPanel().apply { preferredSize = Dimension(48, 0) }
    val tokensPanel: JPanel = JPanel().apply { preferredSize = Dimension(220, 0) }
    val layersPanel: JPanel = JPanel().apply { preferredSize = Dimension(220, 200) }
    val propertiesPanel: JPanel = JPanel().apply { preferredSize = Dimension(220, 200) }
    val canvasPanel: JPanel = JPanel()
    val assetsPanel: JPanel = JPanel().apply { preferredSize = Dimension(0, 220) }
    val statusBar: JPanel = JPanel().apply { preferredSize = Dimension(0, 24) }

    init {
        title = ctx.locale.t("app.title")
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        layout = BorderLayout()

        val rightStack = JSplitPane(JSplitPane.VERTICAL_SPLIT, layersPanel, propertiesPanel).apply {
            resizeWeight = 0.5
            isContinuousLayout = true
        }
        val centerWithRight = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasPanel, rightStack).apply {
            resizeWeight = 0.75
            isContinuousLayout = true
        }
        val mainWithLeft = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tokensPanel, centerWithRight).apply {
            resizeWeight = 0.15
            isContinuousLayout = true
        }
        val top = JPanel(BorderLayout()).apply {
            add(toolPalette, BorderLayout.WEST)
            add(mainWithLeft, BorderLayout.CENTER)
        }
        val full = JSplitPane(JSplitPane.VERTICAL_SPLIT, top, assetsPanel).apply {
            resizeWeight = 0.7
            isContinuousLayout = true
        }

        add(full, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)
        preferredSize = Dimension(1280, 800)
        pack()
        setLocationRelativeTo(null)
    }
}
