package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import java.awt.image.BufferedImage
import java.lang.ref.SoftReference
import java.util.UUID

class ThumbnailRenderer(private val tokenRenderer: TokenRenderer) {

    private sealed interface Key {
        val tokenId: UUID
        val sizePx: Int
        data class TokenKey(override val tokenId: UUID, override val sizePx: Int) : Key
        data class LayerKey(override val tokenId: UUID, val layerId: UUID, override val sizePx: Int) : Key
    }

    private val cache: MutableMap<Key, SoftReference<BufferedImage>> = mutableMapOf()

    @Synchronized
    fun tokenThumbnail(token: Token, sizePx: Int): BufferedImage {
        val key = Key.TokenKey(token.id, sizePx)
        cache[key]?.get()?.let { return it }
        val img = tokenRenderer.render(token, sizePx)
        cache[key] = SoftReference(img)
        return img
    }

    @Synchronized
    fun layerThumbnail(token: Token, layer: Layer, sizePx: Int): BufferedImage {
        val key = Key.LayerKey(token.id, layer.id, sizePx)
        cache[key]?.get()?.let { return it }
        val one = Token(UUID.randomUUID(), token.kind).apply { addLayer(layer) }
        val img = tokenRenderer.render(one, sizePx)
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
