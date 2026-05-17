package net.rafkos.neuroshima.editor.assets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.rafkos.neuroshima.editor.model.AssetPath
import javax.imageio.ImageIO

class ImagePreloader(
    private val library: AssetLibrary,
    private val cache: ImageCache,
) {
    suspend fun preload(paths: Collection<AssetPath>) = coroutineScope {
        paths.distinct().map { ap ->
            async(Dispatchers.IO) {
                val file = library.resolveFile(ap) ?: return@async
                val img = file.toFile().inputStream().use { ImageIO.read(it) }
                if (img != null) cache.put(ap, img)
            }
        }.awaitAll()
    }
}
