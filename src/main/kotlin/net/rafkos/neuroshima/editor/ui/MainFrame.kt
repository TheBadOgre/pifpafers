package net.rafkos.neuroshima.editor.ui

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.SetSameSidesCommand
import net.rafkos.neuroshima.editor.model.ModelEvent
import net.rafkos.neuroshima.editor.model.TokenSide
import net.rafkos.neuroshima.editor.ui.canvas.TokenCanvasPanel
import net.rafkos.neuroshima.editor.ui.dialogs.SaveBeforeCloseDialog
import net.rafkos.neuroshima.editor.ui.panels.AssetsLibraryPanel
import net.rafkos.neuroshima.editor.ui.panels.LayerPropertiesPanel
import net.rafkos.neuroshima.editor.ui.panels.LayersPanel
import net.rafkos.neuroshima.editor.ui.panels.ToolPalettePanel
import net.rafkos.neuroshima.editor.ui.panels.TokensCollectionPanel
import net.rafkos.neuroshima.editor.ui.icon.Icons
import net.rafkos.neuroshima.editor.ui.tools.ToolController
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.AbstractAction
import javax.swing.JCheckBox
import javax.swing.JComponent

import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.KeyStroke
import kotlin.system.exitProcess

class MainFrame(val ctx: AppContext) : JFrame() {

    val toolPalette: JPanel = ToolPalettePanel(ctx).apply { preferredSize = Dimension(40, 0) }
    val tokensPanel: JPanel = TokensCollectionPanel(ctx, ctx.thumbnails).apply { preferredSize = Dimension(240, 0) }
    val layersPanel: JPanel = LayersPanel(ctx).apply { preferredSize = Dimension(260, 240) }
    val propertiesPanel: JPanel = LayerPropertiesPanel(ctx).apply { preferredSize = Dimension(260, 280) }
    val assetsPanel: JPanel = AssetsLibraryPanel(ctx).apply { preferredSize = Dimension(0, 240) }
    val statusBar: JPanel = StatusBar(ctx)

    val canvasComponent: TokenCanvasPanel = TokenCanvasPanel(ctx)
    val menuBuilder: MenuBuilder = MenuBuilder(ctx, this)

    init { ctx.canvasMapper = canvasComponent.mapper }

    val canvasPanel: JPanel = JPanel(BorderLayout()).apply {
        val overlayHost = JPanel(BorderLayout())
        overlayHost.add(canvasComponent, BorderLayout.CENTER)

        add(overlayHost, BorderLayout.CENTER)

        val south = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 2))
        val overlayToggle = JCheckBox(ctx.locale.t("button.show.overlay")).apply {
            isSelected = ctx.viewState.showOverlay
            addActionListener { ctx.viewState.setShowOverlay(isSelected) }
        }
        val sameSidesBox = JCheckBox(ctx.locale.t("checkbox.same.sides")).apply {
            isEnabled = false
            addActionListener {
                val tokenId = ctx.viewState.activeTokenId ?: return@addActionListener
                ctx.history.execute(ctx.bag, SetSameSidesCommand(tokenId, newValue = isSelected))
                if (isSelected) ctx.viewState.setActiveSide(TokenSide.FRONT)
            }
        }
        val flipBtn = javax.swing.JButton(ctx.locale.t("button.flip.side")).apply {
            addActionListener {
                val cur = ctx.viewState.activeSide
                val next = if (cur == TokenSide.FRONT) TokenSide.BACK else TokenSide.FRONT
                ctx.viewState.setActiveSide(next)
            }
        }

        val refreshSameSides: () -> Unit = {
            val token = ctx.viewState.activeTokenId?.let { ctx.bag.findToken(it) }
            val hasSameSides = token?.sameSides == true
            sameSidesBox.isEnabled = token != null
            sameSidesBox.isSelected = hasSameSides
            flipBtn.isEnabled = token != null && !hasSameSides
        }
        ctx.viewState.addListener { refreshSameSides() }
        ctx.addBagListener { e -> if (e is ModelEvent.SameSidesChanged) refreshSameSides() }
        refreshSameSides()

        south.add(overlayToggle)
        south.add(sameSidesBox)
        south.add(flipBtn)
        add(south, BorderLayout.SOUTH)

    }

    init {
        title = ctx.locale.t("app.title")
        iconImages = Icons.appIcon
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        layout = BorderLayout()

        val rightStack = JSplitPane(JSplitPane.VERTICAL_SPLIT, layersPanel, propertiesPanel).apply {
            resizeWeight = 0.75
            isContinuousLayout = true
        }
        val toolAndRight = JPanel(BorderLayout()).apply {
            add(toolPalette, BorderLayout.WEST)
            add(rightStack, BorderLayout.CENTER)
        }
        val centerWithRight = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasPanel, toolAndRight).apply {
            resizeWeight = 0.75
            isContinuousLayout = true
        }
        val mainWithLeft = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tokensPanel, centerWithRight).apply {
            resizeWeight = 0.15
            isContinuousLayout = true
        }
        val full = JSplitPane(JSplitPane.VERTICAL_SPLIT, mainWithLeft, assetsPanel).apply {
            resizeWeight = 0.7
            isContinuousLayout = true
        }

        add(full, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)

        // Ctrl+0 reset zoom
        val resetKey = KeyStroke.getKeyStroke("control 0")
        canvasPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(resetKey, "resetZoom")
        canvasPanel.actionMap.put("resetZoom", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                ctx.viewState.setZoom(1f)
                canvasComponent.panX = 0; canvasComponent.panY = 0
                canvasComponent.repaint()
            }
        })

        // Esc clears selection
        val esc = KeyStroke.getKeyStroke("ESCAPE")
        canvasPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, "clearSelection")
        canvasPanel.actionMap.put("clearSelection", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                ctx.viewState.clearSelection()
                ctx.viewState.setActiveTool(ToolId.SELECT)
            }
        })

        ToolController(ctx, canvasComponent)

        jMenuBar = menuBuilder.build()

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                if (!ctx.dirty) { ctx.savePrefs(); ctx.shutdown(); dispose(); exitProcess(0); return }
                when (SaveBeforeCloseDialog.ask(this@MainFrame, ctx.locale)) {
                    SaveBeforeCloseDialog.Result.SAVE -> {
                        if (menuBuilder.save()) { ctx.savePrefs(); ctx.shutdown(); dispose(); exitProcess(0)
                        }
                    }
                    SaveBeforeCloseDialog.Result.DISCARD -> {
                        ctx.savePrefs(); ctx.shutdown(); dispose(); exitProcess(0)
                    }
                    SaveBeforeCloseDialog.Result.CANCEL -> { /* stay open */ }
                }
            }
        })

        preferredSize = Dimension(1280, 800)
        minimumSize = Dimension(600, 600)
        pack()
        setLocationRelativeTo(null)

    }
}
