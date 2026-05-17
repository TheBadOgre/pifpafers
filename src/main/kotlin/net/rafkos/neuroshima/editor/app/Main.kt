package net.rafkos.neuroshima.editor.app

import net.rafkos.neuroshima.editor.ui.MainFrame
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.SwingUtilities

fun main() {
    val appDir = System.getProperty("app.dir")?.let { Paths.get(it) }
        ?: Paths.get(System.getProperty("user.dir"))
    val bundledRoot: Path = appDir.resolve("assets")
    val userRoot: Path = Paths.get(System.getProperty("user.home"))
        .resolve(".neuroshima-editor").resolve("content")
    val prefsFile: Path = Paths.get(System.getProperty("user.home"))
        .resolve(".neuroshima-editor").resolve("prefs.json")

    val ctx = AppContext(bundledRoot = bundledRoot, userRoot = userRoot, prefsFile = prefsFile)
    SwingUtilities.invokeLater {
        val frame = MainFrame(ctx)
        frame.isVisible = true
        Runtime.getRuntime().addShutdownHook(Thread { ctx.savePrefs() })
    }
}
