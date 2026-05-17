package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.DuplicateLayerCommand
import net.rafkos.neuroshima.editor.command.RemoveLayerCommand
import net.rafkos.neuroshima.editor.command.ReorderLayerCommand
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.ui.icon.Icons
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

private const val BTN = 28

class LayersPanel(private val ctx: AppContext) : JPanel() {

    private val list = object : JPanel() {
        init { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        override fun paintChildren(g: Graphics) {
            super.paintChildren(g)
            val dy = dragY
            if (dy < 0 || dragLayerId == null) return
            val g2 = g as Graphics2D
            g2.color = java.awt.Color(0x2196F3)
            g2.stroke = java.awt.BasicStroke(2f)
            g2.drawLine(4, dy, width - 4, dy)
        }
    }
    private val slider = JSlider(48, 192, ctx.viewState.layersThumbSize)

    private var dragLayerId: UUID? = null
    private var dragY: Int = -1

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.layers"))
        add(JScrollPane(list), BorderLayout.CENTER)
        slider.preferredSize = Dimension(120, slider.preferredSize.height)
        add(JPanel(FlowLayout(FlowLayout.TRAILING, 4, 2)).apply { add(slider) }, BorderLayout.SOUTH)
        slider.addChangeListener { ctx.viewState.setLayersThumbSize(slider.value) }

        ctx.bag.addListener { rebuild() }
        ctx.viewState.addListener { rebuild() }
        rebuild()
    }

    private fun iconBtn(icon: javax.swing.ImageIcon, tooltip: String, action: () -> Unit) =
        JButton(icon).apply {
            toolTipText   = tooltip
            preferredSize = Dimension(BTN, BTN)
            minimumSize   = Dimension(BTN, BTN)
            maximumSize   = Dimension(BTN, BTN)
            margin        = Insets(1, 1, 1, 1)
            addActionListener { action() }
        }

    private fun rebuild() {
        list.removeAll()
        val activeId = ctx.viewState.activeTokenId
        val token: Token? = activeId?.let { ctx.bag.findToken(it) }
        val size = ctx.viewState.layersThumbSize
        if (token != null) {
            for (layer in token.layers.asReversed()) {
                val img      = ctx.thumbnails.layerThumbnail(token, layer, size)
                val row      = JPanel(BorderLayout())
                row.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                val selected = layer.id in ctx.viewState.selectedLayers
                val innerBorder = BorderFactory.createRaisedBevelBorder()
                val outerBorder = BorderFactory.createLineBorder(
                    if (selected) Color.BLUE else Color.GRAY,
                    if (selected) 2 else 1,
                )
                row.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(1, 1, 1, 1),
                    BorderFactory.createCompoundBorder(outerBorder, innerBorder),
                )
                row.maximumSize = Dimension(Int.MAX_VALUE, size + 8)
                row.add(JLabel(ImageIcon(img)), BorderLayout.WEST)

                val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 2))
                buttons.add(iconBtn(Icons.layerUp, ctx.locale.t("button.layer.up")) {
                    val idx = token.layers.indexOfFirst { it.id == layer.id }
                    if (idx < token.layers.size - 1)
                        ctx.history.execute(ctx.bag, ReorderLayerCommand(token.id, layer.id, idx + 1))
                })
                buttons.add(iconBtn(Icons.layerDown, ctx.locale.t("button.layer.down")) {
                    val idx = token.layers.indexOfFirst { it.id == layer.id }
                    if (idx > 0)
                        ctx.history.execute(ctx.bag, ReorderLayerCommand(token.id, layer.id, idx - 1))
                })
                buttons.add(iconBtn(Icons.layerDuplicate, ctx.locale.t("button.layer.duplicate")) {
                    ctx.history.execute(ctx.bag, DuplicateLayerCommand(token.id, layer.id))
                })
                buttons.add(iconBtn(Icons.layerRemove, ctx.locale.t("button.layer.remove")) {
                    val ok = JOptionPane.showConfirmDialog(
                        this@LayersPanel,
                        ctx.locale.t("dialog.layer.remove"),
                        ctx.locale.t("dialog.confirm"),
                        JOptionPane.YES_NO_OPTION,
                    )
                    if (ok == JOptionPane.YES_OPTION)
                        ctx.history.execute(ctx.bag, RemoveLayerCommand(token.id, layer.id))
                })
                row.add(buttons, BorderLayout.EAST)

                row.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (e.button != MouseEvent.BUTTON1) return
                        if (e.isControlDown) {
                            if (layer.id in ctx.viewState.selectedLayers) ctx.viewState.deselectLayer(layer.id)
                            else ctx.viewState.selectLayer(layer.id)
                        } else {
                            ctx.viewState.replaceSelection(listOf(layer.id))
                        }
                    }
                })
                val capturedLayerId = layer.id
                val dragAdapter = object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (e.button != MouseEvent.BUTTON1) return
                        if (capturedLayerId !in ctx.viewState.selectedLayers) return
                        dragLayerId = capturedLayerId
                        dragY = SwingUtilities.convertPoint(row, e.point, list).y
                        list.repaint()
                    }
                    override fun mouseDragged(e: MouseEvent) {
                        if (dragLayerId == null) return
                        dragY = SwingUtilities.convertPoint(row, e.point, list).y.coerceIn(0, list.height)
                        list.repaint()
                    }
                    override fun mouseReleased(e: MouseEvent) {
                        if (dragLayerId == null || e.button != MouseEvent.BUTTON1) return
                        val finalY = SwingUtilities.convertPoint(row, e.point, list).y.coerceIn(0, list.height)
                        commitDrop(finalY)
                        dragLayerId = null
                        dragY = -1
                        list.repaint()
                    }
                }
                row.addMouseListener(dragAdapter)
                row.addMouseMotionListener(dragAdapter)
                list.add(row)
            }
        } else {
            list.add(JLabel(ctx.locale.t("label.no.token"), SwingConstants.CENTER))
        }
        list.add(Box.createVerticalGlue())
        list.revalidate(); list.repaint()
    }

    private fun commitDrop(dropY: Int) {
        val activeId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(activeId) ?: return
        val selected = ctx.viewState.selectedLayers
        if (selected.isEmpty()) return

        val rowH = if (list.componentCount > 1) {
            val firstRow = list.getComponent(0)
            firstRow.height.takeIf { it > 0 } ?: (ctx.viewState.layersThumbSize + 8)
        } else {
            ctx.viewState.layersThumbSize + 8
        }
        val reversedLayerCount = token.layers.size
        val visualDropIndex = (dropY / rowH).coerceIn(0, reversedLayerCount)
        val targetLayersIndex = (reversedLayerCount - visualDropIndex).coerceIn(0, reversedLayerCount)

        val selectedSorted = token.layers.filter { it.id in selected }
        for (layer in selectedSorted) {
            val currentIdx = token.layers.indexOfFirst { it.id == layer.id }
            if (currentIdx != targetLayersIndex) {
                ctx.history.execute(ctx.bag, ReorderLayerCommand(activeId, layer.id, targetLayersIndex))
            }
        }
    }
}
