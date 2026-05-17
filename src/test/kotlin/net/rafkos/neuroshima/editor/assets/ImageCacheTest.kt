package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ImageCacheTest {

    private fun img(): BufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    @Test
    fun `put and get returns same instance`() {
        val cache = ImageCache(maxEntries = 4)
        val key = AssetPath.Bundled("a.png")
        val i = img()
        cache.put(key, i)
        assertSame(i, cache.get(key))
    }

    @Test
    fun `get on empty cache returns null`() {
        assertNull(ImageCache(2).get(AssetPath.Bundled("x.png")))
    }

    @Test
    fun `LRU evicts the least recently used`() {
        val cache = ImageCache(maxEntries = 2)
        val a = AssetPath.Bundled("a.png")
        val b = AssetPath.Bundled("b.png")
        val c = AssetPath.Bundled("c.png")
        cache.put(a, img())
        cache.put(b, img())
        cache.get(a) // a is now most-recent
        cache.put(c, img()) // should evict b
        assertNotNull(cache.get(a))
        assertNull(cache.get(b))
        assertNotNull(cache.get(c))
    }

    @Test
    fun `size reports current entry count`() {
        val cache = ImageCache(maxEntries = 4)
        assertEquals(0, cache.size())
        cache.put(AssetPath.Bundled("a.png"), img())
        cache.put(AssetPath.Bundled("b.png"), img())
        assertEquals(2, cache.size())
    }

    @Test
    fun `clear removes all entries`() {
        val cache = ImageCache(maxEntries = 4)
        cache.put(AssetPath.Bundled("a.png"), img())
        cache.clear()
        assertEquals(0, cache.size())
    }
}
