package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath

class AssetTreeNode(val name: String) {
    val childFolders: MutableList<AssetTreeNode> = mutableListOf()
    val assets: MutableList<AssetPath> = mutableListOf()
}
