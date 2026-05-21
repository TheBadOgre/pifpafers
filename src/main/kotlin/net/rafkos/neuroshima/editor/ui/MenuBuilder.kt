package net.rafkos.neuroshima.editor.ui

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.PasteLayersCommand
import net.rafkos.neuroshima.editor.model.LayerSnapshot
import net.rafkos.neuroshima.editor.model.ModelEvent
import net.rafkos.neuroshima.editor.persistence.JsonBagStore
import net.rafkos.neuroshima.editor.persistence.MissingAssetsException
import net.rafkos.neuroshima.editor.persistence.SchemaVersionException
import net.rafkos.neuroshima.editor.ui.dialogs.MissingAssetsDialog
import net.rafkos.neuroshima.editor.ui.publish.PublishingDialog
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.KeyStroke
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.text.JTextComponent
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
        m.add(JMenuItem(ctx.locale.t("menu.file.open")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK)
            addActionListener { open() }
        })
        m.add(JMenuItem(ctx.locale.t("menu.file.save")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)
            addActionListener { save() }
        })
        m.add(JMenuItem(ctx.locale.t("menu.file.saveAs")).apply { addActionListener { saveAs() } })
        m.addSeparator()
        val publishItem = JMenuItem(ctx.locale.t("menu.file.publish")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK)
            isEnabled = ctx.bag.tokens.isNotEmpty()
            addActionListener { PublishingDialog(ctx, frame).isVisible = true }
        }
        ctx.addBagListener { e ->
            if (e is ModelEvent.TokenAdded || e is ModelEvent.TokenRemoved) {
                publishItem.isEnabled = ctx.bag.tokens.isNotEmpty()
            }
        }
        ctx.addBagReplacedListener { publishItem.isEnabled = ctx.bag.tokens.isNotEmpty() }
        m.add(publishItem)
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

        m.addSeparator()

        val copyItem = JMenuItem(ctx.locale.t("menu.edit.copy.layers")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK)
            isEnabled = false
        }
        val pasteItem = JMenuItem(ctx.locale.t("menu.edit.paste.layers")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK)
            isEnabled = false
        }

        fun updateCopyEnabled() {
            copyItem.isEnabled = ctx.viewState.activeTokenId != null &&
                ctx.viewState.selectedLayers.isNotEmpty()
        }
        fun updatePasteEnabled() {
            pasteItem.isEnabled = !ctx.clipboard.isEmpty &&
                ctx.viewState.activeTokenId != null
        }

        ctx.viewState.addListener { updateCopyEnabled(); updatePasteEnabled() }
        ctx.clipboard.addListener { updatePasteEnabled() }
        ctx.addBagReplacedListener { updateCopyEnabled(); updatePasteEnabled() }

        copyItem.addActionListener {
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (focusOwner is JTextComponent) return@addActionListener
            val tokenId = ctx.viewState.activeTokenId ?: return@addActionListener
            val token = ctx.bag.findToken(tokenId) ?: return@addActionListener
            val side = ctx.viewState.activeSide
            val selected = ctx.viewState.selectedLayers
            val snapshots = token.layers(side)
                .filter { it.id in selected }
                .map { LayerSnapshot(it.assetPath, it.props) }
            if (snapshots.isNotEmpty()) ctx.clipboard.copy(snapshots)
        }

        pasteItem.addActionListener {
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            if (focusOwner is JTextComponent) return@addActionListener
            if (ctx.clipboard.isEmpty) return@addActionListener
            val tokenId = ctx.viewState.activeTokenId ?: return@addActionListener
            val token = ctx.bag.findToken(tokenId) ?: return@addActionListener
            val side = ctx.viewState.activeSide
            val layers = token.layers(side)
            val selected = ctx.viewState.selectedLayers
            val insertIndex = if (selected.isNotEmpty()) {
                val idx = layers.indexOfLast { it.id in selected }
                if (idx >= 0) idx + 1 else layers.size
            } else {
                layers.size
            }
            val layerIds = ctx.clipboard.contents.map { UUID.randomUUID() }
            ctx.history.execute(ctx.bag, PasteLayersCommand(tokenId, side, ctx.clipboard.contents, insertIndex, layerIds))
            ctx.viewState.replaceSelection(layerIds)
        }

        m.add(copyItem)
        m.add(pasteItem)
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
        val chooser = JFileChooser().apply {
            dialogTitle = ctx.locale.t("chooser.save.army.title")
            fileFilter = FileNameExtensionFilter(ctx.locale.t("filter.box.description"), "box")
        }
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
        val chooser = JFileChooser().apply {
            dialogTitle = ctx.locale.t("chooser.open.army.title")
            fileFilter = FileNameExtensionFilter(ctx.locale.t("filter.box.description"), "box")
        }
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        val path = chooser.selectedFile.toPath()
        val opener = net.rafkos.neuroshima.editor.persistence.BagOpener(ctx.library, ctx.imageCache)
        try {
            val loaded = runBlocking { opener.open(path) }
            ctx.replaceBag(loaded, path)
            frame.title = "${ctx.locale.t("app.title")} — ${path.fileName}"
        } catch (ex: MissingAssetsException) {
            MissingAssetsDialog.show(
                frame,
                ctx.locale.t("dialog.missing.assets.title"),
                ctx.locale.t("error.missing.assets"),
                ex,
            )
        } catch (ex: SchemaVersionException) {
            JOptionPane.showMessageDialog(
                frame,
                ctx.locale.t("error.schema.version", ex.found),
                ctx.locale.t("error.open.failed"),
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }
}
