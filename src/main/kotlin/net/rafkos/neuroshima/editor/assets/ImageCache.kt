package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath
import java.awt.image.BufferedImage
import java.lang.ref.SoftReference

class ImageCache(private val maxEntries: Int) {

    private val map: LinkedHashMap<AssetPath, SoftReference<BufferedImage>> =
        object : LinkedHashMap<AssetPath, SoftReference<BufferedImage>>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<AssetPath, SoftReference<BufferedImage>>
            ): Boolean = size > maxEntries
        }

    @Synchronized
    fun put(key: AssetPath, image: BufferedImage) {
        map[key] = SoftReference(image)
    }

    @Synchronized
    fun get(key: AssetPath): BufferedImage? {
        val ref = map[key] ?: return null
        val img = ref.get()
        if (img == null) map.remove(key)
        return img
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun clear() = map.clear()
}
