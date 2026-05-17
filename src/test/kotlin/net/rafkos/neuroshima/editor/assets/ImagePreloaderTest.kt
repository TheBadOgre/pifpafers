package net.rafkos.neuroshima.editor.assets

import kotlinx.coroutines.runBlocking
import net.rafkos.neuroshima.editor.model.AssetPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class ImagePreloaderTest {

    private fun writePng(p: Path, w: Int = 2, h: Int = 2) {
        Files.createDirectories(p.parent)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        Files.newOutputStream(p).use { ImageIO.write(img, "png", it) }
    }

    @Test
    fun `preloads all referenced assets into cache`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        writePng(bundled.resolve("a.png"))
        writePng(user.resolve("b.png"))
        val lib = AssetLibrary(bundled, user)
        lib.scan()
        val cache = ImageCache(maxEntries = 16)

        runBlocking {
            ImagePreloader(lib, cache).preload(
                listOf(AssetPath.Bundled("a.png"), AssetPath.User("b.png"))
            )
        }

        assertNotNull(cache.get(AssetPath.Bundled("a.png")))
        assertNotNull(cache.get(AssetPath.User("b.png")))
        assertEquals(2, cache.size())
    }
}
