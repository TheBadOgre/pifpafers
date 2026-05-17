package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.AddTokenCommand
import net.rafkos.neuroshima.editor.command.RemoveTokenCommand
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.render.ThumbnailRenderer
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider

class TokensCollectionPanel(
    private val ctx: AppContext,
    private val thumbnails: ThumbnailRenderer,
) : JPanel() {

    private val grid = JPanel(GridLayout(0, 2, 4, 4))
    private val slider = JSlider(48, 192, ctx.viewState.collectionThumbSize)

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.tokens"))
        add(JScrollPane(grid), BorderLayout.CENTER)

        val south = JPanel(BorderLayout())
        south.add(slider, BorderLayout.NORTH)
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        buttons.add(JButton(ctx.locale.t("button.add.unit")).apply {
            addActionListener {
                ctx.history.execute(ctx.bag, AddTokenCommand(TokenKind.UNIT, ctx.bag.tokens.size))
            }
        })
        buttons.add(JButton(ctx.locale.t("button.add.modifier")).apply {
            addActionListener {
                ctx.history.execute(ctx.bag, AddTokenCommand(TokenKind.MODIFIER, ctx.bag.tokens.size))
            }
        })
        south.add(buttons, BorderLayout.SOUTH)
        add(south, BorderLayout.SOUTH)

        slider.addChangeListener { ctx.viewState.setCollectionThumbSize(slider.value) }

        ctx.bag.addListener { rebuild() }
        ctx.viewState.addListener { rebuild() }
        rebuild()
    }

    private fun rebuild() {
        grid.removeAll()
        val size = ctx.viewState.collectionThumbSize
        for (token in ctx.bag.tokens) {
            val img = thumbnails.tokenThumbnail(token, size)
            val cell = JPanel(BorderLayout()).apply {
                preferredSize = Dimension(size + 8, size + 8)
                border = BorderFactory.createLineBorder(
                    if (token.id == ctx.viewState.activeTokenId) Color.BLUE else Color.GRAY,
                    if (token.id == ctx.viewState.activeTokenId) 2 else 1,
                )
            }
            val lbl = JLabel(ImageIcon(img))
            lbl.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) {
                        ctx.viewState.setActiveToken(token.id)
                    } else if (e.button == MouseEvent.BUTTON3) {
                        val ok = JOptionPane.showConfirmDialog(
                            this@TokensCollectionPanel,
                            "Delete token?",
                            "Confirm",
                            JOptionPane.YES_NO_OPTION,
                        )
                        if (ok == JOptionPane.YES_OPTION) {
                            ctx.history.execute(ctx.bag, RemoveTokenCommand(token.id))
                        }
                    }
                }
            })
            cell.add(lbl, BorderLayout.CENTER)
            grid.add(cell)
        }
        grid.revalidate()
        grid.repaint()
    }
}
