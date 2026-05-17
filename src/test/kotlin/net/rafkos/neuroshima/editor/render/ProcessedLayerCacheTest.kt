package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.LayerProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage

class ProcessedLayerCacheTest {

    private fun img() = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)

    @Test
    fun `same key returns cached instance`() {
        val cache = ProcessedLayerCache(maxEntries = 4)
        val key1 = ProcessedLayerCache.Key(AssetPath.Bundled("a.png"), LayerProperties(opacity = 0.5f))
        val v = img()
        cache.put(key1, v)
        val key2 = ProcessedLayerCache.Key(AssetPath.Bundled("a.png"), LayerProperties(opacity = 0.5f))
        assertSame(v, cache.get(key2))
    }

    @Test
    fun `different props miss`() {
        val cache = ProcessedLayerCache(maxEntries = 4)
        cache.put(
            ProcessedLayerCache.Key(AssetPath.Bundled("a.png"), LayerProperties(opacity = 0.5f)),
            img(),
        )
        val miss = cache.get(
            ProcessedLayerCache.Key(AssetPath.Bundled("a.png"), LayerProperties(opacity = 0.6f))
        )
        assertEquals(null, miss)
    }
}
