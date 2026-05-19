package net.rafkos.neuroshima.editor.ui.preview

import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.render.TokenRenderer
import java.awt.image.BufferedImage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

sealed interface PreviewKey {
    val tokenId: UUID
    data class TokenSnapshot(override val tokenId: UUID) : PreviewKey
}

class PreviewService(
    private val bag: TokenBag,
    private val tokenRenderer: TokenRenderer,
    private val debounceMillis: Long = 300L,
) {
    private data class Subscription(
        val sizePx: Int,
        val onReady: (BufferedImage) -> Unit,
        val version: AtomicLong = AtomicLong(0),
        @Volatile var pending: ScheduledFuture<*>? = null,
    )

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "preview-service").apply { isDaemon = true }
    }
    private val subs: MutableMap<PreviewKey, Subscription> = ConcurrentHashMap()

    fun subscribe(key: PreviewKey, sizePx: Int, onReady: (BufferedImage) -> Unit) {
        subs[key] = Subscription(sizePx, onReady)
        invalidate(key)
    }

    fun unsubscribe(key: PreviewKey) {
        val s = subs.remove(key) ?: return
        s.pending?.cancel(false)
    }

    fun invalidate(key: PreviewKey) {
        val s = subs[key] ?: return
        val myVersion = s.version.incrementAndGet()
        s.pending?.cancel(false)
        s.pending = executor.schedule({ render(key, s, myVersion) }, debounceMillis, TimeUnit.MILLISECONDS)
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun render(key: PreviewKey, s: Subscription, myVersion: Long) {
        if (s.version.get() != myVersion) return
        val token = bag.findToken(key.tokenId) ?: return
        val img = tokenRenderer.renderDual(token, s.sizePx)
        if (s.version.get() != myVersion) return
        SwingUtilities.invokeLater { s.onReady(img) }
    }
}
