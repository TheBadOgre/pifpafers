package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.Token
import java.awt.image.BufferedImage
import java.lang.ref.SoftReference
import java.util.UUID

class ThumbnailRenderer(private val tokenRenderer: TokenRenderer) {

    private data class Key(val tokenId: UUID, val sizePx: Int)

    private val cache: MutableMap<Key, SoftReference<BufferedImage>> = mutableMapOf()

    @Synchronized
    fun tokenThumbnail(token: Token, sizePx: Int): BufferedImage {
        val key = Key(token.id, sizePx)
        cache[key]?.get()?.let { return it }
        val img = tokenRenderer.render(token, sizePx)
        cache[key] = SoftReference(img)
        return img
    }

    @Synchronized
    fun invalidateToken(tokenId: UUID) {
        cache.keys.removeAll { it.tokenId == tokenId }
    }

    @Synchronized
    fun clear() = cache.clear()
}
