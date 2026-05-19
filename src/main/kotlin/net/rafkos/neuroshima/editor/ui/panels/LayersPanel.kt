package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.DuplicateLayerCommand
import net.rafkos.neuroshima.editor.command.RemoveLayerCommand
import net.rafkos.neuroshima.editor.command.ReorderLayerCommand
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenSide
import net.rafkos.neuroshima.editor.ui.icon.Icons
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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

private const val BTN = 28

class LayersPanel(private val ctx: AppContext) : JPanel() {

    private val list = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val slider = JSlider(48, 192, ctx.viewState.layersThumbSize)

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.layers"))
        add(JScrollPane(list), BorderLayout.CENTER)
        slider.preferredSize = Dimension(120, slider.preferredSize.height)
        add(JPanel(FlowLayout(FlowLayout.TRAILING, 4, 2)).apply { add(slider) }, BorderLayout.SOUTH)
        slider.addChangeListener { ctx.viewState.setLayersThumbSize(slider.value) }

        ctx.addBagListener { rebuild() }
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
            val side = ctx.viewState.activeSide
            for (layer in token.layers(side).asReversed()) {
                val img      = ctx.thumbnails.layerThumbnail(token, side, layer, size)
                val row      = JPanel(BorderLayout())
                val selected = layer.id in ctx.viewState.selectedLayers
                val innerBorder = BorderFactory.createRaisedBevelBorder()
                val outerBorder = BorderFactory.createLineBorder(
                    if (selected) Color.BLUE else Color.GRAY,
                    2, // fixed thickness for both states
                )
                row.border = BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(1, 1, 1, 1),
                    BorderFactory.createCompoundBorder(outerBorder, innerBorder),
                )
                row.maximumSize = Dimension(Int.MAX_VALUE, size + 8)

                val lbl = JLabel(ImageIcon(img))
                row.add(lbl, BorderLayout.WEST)

                val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 2))
                buttons.add(iconBtn(Icons.layerUp, ctx.locale.t("button.layer.up")) {
                    val idx = token.layers(side).indexOfFirst { it.id == layer.id }
                    if (idx < token.layers(side).size - 1)
                        ctx.history.execute(ctx.bag, ReorderLayerCommand(token.id, side, layer.id, idx + 1))
                })
                buttons.add(iconBtn(Icons.layerDown, ctx.locale.t("button.layer.down")) {
                    val idx = token.layers(side).indexOfFirst { it.id == layer.id }
                    if (idx > 0)
                        ctx.history.execute(ctx.bag, ReorderLayerCommand(token.id, side, layer.id, idx - 1))
                })
                buttons.add(iconBtn(Icons.layerDuplicate, ctx.locale.t("button.layer.duplicate")) {
                    ctx.history.execute(ctx.bag, DuplicateLayerCommand(token.id, side, layer.id))
                })
                buttons.add(iconBtn(Icons.layerRemove, ctx.locale.t("button.layer.remove")) {
                    val yes = ctx.locale.t("dialog.yes")
                    val no  = ctx.locale.t("dialog.no")
                    val ok  = JOptionPane.showOptionDialog(
                        this@LayersPanel,
                        ctx.locale.t("dialog.layer.remove"),
                        ctx.locale.t("dialog.confirm"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null, arrayOf(yes, no), yes,
                    )
                    if (ok == 0) ctx.history.execute(ctx.bag, RemoveLayerCommand(token.id, side, layer.id))
                })
                row.add(buttons, BorderLayout.EAST)

                val selectListener = object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (e.button != MouseEvent.BUTTON1) return
                        if (e.isControlDown) {
                            if (layer.id in ctx.viewState.selectedLayers) ctx.viewState.deselectLayer(layer.id)
                            else ctx.viewState.selectLayer(layer.id)
                        } else {
                            ctx.viewState.replaceSelection(listOf(layer.id))
                        }
                    }
                }
                row.addMouseListener(selectListener)
                lbl.addMouseListener(selectListener)

                list.add(row)
            }
        } else {
            list.add(JLabel(ctx.locale.t("label.no.token"), SwingConstants.CENTER))
        }
        list.add(Box.createVerticalGlue())
        list.revalidate(); list.repaint()
    }

}
