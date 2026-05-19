package net.rafkos.neuroshima.editor.app

import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.command.CommandHistory
import net.rafkos.neuroshima.editor.i18n.LocaleService
import net.rafkos.neuroshima.editor.model.ModelEvent
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.prefs.PrefsStore
import net.rafkos.neuroshima.editor.prefs.UserPreferences
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.ThumbnailRenderer
import net.rafkos.neuroshima.editor.render.TokenRenderer
import net.rafkos.neuroshima.editor.ui.ViewState
import net.rafkos.neuroshima.editor.ui.preview.PreviewKey
import net.rafkos.neuroshima.editor.ui.preview.PreviewService
import java.nio.file.Path
import java.util.Locale

class AppContext(
    bundledRoot: Path,
    userRoot: Path,
    prefsFile: Path,
) {
    val locale: LocaleService = LocaleService(Locale.getDefault())
    val library: AssetLibrary = AssetLibrary(bundledRoot, userRoot).also { it.scan() }
    val imageCache: ImageCache = ImageCache(maxEntries = 256)
    val processedCache: ProcessedLayerCache = ProcessedLayerCache(maxEntries = 256)
    val history: CommandHistory = CommandHistory()
    val prefsStore: PrefsStore = PrefsStore(prefsFile)
    val viewState: ViewState = ViewState()
    val tokenRenderer: TokenRenderer = TokenRenderer(imageCache, processedCache)
    val thumbnails: ThumbnailRenderer = ThumbnailRenderer(tokenRenderer)

    var canvasMapper: net.rafkos.neuroshima.editor.ui.canvas.CanvasMapper? = null

    var bag: TokenBag = TokenBag()
        private set
    var dirty: Boolean = false
        private set
    var currentFile: Path? = null
        private set

    val previewService: PreviewService = PreviewService(bag, tokenRenderer)

    private val persistentBagListeners: MutableList<(ModelEvent) -> Unit> = mutableListOf()
    private val bagReplacedListeners: MutableList<() -> Unit> = mutableListOf()

    fun addBagListener(l: (ModelEvent) -> Unit) {
        bag.addListener(l)
        persistentBagListeners += l
    }

    fun addBagReplacedListener(l: () -> Unit) { bagReplacedListeners += l }

    init {
        val prefs = prefsStore.load()
        viewState.setCollectionThumbSize(prefs.collectionThumbSize)
        viewState.setLayersThumbSize(prefs.layersThumbSize)
        viewState.setAssetsThumbSize(prefs.assetsThumbSize)
        bag.addListener { dirty = true }
        installInvalidationListener(bag)
    }

    fun replaceBag(newBag: TokenBag, file: Path?) {
        bag = newBag.also { b ->
            b.addListener { dirty = true }
            for (l in persistentBagListeners) b.addListener(l)
        }
        currentFile = file
        dirty = false
        history.clear()
        viewState.setActiveToken(bag.tokens.firstOrNull()?.id)
        installInvalidationListener(bag)
        bagReplacedListeners.forEach { it() }
    }

    private fun installInvalidationListener(b: TokenBag) {
        b.addListener { event ->
            if (event is ModelEvent.TokenRemoved) {
                if (viewState.activeTokenId == event.tokenId) {
                    viewState.setActiveToken(bag.tokens.firstOrNull()?.id)
                }
                return@addListener
            }
            val touchedToken: java.util.UUID? = when (event) {
                is ModelEvent.LayerAdded -> event.tokenId
                is ModelEvent.LayerRemoved -> event.tokenId
                is ModelEvent.LayerReordered -> event.tokenId
                is ModelEvent.LayerPropsChanged -> event.tokenId
                else -> null
            }
            if (touchedToken != null) {
                thumbnails.invalidateToken(touchedToken)
                previewService.invalidate(PreviewKey.TokenSnapshot(touchedToken))
                if (viewState.activeTokenId != touchedToken) viewState.setActiveToken(touchedToken)
            }
        }
    }

    fun shutdown() {
        previewService.shutdown()
    }

    fun markClean() { dirty = false }

    fun savePrefs() {
        prefsStore.save(
            UserPreferences(
                collectionThumbSize = viewState.collectionThumbSize,
                layersThumbSize = viewState.layersThumbSize,
                assetsThumbSize = viewState.assetsThumbSize,
                lastOpenedBag = currentFile?.toString(),
            )
        )
    }
}
