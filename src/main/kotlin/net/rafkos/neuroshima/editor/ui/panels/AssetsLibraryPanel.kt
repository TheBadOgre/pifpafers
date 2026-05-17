package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.assets.AssetTreeNode
import net.rafkos.neuroshima.editor.command.AddLayerCommand
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.ui.WrapLayout
import net.rafkos.neuroshima.editor.ui.icon.Icons
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

private const val BTN = 32

private class FolderUserObject(val node: AssetTreeNode) {
    override fun toString(): String = node.name.ifEmpty { "content" }
}

class AssetsLibraryPanel(private val ctx: AppContext) : JPanel() {

    private val previewGrid = JPanel(WrapLayout(FlowLayout.LEFT, 4, 4))
    private val tree = JTree(DefaultTreeModel(DefaultMutableTreeNode(FolderUserObject(AssetTreeNode("")))))
    private val slider = JSlider(48, 192, ctx.viewState.assetsThumbSize)

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.assets"))
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(tree), JScrollPane(previewGrid))
        split.resizeWeight = 0.3
        add(split, BorderLayout.CENTER)

        val south = JPanel(BorderLayout())
        south.add(JButton(Icons.assetsRefresh).apply {
            toolTipText   = ctx.locale.t("button.refresh")
            preferredSize = Dimension(BTN, BTN)
            minimumSize   = Dimension(BTN, BTN)
            maximumSize   = Dimension(BTN, BTN)
            margin        = Insets(2, 2, 2, 2)
            addActionListener {
                val savedPath = currentFolderNames()
                ctx.library.refreshUser()
                rebuildTree()
                restorePath(savedPath)
            }
        }, BorderLayout.WEST)
        slider.preferredSize = Dimension(120, slider.preferredSize.height)
        south.add(JPanel(FlowLayout(FlowLayout.TRAILING, 4, 2)).apply { add(slider) }, BorderLayout.EAST)
        add(south, BorderLayout.SOUTH)

        slider.addChangeListener { ctx.viewState.setAssetsThumbSize(slider.value); refreshPreview() }
        tree.addTreeSelectionListener(object : TreeSelectionListener {
            override fun valueChanged(e: TreeSelectionEvent?) { refreshPreview() }
        })

        rebuildTree()
        selectRoot()
    }

    private fun rebuildTree() {
        val root = DefaultMutableTreeNode(FolderUserObject(ctx.library.tree()))
        buildSubtree(root, (root.userObject as FolderUserObject).node)
        tree.model = DefaultTreeModel(root)
    }

    private fun buildSubtree(parent: DefaultMutableTreeNode, node: AssetTreeNode) {
        for (child in node.childFolders.sortedBy { it.name }) {
            val childNode = DefaultMutableTreeNode(FolderUserObject(child))
            parent.add(childNode)
            buildSubtree(childNode, child)
        }
    }

    private fun selectRoot() {
        val root = tree.model.root as DefaultMutableTreeNode
        tree.selectionPath = TreePath(root.path)
    }

    private fun currentFolderNames(): List<String> {
        val path = tree.selectionPath ?: return emptyList()
        return path.path.drop(1).map { (it as DefaultMutableTreeNode).userObject.toString() }
    }

    private fun restorePath(names: List<String>) {
        if (names.isEmpty()) { selectRoot(); return }
        val root = tree.model.root as DefaultMutableTreeNode
        var node: DefaultMutableTreeNode = root
        for (name in names) {
            val child = (0 until node.childCount)
                .mapNotNull { node.getChildAt(it) as? DefaultMutableTreeNode }
                .firstOrNull { it.userObject.toString() == name }
                ?: run { tree.selectionPath = TreePath(node.path); return }
            node = child
        }
        tree.selectionPath = TreePath(node.path)
    }

    private fun selectedFolder(): AssetTreeNode? {
        val path = tree.selectionPath ?: return null
        val last = path.lastPathComponent as DefaultMutableTreeNode
        return (last.userObject as? FolderUserObject)?.node
    }

    private fun refreshPreview() {
        previewGrid.removeAll()
        val node = selectedFolder() ?: return
        val size = ctx.viewState.assetsThumbSize
        for (asset in node.assets) {
            val source = ctx.imageCache.get(asset) ?: run {
                val file = ctx.library.resolveFile(asset) ?: return@run null
                file.toFile().inputStream().use { ImageIO.read(it) }?.also { ctx.imageCache.put(asset, it) }
            } ?: continue
            val thumb = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { t ->
                val g = t.createGraphics()
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.drawImage(source, 0, 0, size, size, null)
                g.dispose()
            }
            val lbl = JLabel(ImageIcon(thumb), JLabel.CENTER).apply {
                preferredSize = Dimension(size + 4, size + 4)
                toolTipText = asset.uri
            }
            lbl.addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) addLayer(asset)
                }
            })
            previewGrid.add(lbl)
        }
        previewGrid.revalidate(); previewGrid.repaint()
    }

    private fun addLayer(asset: AssetPath) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        ctx.library.resolveFile(asset)?.let { p ->
            val img = p.toFile().inputStream().use { ImageIO.read(it) }
            if (img != null) ctx.imageCache.put(asset, img)
        }
        val cmd = AddLayerCommand(tokenId, asset)
        ctx.history.execute(ctx.bag, cmd)
        cmd.layerId?.let { ctx.viewState.replaceSelection(listOf(it)) }
    }
}
