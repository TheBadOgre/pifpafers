package net.rafkos.neuroshima.editor.app

import java.io.File

object AppDirs {
    private val appDir: String? = System.getProperty("app.dir")
    private val installRoot: File? = if (appDir != null) File(appDir).parentFile else null

    val root: File = File(appDir ?: ".")

    val assetsRoot: File = installRoot?.resolve("assets") ?: root.resolve("assets")
    val overlayRoot: File = installRoot?.resolve("overlay") ?: root.resolve("overlay")

    fun resolve(path: String): File = root.resolve(path)
}
