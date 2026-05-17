package net.rafkos.neuroshima.editor.ui

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.persistence.JsonBagStore
import net.rafkos.neuroshima.editor.persistence.MissingAssetsException
import net.rafkos.neuroshima.editor.persistence.SchemaVersionException
import net.rafkos.neuroshima.editor.ui.dialogs.MissingAssetsDialog
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.KeyStroke
import kotlinx.coroutines.runBlocking

class MenuBuilder(private val ctx: AppContext, private val frame: MainFrame) {

    fun build(): JMenuBar {
        val bar = JMenuBar()
        bar.add(fileMenu())
        bar.add(editMenu())
        return bar
    }

    private fun fileMenu(): JMenu {
        val m = JMenu(ctx.locale.t("menu.file"))
        m.add(JMenuItem("Open...").apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK)
            addActionListener { open() }
        })
        m.add(JMenuItem(ctx.locale.t("menu.file.save")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)
            addActionListener { save() }
        })
        m.add(JMenuItem(ctx.locale.t("menu.file.saveAs")).apply { addActionListener { saveAs() } })
        m.addSeparator()
        m.add(JMenuItem(ctx.locale.t("menu.file.print")).apply {
            isEnabled = false
            toolTipText = "Out of scope for first release"
        })
        return m
    }

    private fun editMenu(): JMenu {
        val m = JMenu(ctx.locale.t("menu.edit"))
        m.add(JMenuItem(ctx.locale.t("menu.edit.undo")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
            addActionListener { ctx.history.undo(ctx.bag) }
        })
        m.add(JMenuItem(ctx.locale.t("menu.edit.redo")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK)
            addActionListener { ctx.history.redo(ctx.bag) }
        })
        return m
    }

    fun save(): Boolean {
        val file = ctx.currentFile ?: return saveAs()
        JsonBagStore(assetResolver = ctx.library::assetExists).save(ctx.bag, file)
        ctx.markClean()
        ctx.history.clear()
        return true
    }

    fun saveAs(): Boolean {
        val chooser = JFileChooser()
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return false
        val raw: File = chooser.selectedFile
        val path = if (raw.extension.equals("box", ignoreCase = true)) raw.toPath()
            else File("${raw.absolutePath}.box").toPath()
        JsonBagStore(assetResolver = ctx.library::assetExists).save(ctx.bag, path)
        ctx.markClean()
        ctx.history.clear()
        frame.title = "${ctx.locale.t("app.title")} — ${path.fileName}"
        return true
    }

    private fun open() {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        val path = chooser.selectedFile.toPath()
        val opener = net.rafkos.neuroshima.editor.persistence.BagOpener(ctx.library, ctx.imageCache)
        try {
            val loaded = runBlocking { opener.open(path) }
            ctx.replaceBag(loaded, path)
            frame.title = "${ctx.locale.t("app.title")} — ${path.fileName}"
        } catch (ex: MissingAssetsException) {
            MissingAssetsDialog.show(frame, ctx.locale.t("error.missing.assets"), ex)
        } catch (ex: SchemaVersionException) {
            JOptionPane.showMessageDialog(
                frame,
                ctx.locale.t("error.schema.version", ex.found),
                "Open failed",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }
}
