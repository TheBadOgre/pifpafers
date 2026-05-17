package net.rafkos.neuroshima.editor.integration

import kotlinx.coroutines.runBlocking
import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.persistence.BagOpener
import net.rafkos.neuroshima.editor.persistence.JsonBagStore
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.TokenRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs

class RoundTripRenderTest {

    private fun writeSolidPng(p: Path, color: Color, w: Int = 100, h: Int = 100) {
        Files.createDirectories(p.parent)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, w, h)
        g.dispose()
        Files.newOutputStream(p).use { ImageIO.write(img, "png", it) }
    }

    private fun pixelDelta(a: BufferedImage, b: BufferedImage): Int {
        require(a.width == b.width && a.height == b.height)
        var max = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val pa = a.getRGB(x, y); val pb = b.getRGB(x, y)
            for (shift in intArrayOf(0, 8, 16, 24)) {
                val d = abs(((pa ushr shift) and 0xff) - ((pb ushr shift) and 0xff))
                if (d > max) max = d
            }
        }
        return max
    }

    @Test
    fun `save, reopen, render - two passes match within tolerance`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        writeSolidPng(bundled.resolve("bg.png"), Color.YELLOW, w = 1044, h = 902)
        writeSolidPng(user.resolve("dot.png"), Color.BLUE, w = 40, h = 40)
        val library = AssetLibrary(bundled, user).also { it.scan() }

        val bag = TokenBag().apply { name = "sample" }
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("bg.png")))
        t.addLayer(
            Layer.create(
                AssetPath.User("dot.png"),
                LayerProperties(offsetX = 20, offsetY = -10, opacity = 0.8f),
            )
        )
        bag.addToken(t)

        val box = tmp.resolve("sample.box")
        JsonBagStore(assetResolver = { library.assetExists(it) }).save(bag, box)

        val cache1 = ImageCache(16)
        val opener1 = BagOpener(library, cache1)
        val loaded1 = runBlocking { opener1.open(box) }
        val render1 = TokenRenderer(cache1, ProcessedLayerCache(16))
            .render(loaded1.tokens.first(), sizePx = 100)

        val cache2 = ImageCache(16)
        val opener2 = BagOpener(library, cache2)
        val loaded2 = runBlocking { opener2.open(box) }
        val render2 = TokenRenderer(cache2, ProcessedLayerCache(16))
            .render(loaded2.tokens.first(), sizePx = 100)

        assertEquals(0, pixelDelta(render1, render2))

        // sample a point clearly in the yellow background (not under the blue dot)
        // inner hex crop W=880; fit=100/880≈0.1136. dot (40x40) at offsetX=20,offsetY=-10 lands
        // at screen ~x=[50,55], y=[47,51]. x=25 maps to logical ≈(302,451) — safely in yellow bg.
        val center = render1.getRGB(25, 50)
        val alpha = (center ushr 24) and 0xff
        val red = (center ushr 16) and 0xff
        val green = (center ushr 8) and 0xff
        assertTrue(alpha > 200, "alpha=$alpha")
        assertTrue(red > 200, "r=$red")
        assertTrue(green > 200, "g=$green")
    }
}
