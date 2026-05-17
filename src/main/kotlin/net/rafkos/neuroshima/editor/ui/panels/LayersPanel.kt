package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.RemoveLayerCommand
import net.rafkos.neuroshima.editor.command.ReorderLayerCommand
import net.rafkos.neuroshima.editor.model.Token
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
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

class LayersPanel(private val ctx: AppContext) : JPanel() {

    private val list = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val slider = JSlider(48, 192, ctx.viewState.layersThumbSize)

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.layers"))
        add(JScrollPane(list), BorderLayout.CENTER)
        slider.preferredSize = Dimension(120, slider.preferredSize.height)
        val sliderRow = JPanel(FlowLayout(FlowLayout.TRAILING, 4, 2)).apply { add(slider) }
        add(sliderRow, BorderLayout.SOUTH)
        slider.addChangeListener { ctx.viewState.setLayersThumbSize(slider.value) }

        ctx.bag.addListener { rebuild() }
        ctx.viewState.addListener { rebuild() }
        rebuild()
    }

    private fun rebuild() {
        list.removeAll()
        val activeId = ctx.viewState.activeTokenId
        val token: Token? = activeId?.let { ctx.bag.findToken(it) }
        val size = ctx.viewState.layersThumbSize
        if (token != null) {
            val layersTopFirst = token.layers.asReversed()
            for (layer in layersTopFirst) {
                val img = ctx.thumbnails.layerThumbnail(token, layer, size)
                val row = JPanel(BorderLayout())
                val selected = layer.id in ctx.viewState.selectedLayers
                row.border = BorderFactory.createLineBorder(
                    if (selected) Color.BLUE else Color.GRAY,
                    if (selected) 2 else 1,
                )
                row.maximumSize = Dimension(Int.MAX_VALUE, size + 8)
                row.add(JLabel(ImageIcon(img)), BorderLayout.WEST)

                val buttons = JPanel(FlowLayout(FlowLayout.RIGHT))
                val up = JButton("▲").apply {
                    addActionListener {
                        val srcIdx = token.layers.indexOfFirst { it.id == layer.id }
                        if (srcIdx < token.layers.size - 1) {
                            ctx.history.execute(ctx.bag, ReorderLayerCommand(token.id, layer.id, srcIdx + 1))
                        }
                    }
                }
                val down = JButton("▼").apply {
                    addActionListener {
                        val srcIdx = token.layers.indexOfFirst { it.id == layer.id }
                        if (srcIdx > 0) {
                            ctx.history.execute(ctx.bag, ReorderLayerCommand(token.id, layer.id, srcIdx - 1))
                        }
                    }
                }
                val remove = JButton("X").apply {
                    addActionListener {
                        val ok = JOptionPane.showConfirmDialog(
                            this@LayersPanel,
                            "Remove layer?",
                            "Confirm",
                            JOptionPane.YES_NO_OPTION,
                        )
                        if (ok == JOptionPane.YES_OPTION) {
                            ctx.history.execute(ctx.bag, RemoveLayerCommand(token.id, layer.id))
                        }
                    }
                }
                buttons.add(up); buttons.add(down); buttons.add(remove)
                row.add(buttons, BorderLayout.EAST)
                row.addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.button != MouseEvent.BUTTON1) return
                        if (e.isControlDown) {
                            if (layer.id in ctx.viewState.selectedLayers) ctx.viewState.deselectLayer(layer.id)
                            else ctx.viewState.selectLayer(layer.id)
                        } else {
                            ctx.viewState.replaceSelection(listOf(layer.id))
                        }
                    }
                })
                list.add(row)
            }
        } else {
            list.add(JLabel("No token selected", SwingConstants.CENTER))
        }
        list.add(Box.createVerticalGlue())
        list.revalidate(); list.repaint()
    }
}
