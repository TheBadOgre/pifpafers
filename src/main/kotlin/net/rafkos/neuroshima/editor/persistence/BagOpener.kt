package net.rafkos.neuroshima.editor.persistence

import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.assets.ImagePreloader
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import java.nio.file.Path

class BagOpener(
    private val library: AssetLibrary,
    imageCache: ImageCache,
) {
    private val store = JsonBagStore(assetResolver = { library.assetExists(it) })
    private val preloader = ImagePreloader(library, imageCache)

    suspend fun open(file: Path): TokenBag {
        val bag = store.load(file)
        val referenced: List<AssetPath> = bag.tokens.flatMap { t ->
            TokenSide.entries.flatMap { side -> t.layers(side).map { it.assetPath } }
        }.distinct()
        preloader.preload(referenced)
        return bag
    }
}
