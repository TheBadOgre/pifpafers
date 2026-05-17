package net.rafkos.neuroshima.editor.persistence

import kotlinx.coroutines.runBlocking
import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class BagOpenerTest {

    private fun writePng(p: Path) {
        Files.createDirectories(p.parent)
        val img = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        Files.newOutputStream(p).use { ImageIO.write(img, "png", it) }
    }

    @Test
    fun `open succeeds, returns bag, preloads referenced assets`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        writePng(bundled.resolve("a.png"))
        writePng(user.resolve("b.png"))
        val lib = AssetLibrary(bundled, user).also { it.scan() }

        val bag = TokenBag().apply { name = "test" }
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("a.png")))
        t.addLayer(Layer.create(AssetPath.User("b.png")))
        bag.addToken(t)
        val file = tmp.resolve("army.box")
        JsonBagStore(assetResolver = { lib.assetExists(it) }).save(bag, file)

        val cache = ImageCache(16)
        val opener = BagOpener(library = lib, imageCache = cache)
        val loaded = runBlocking { opener.open(file) }
        assertEquals("test", loaded.name)
        assertEquals(1, loaded.tokens.size)
        assertEquals(2, loaded.tokens.first().layers.size)
        assertNotNull(cache.get(AssetPath.Bundled("a.png")))
        assertNotNull(cache.get(AssetPath.User("b.png")))
    }

    @Test
    fun `open throws MissingAssetsException for unknown asset`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        Files.createDirectories(bundled)
        val lib = AssetLibrary(bundled, tmp.resolve("user")).also { it.scan() }

        val bag = TokenBag()
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("missing.png")))
        bag.addToken(t)
        val file = tmp.resolve("army.box")
        JsonBagStore(assetResolver = { true }).save(bag, file)

        val opener = BagOpener(library = lib, imageCache = ImageCache(4))
        assertThrows(MissingAssetsException::class.java) { runBlocking { opener.open(file) } }
    }
}
