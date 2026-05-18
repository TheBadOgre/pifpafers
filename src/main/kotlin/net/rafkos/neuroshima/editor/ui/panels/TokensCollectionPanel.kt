package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.AddTokenCommand
import net.rafkos.neuroshima.editor.command.DuplicateTokenCommand
import net.rafkos.neuroshima.editor.command.RemoveTokenCommand
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.render.ThumbnailRenderer
import net.rafkos.neuroshima.editor.ui.WrapLayout
import net.rafkos.neuroshima.editor.ui.icon.Icons
import net.rafkos.neuroshima.editor.ui.preview.PreviewKey
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider

private const val BTN = 32

class TokensCollectionPanel(
    private val ctx: AppContext,
    private val thumbnails: ThumbnailRenderer,
) : JPanel() {

    private val grid   = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4))
    private val slider = JSlider(48, 192, ctx.viewState.collectionThumbSize)
    private var currentSnapshotKey: PreviewKey.TokenSnapshot? = null
    private var currentSnapshotLabel: JLabel? = null
    private val deleteBtn: JButton
    private val duplicateBtn: JButton

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.tokens"))
        add(JScrollPane(grid), BorderLayout.CENTER)

        deleteBtn = iconBtn(Icons.tokenDelete, ctx.locale.t("button.delete.token")) {
            val id = ctx.viewState.activeTokenId ?: return@iconBtn
            val yes = ctx.locale.t("dialog.yes")
            val no  = ctx.locale.t("dialog.no")
            val ok  = JOptionPane.showOptionDialog(
                this@TokensCollectionPanel,
                ctx.locale.t("dialog.token.delete"),
                ctx.locale.t("dialog.confirm"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, arrayOf(yes, no), yes,
            )
            if (ok == 0) ctx.history.execute(ctx.bag, RemoveTokenCommand(id))
        }

        duplicateBtn = iconBtn(Icons.tokenDuplicate, ctx.locale.t("button.duplicate.token")) {
            val id = ctx.viewState.activeTokenId ?: return@iconBtn
            val cmd = DuplicateTokenCommand(id)
            ctx.history.execute(ctx.bag, cmd)
            cmd.newTokenId?.let { ctx.viewState.setActiveToken(it) }
        }

        val south = JPanel(BorderLayout()).apply {
            val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
            buttons.add(iconBtn(Icons.tokenAddUnit, ctx.locale.t("button.add.unit")) {
                val cmd = AddTokenCommand(TokenKind.UNIT, ctx.bag.tokens.size)
                ctx.history.execute(ctx.bag, cmd)
                cmd.createdId?.let { ctx.viewState.setActiveToken(it) }
            })
            buttons.add(iconBtn(Icons.tokenAddMod, ctx.locale.t("button.add.modifier")) {
                val cmd = AddTokenCommand(TokenKind.MODIFIER, ctx.bag.tokens.size)
                ctx.history.execute(ctx.bag, cmd)
                cmd.createdId?.let { ctx.viewState.setActiveToken(it) }
            })
            buttons.add(duplicateBtn)
            buttons.add(deleteBtn)
            add(buttons, BorderLayout.WEST)
            slider.preferredSize = Dimension(120, slider.preferredSize.height)
            add(JPanel(FlowLayout(FlowLayout.TRAILING, 4, 2)).apply { add(slider) }, BorderLayout.EAST)
        }
        add(south, BorderLayout.SOUTH)

        slider.addChangeListener {
            ctx.viewState.setCollectionThumbSize(slider.value)
            if (!slider.valueIsAdjusting) {
                val activeId = ctx.viewState.activeTokenId ?: return@addChangeListener
                val size = ctx.viewState.collectionThumbSize
                val token = ctx.bag.findToken(activeId)
                val displaySize = if (token?.kind == TokenKind.MODIFIER) (size * 0.55).toInt().coerceAtLeast(24) else size
                updateSnapshotSubscription(activeId, displaySize)
            }
        }

        ctx.addBagListener { rebuild() }
        ctx.viewState.addListener { rebuild() }
        rebuild()
    }

    private fun iconBtn(icon: javax.swing.ImageIcon, tooltip: String, action: () -> Unit) =
        JButton(icon).apply {
            toolTipText = tooltip
            preferredSize = Dimension(BTN, BTN)
            minimumSize   = Dimension(BTN, BTN)
            maximumSize   = Dimension(BTN, BTN)
            margin        = Insets(2, 2, 2, 2)
            addActionListener { action() }
        }

    private fun rebuild() {
        val isAdjusting = slider.valueIsAdjusting
        grid.removeAll()
        currentSnapshotLabel = null
        val size     = ctx.viewState.collectionThumbSize
        val activeId = ctx.viewState.activeTokenId
        var activeDisplaySize = size
        for (token in ctx.bag.tokens) {
            val displaySize = if (token.kind == TokenKind.MODIFIER) (size * 0.55).toInt().coerceAtLeast(24) else size
            val isActive = token.id == activeId
            if (isActive) activeDisplaySize = displaySize
            val img = thumbnails.tokenThumbnail(token, displaySize)
            val cell = JPanel(BorderLayout()).apply {
                preferredSize = Dimension(size + 8, size + 8)
                val innerBorder = BorderFactory.createRaisedBevelBorder()
                val outerBorder = BorderFactory.createLineBorder(
                    if (isActive) Color.BLUE else Color.GRAY,
                    if (isActive) 2 else 1,
                )
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createEmptyBorder(1, 1, 1, 1),
                    BorderFactory.createCompoundBorder(outerBorder, innerBorder),
                )
            }
            val lbl = JLabel(ImageIcon(img), JLabel.CENTER)
            if (isActive) currentSnapshotLabel = lbl
            lbl.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            lbl.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) {
                        ctx.viewState.setActiveToken(token.id)
                    } else if (e.button == MouseEvent.BUTTON3) {
                        val yes = ctx.locale.t("dialog.yes")
                        val no  = ctx.locale.t("dialog.no")
                        val ok  = JOptionPane.showOptionDialog(
                            this@TokensCollectionPanel,
                            ctx.locale.t("dialog.token.delete"),
                            ctx.locale.t("dialog.confirm"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null, arrayOf(yes, no), yes,
                        )
                        if (ok == 0) {
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

        val hasActive = activeId != null
        deleteBtn.isEnabled = hasActive
        duplicateBtn.isEnabled = hasActive

        if (!isAdjusting) updateSnapshotSubscription(activeId, activeDisplaySize)
    }

    private fun updateSnapshotSubscription(activeId: UUID?, sizePx: Int) {
        val prev = currentSnapshotKey
        if (prev != null && prev.tokenId != activeId) ctx.previewService.unsubscribe(prev)
        if (activeId == null) { currentSnapshotKey = null; return }
        val key = PreviewKey.TokenSnapshot(activeId)
        currentSnapshotKey = key
        ctx.previewService.subscribe(key, sizePx) { img ->
            currentSnapshotLabel?.icon = ImageIcon(img)
            currentSnapshotLabel?.repaint()
        }
    }
}
