package net.rafkos.neuroshima.editor.app

import kotlinx.coroutines.runBlocking
import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.persistence.BagOpener
import net.rafkos.neuroshima.editor.persistence.JsonBagStore
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.TokenRenderer
import net.rafkos.neuroshima.editor.util.logger
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

private val log = logger<Main>()

private class Main

fun main() {
    val tmp = Files.createTempDirectory("nh-editor-smoke")
    val bundled = tmp.resolve("bundled")
    val user = tmp.resolve("user")
    writeSolidPng(bundled.resolve("bg.png"), Color.ORANGE)

    val library = AssetLibrary(bundled, user).also { it.scan() }

    val bag = TokenBag().apply { name = "smoke" }
    val t = Token.createUnit()
    t.addLayer(Layer.create(AssetPath.Bundled("bg.png")))
    bag.addToken(t)

    val box = tmp.resolve("smoke.box")
    JsonBagStore(assetResolver = library::assetExists).save(bag, box)

    val cache = ImageCache(16)
    val loaded = runBlocking { BagOpener(library, cache).open(box) }
    val rendered = TokenRenderer(cache, ProcessedLayerCache(16))
        .render(loaded.tokens.first(), sizePx = 256)

    val out = tmp.resolve("smoke.png")
    Files.newOutputStream(out).use { ImageIO.write(rendered, "png", it) }
    log.info("Smoke render written to {}", out)
    println("Smoke render: $out")
}

private fun writeSolidPng(p: Path, color: Color, w: Int = 100, h: Int = 100) {
    Files.createDirectories(p.parent)
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.color = color
    g.fillRect(0, 0, w, h)
    g.dispose()
    Files.newOutputStream(p).use { ImageIO.write(img, "png", it) }
}
