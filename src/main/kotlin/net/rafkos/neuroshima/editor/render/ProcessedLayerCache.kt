package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.image.BufferedImage
import java.lang.ref.SoftReference

class ProcessedLayerCache(private val maxEntries: Int) {

    data class Key(val assetPath: AssetPath, val props: LayerProperties)

    private val map = object : LinkedHashMap<Key, SoftReference<BufferedImage>>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Key, SoftReference<BufferedImage>>
        ): Boolean = size > maxEntries
    }

    @Synchronized
    fun put(key: Key, image: BufferedImage) {
        map[key] = SoftReference(image)
    }

    @Synchronized
    fun get(key: Key): BufferedImage? {
        val ref = map[key] ?: return null
        val img = ref.get()
        if (img == null) map.remove(key)
        return img
    }
}
