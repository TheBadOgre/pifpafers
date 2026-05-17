package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.assets.AssetTreeNode
import net.rafkos.neuroshima.editor.command.AddLayerCommand
import net.rafkos.neuroshima.editor.model.AssetPath
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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

private class FolderUserObject(val node: AssetTreeNode) {
    override fun toString(): String = node.name.ifEmpty { "content" }
}

class AssetsLibraryPanel(private val ctx: AppContext) : JPanel() {

    private val previewGrid = JPanel(GridLayout(0, 4, 4, 4))
    private val tree = JTree(DefaultTreeModel(DefaultMutableTreeNode(FolderUserObject(AssetTreeNode("")))))
    private val slider = JSlider(48, 192, ctx.viewState.assetsThumbSize)

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.assets"))
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(tree), JScrollPane(previewGrid))
        split.resizeWeight = 0.3
        add(split, BorderLayout.CENTER)

        val south = JPanel(BorderLayout())
        south.add(JButton(ctx.locale.t("button.refresh")).apply {
            addActionListener { ctx.library.refreshUser(); rebuildTree(); selectRoot() }
        }, BorderLayout.WEST)
        south.add(slider, BorderLayout.CENTER)
        add(south, BorderLayout.SOUTH)

        slider.addChangeListener { ctx.viewState.setAssetsThumbSize(slider.value); refreshPreview() }
        tree.addTreeSelectionListener(object : TreeSelectionListener {
            override fun valueChanged(e: TreeSelectionEvent?) { refreshPreview() }
        })
        ctx.viewState.addListener { refreshPreview() }

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
            val file = ctx.library.resolveFile(asset) ?: continue
            val source = file.toFile().inputStream().use { ImageIO.read(it) } ?: continue
            val scaled = source.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH)
            val lbl = JLabel(ImageIcon(scaled), JLabel.CENTER).apply {
                preferredSize = Dimension(size + 4, size + 4)
                toolTipText = asset.uri
            }
            lbl.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
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
        ctx.history.execute(ctx.bag, AddLayerCommand(tokenId, asset))
    }
}
