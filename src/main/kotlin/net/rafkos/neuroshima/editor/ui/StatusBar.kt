package net.rafkos.neuroshima.editor.ui

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.model.TokenKind
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel

class StatusBar(private val ctx: AppContext) : JPanel(FlowLayout(FlowLayout.LEFT, 12, 2)) {
    private val fileLabel = JLabel()
    private val countLabel = JLabel()
    private val savedLabel = JLabel()

    init {
        add(fileLabel); add(countLabel); add(savedLabel)
        ctx.bag.addListener { refresh() }
        refresh()
    }

    private fun refresh() {
        fileLabel.text = ctx.locale.t("status.loadedFile", ctx.currentFile?.fileName?.toString() ?: "—")
        val units = ctx.bag.tokens.count { it.kind == TokenKind.UNIT }
        val mods  = ctx.bag.tokens.count { it.kind == TokenKind.MODIFIER }
        countLabel.text = ctx.locale.t("status.tokens", ctx.bag.tokens.size, units, mods)
        savedLabel.text = if (ctx.dirty) "*" else ""
    }
}
