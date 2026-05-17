package net.rafkos.neuroshima.editor.app

import net.rafkos.neuroshima.editor.ui.MainFrame
import java.nio.file.Paths
import javax.swing.SwingUtilities

fun main() {
    val bundledRoot = AppDirs.assetsRoot.toPath()
    val userRoot = Paths.get(System.getProperty("user.home"))
        .resolve(".neuroshima-editor").resolve("content")
    val prefsFile = Paths.get(System.getProperty("user.home"))
        .resolve(".neuroshima-editor").resolve("prefs.json")

    val ctx = AppContext(bundledRoot = bundledRoot, userRoot = userRoot, prefsFile = prefsFile)
    SwingUtilities.invokeLater {
        val frame = MainFrame(ctx)
        frame.isVisible = true
        Runtime.getRuntime().addShutdownHook(Thread { ctx.savePrefs() })
    }
}
