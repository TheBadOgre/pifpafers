package net.rafkos.neuroshima.editor.ui.preview

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.TokenRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PreviewServiceTest {

    private fun solid(c: Color, w: Int = 16, h: Int = 16): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics(); g.color = c; g.fillRect(0, 0, w, h); g.dispose()
        return img
    }

    @Test
    fun `coalesces repeated invalidations into one render within debounce window`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png"); cache.put(asset, solid(Color.RED))
        val bag = TokenBag()
        val token = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        bag.addToken(token)
        val service = PreviewService(bag, TokenRenderer(cache, ProcessedLayerCache(8)),
            debounceMillis = 80L)

        val calls = AtomicInteger()
        val done = CountDownLatch(1)
        service.subscribe(PreviewKey.TokenSnapshot(token.id), 64) {
            calls.incrementAndGet(); done.countDown()
        }
        repeat(8) { service.invalidate(PreviewKey.TokenSnapshot(token.id)) }

        assertTrue(done.await(2, TimeUnit.SECONDS), "render never fired")
        Thread.sleep(200)
        assertEquals(1, calls.get(), "exactly one delivery for one coalesced burst")
        service.shutdown()
    }

    @Test
    fun `stale results dropped when later invalidation supersedes`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png"); cache.put(asset, solid(Color.RED))
        val bag = TokenBag()
        val token = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        bag.addToken(token)
        val service = PreviewService(bag, TokenRenderer(cache, ProcessedLayerCache(8)),
            debounceMillis = 60L)

        val received = mutableListOf<BufferedImage>()
        val latch = CountDownLatch(1)
        service.subscribe(PreviewKey.TokenSnapshot(token.id), 64) {
            synchronized(received) { received += it }
            latch.countDown()
        }
        service.invalidate(PreviewKey.TokenSnapshot(token.id))
        Thread.sleep(20)
        service.invalidate(PreviewKey.TokenSnapshot(token.id))

        assertTrue(latch.await(2, TimeUnit.SECONDS), "no result")
        Thread.sleep(200)
        synchronized(received) { assertEquals(1, received.size) }
        service.shutdown()
    }
}
