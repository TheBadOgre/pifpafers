package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath
import java.nio.file.Files
import java.nio.file.Path

class AssetLibrary(
    private val bundledRoot: Path,
    private val userRoot: Path,
) {
    private val bundledAssets: MutableMap<String, Path> = mutableMapOf()
    private val userAssets: MutableMap<String, Path> = mutableMapOf()

    fun scan() {
        bundledAssets.clear()
        userAssets.clear()
        scanRoot(bundledRoot, bundledAssets)
        scanRoot(userRoot, userAssets)
    }

    fun refreshUser() {
        userAssets.clear()
        scanRoot(userRoot, userAssets)
    }

    private fun scanRoot(root: Path, into: MutableMap<String, Path>) {
        if (!Files.isDirectory(root)) return
        Files.walk(root).use { stream ->
            for (p in stream) {
                if (!Files.isRegularFile(p)) continue
                if (!p.fileName.toString().endsWith(".png", ignoreCase = true)) continue
                val rel = root.relativize(p).toString().replace('\\', '/')
                into[rel] = p
            }
        }
    }

    fun allAssets(): List<AssetPath> {
        val out = mutableListOf<AssetPath>()
        for (rel in bundledAssets.keys) out += AssetPath.Bundled(rel)
        for (rel in userAssets.keys) {
            if (rel !in bundledAssets) out += AssetPath.User(rel)
        }
        return out
    }

    fun resolveFile(path: AssetPath): Path? = when (path) {
        is AssetPath.Bundled -> bundledAssets[path.relativePath]
        is AssetPath.User -> userAssets[path.relativePath]
    }

    fun assetExists(path: AssetPath): Boolean = resolveFile(path) != null

    fun tree(): AssetTreeNode {
        val root = AssetTreeNode("")
        for (asset in allAssets().sortedBy { it.relativePath }) insertIntoTree(root, asset)
        return root
    }

    private fun insertIntoTree(root: AssetTreeNode, asset: AssetPath) {
        val parts = asset.relativePath.split('/')
        var current = root
        for (i in 0 until parts.size - 1) {
            val folderName = parts[i]
            val child = current.childFolders.firstOrNull { it.name == folderName }
                ?: AssetTreeNode(folderName).also { current.childFolders += it }
            current = child
        }
        current.assets += asset
    }
}
