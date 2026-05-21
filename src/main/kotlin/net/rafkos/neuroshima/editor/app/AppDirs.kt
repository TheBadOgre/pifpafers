package net.rafkos.neuroshima.editor.app

import java.io.File

object AppDirs {
    private val appDir: String? = System.getProperty("app.dir")
    private val installRoot: File? = if (appDir != null) File(appDir).parentFile else null
    private val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    val root: File = File(appDir ?: ".")

    val assetsRoot: File = installRoot?.resolve("assets") ?: root.resolve("assets")
    val overlayRoot: File = installRoot?.resolve("overlay") ?: root.resolve("overlay")

    val userContentRoot: File = if (isWindows) {
        (installRoot ?: root).resolve("content")
    } else {
        File(System.getProperty("user.home"), ".neuroshima-editor/content")
    }

    fun resolve(path: String): File = root.resolve(path)
}
