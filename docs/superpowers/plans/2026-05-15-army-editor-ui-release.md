# Neuroshima Hex Army Editor — Plan B: UI + Release

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the full Swing UI on top of the Plan A headless core, deliver the per-OS jpackage installers, and ship the working editor that matches the source UI mockup.

**Architecture:** Single `JFrame` hosting nested `JSplitPane`s per the mockup layout. An `AppContext` owns the singletons (`TokenBag`, `AssetLibrary`, `ImageCache`, `ProcessedLayerCache`, `CommandHistory`, `LocaleService`, `UserPreferences`, plus a `ViewState` for selection, active tool, and the three size sliders). Panels subscribe to `ModelEvent`s from the bag and to a small `ViewStateListener` for selection / zoom / size changes. All mutations go through `CommandHistory.execute(...)`. Tools install themselves as the canvas mouse handler. Rendering reuses Plan A's `TokenRenderer` for thumbnails and a slimmer per-paint draw inside `TokenCanvasPanel`.

**Tech Stack:** Same as Plan A (Kotlin 2.3.10, JDK 21, Swing) plus jpackage for distribution.

**Reference spec:** `docs/superpowers/specs/2026-05-15-neuroshima-hex-army-editor-design.md`.
**Predecessor plan:** `docs/superpowers/plans/2026-05-15-army-editor-headless-core.md` — must be complete before starting Plan B.

**Conventions used throughout this plan:**
- Source root: `src/main/kotlin/net/rafkos/neuroshima/editor/`.
- Manual smoke verification appears as an explicit step ("run `./gradlew run`, do X, observe Y") since the spec opts out of Swing UI tests.
- After each task, run `./gradlew test` (architecture test still applies). Commit when green.

---

## Task 1: `UserPreferences` with JSON persistence

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/prefs/UserPreferences.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/prefs/PrefsStore.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/prefs/PrefsStoreTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/prefs/PrefsStoreTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PrefsStoreTest {

    @Test
    fun `save and load round-trip preserves values`(@TempDir tmp: Path) {
        val file = tmp.resolve("prefs.json")
        val store = PrefsStore(file)
        val original = UserPreferences(
            collectionThumbSize = 128,
            layersThumbSize = 96,
            assetsThumbSize = 80,
            lastOpenedBag = "C:/Users/me/army.box",
        )
        store.save(original)
        val loaded = store.load()
        assertEquals(original, loaded)
    }

    @Test
    fun `load missing file returns defaults`(@TempDir tmp: Path) {
        val store = PrefsStore(tmp.resolve("none.json"))
        assertEquals(UserPreferences(), store.load())
    }

    @Test
    fun `defaults are reasonable thumbnail sizes`() {
        val p = UserPreferences()
        assertEquals(96, p.collectionThumbSize)
        assertEquals(64, p.layersThumbSize)
        assertEquals(64, p.assetsThumbSize)
        assertEquals(null, p.lastOpenedBag)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.prefs.PrefsStoreTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `UserPreferences`**

`src/main/kotlin/net/rafkos/neuroshima/editor/prefs/UserPreferences.kt`:

```kotlin
package net.rafkos.neuroshima.editor.prefs

import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val collectionThumbSize: Int = 96,
    val layersThumbSize: Int = 64,
    val assetsThumbSize: Int = 64,
    val lastOpenedBag: String? = null,
)
```

- [ ] **Step 4: Implement `PrefsStore`**

`src/main/kotlin/net/rafkos/neuroshima/editor/prefs/PrefsStore.kt`:

```kotlin
package net.rafkos.neuroshima.editor.prefs

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class PrefsStore(private val file: Path) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun load(): UserPreferences {
        if (!Files.isRegularFile(file)) return UserPreferences()
        return json.decodeFromString(UserPreferences.serializer(), Files.readString(file))
    }

    fun save(prefs: UserPreferences) {
        Files.createDirectories(file.parent)
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(tmp, json.encodeToString(UserPreferences.serializer(), prefs))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.prefs.PrefsStoreTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/prefs \
        src/test/kotlin/net/rafkos/neuroshima/editor/prefs
git commit -m "feat(prefs): add UserPreferences with JSON round-trip"
```

---

## Task 2: `ViewState` (selection + tool + zoom + slider sizes)

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/ViewState.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/ui/ViewStateTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/ui/ViewStateTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ViewStateTest {

    @Test
    fun `setActiveToken fires single listener notification`() {
        val vs = ViewState()
        var fires = 0
        vs.addListener { fires++ }
        val id = UUID.randomUUID()
        vs.setActiveToken(id)
        assertEquals(id, vs.activeTokenId)
        assertEquals(1, fires)
    }

    @Test
    fun `setting same active token does not re-fire`() {
        val vs = ViewState()
        val id = UUID.randomUUID()
        vs.setActiveToken(id)
        var fires = 0
        vs.addListener { fires++ }
        vs.setActiveToken(id)
        assertEquals(0, fires)
    }

    @Test
    fun `selected layers default to empty set`() {
        assertEquals(emptySet<UUID>(), ViewState().selectedLayers)
    }

    @Test
    fun `selectLayer adds; deselectLayer removes; clearSelection empties`() {
        val vs = ViewState()
        val a = UUID.randomUUID(); val b = UUID.randomUUID()
        vs.selectLayer(a)
        vs.selectLayer(b)
        assertEquals(setOf(a, b), vs.selectedLayers)
        vs.deselectLayer(a)
        assertEquals(setOf(b), vs.selectedLayers)
        vs.clearSelection()
        assertEquals(emptySet<UUID>(), vs.selectedLayers)
    }

    @Test
    fun `setActiveTool fires`() {
        val vs = ViewState()
        var fires = 0
        vs.addListener { fires++ }
        vs.setActiveTool(ToolId.MOVE)
        assertEquals(ToolId.MOVE, vs.activeTool)
        assertEquals(1, fires)
    }

    @Test
    fun `setZoom clamps to bounds`() {
        val vs = ViewState()
        vs.setZoom(100f)
        assertEquals(8f, vs.zoom)
        vs.setZoom(0.001f)
        assertEquals(0.25f, vs.zoom)
        vs.setZoom(2f)
        assertEquals(2f, vs.zoom)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.ui.ViewStateTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `ToolId` + `ViewState`**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/ViewState.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui

import java.util.UUID

enum class ToolId { SELECT, MOVE, ROTATE, SCALE, OPACITY, COLORIZE }

class ViewState {

    var activeTokenId: UUID? = null
        private set

    private val _selectedLayers: MutableSet<UUID> = linkedSetOf()
    val selectedLayers: Set<UUID> get() = _selectedLayers

    var activeTool: ToolId = ToolId.SELECT
        private set

    var zoom: Float = 1f
        private set

    var showOverlay: Boolean = false
        private set

    var collectionThumbSize: Int = 96
        private set
    var layersThumbSize: Int = 64
        private set
    var assetsThumbSize: Int = 64
        private set

    private val listeners: MutableList<() -> Unit> = mutableListOf()

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun fire() { for (l in listeners.toList()) l() }

    fun setActiveToken(id: UUID?) {
        if (activeTokenId == id) return
        activeTokenId = id
        _selectedLayers.clear()
        fire()
    }

    fun selectLayer(id: UUID) {
        if (_selectedLayers.add(id)) fire()
    }

    fun deselectLayer(id: UUID) {
        if (_selectedLayers.remove(id)) fire()
    }

    fun replaceSelection(ids: Collection<UUID>) {
        _selectedLayers.clear()
        _selectedLayers += ids
        fire()
    }

    fun clearSelection() {
        if (_selectedLayers.isNotEmpty()) {
            _selectedLayers.clear()
            fire()
        }
    }

    fun setActiveTool(t: ToolId) {
        if (activeTool == t) return
        activeTool = t
        fire()
    }

    fun setZoom(z: Float) {
        val clamped = z.coerceIn(0.25f, 8f)
        if (clamped == zoom) return
        zoom = clamped
        fire()
    }

    fun setShowOverlay(b: Boolean) {
        if (showOverlay == b) return
        showOverlay = b
        fire()
    }

    fun setCollectionThumbSize(px: Int) {
        val clamped = px.coerceIn(48, 192)
        if (clamped == collectionThumbSize) return
        collectionThumbSize = clamped
        fire()
    }

    fun setLayersThumbSize(px: Int) {
        val clamped = px.coerceIn(48, 192)
        if (clamped == layersThumbSize) return
        layersThumbSize = clamped
        fire()
    }

    fun setAssetsThumbSize(px: Int) {
        val clamped = px.coerceIn(48, 192)
        if (clamped == assetsThumbSize) return
        assetsThumbSize = clamped
        fire()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.ui.ViewStateTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/ViewState.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/ui/ViewStateTest.kt
git commit -m "feat(ui): add ViewState for selection, tool, zoom, slider sizes"
```

---

## Task 3: `AppContext` wires the singletons

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt`

- [ ] **Step 1: Implement `AppContext`**

`src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt`:

```kotlin
package net.rafkos.neuroshima.editor.app

import net.rafkos.neuroshima.editor.assets.AssetLibrary
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.command.CommandHistory
import net.rafkos.neuroshima.editor.i18n.LocaleService
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.prefs.PrefsStore
import net.rafkos.neuroshima.editor.prefs.UserPreferences
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.TokenRenderer
import net.rafkos.neuroshima.editor.ui.ViewState
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

    var bag: TokenBag = TokenBag()
        private set
    var dirty: Boolean = false
        private set
    var currentFile: Path? = null
        private set

    init {
        val prefs = prefsStore.load()
        viewState.setCollectionThumbSize(prefs.collectionThumbSize)
        viewState.setLayersThumbSize(prefs.layersThumbSize)
        viewState.setAssetsThumbSize(prefs.assetsThumbSize)
        bag.addListener { dirty = true }
    }

    fun replaceBag(newBag: TokenBag, file: Path?) {
        bag = newBag.also { b -> b.addListener { dirty = true } }
        currentFile = file
        dirty = false
        history.clear()
        viewState.setActiveToken(bag.tokens.firstOrNull()?.id)
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
```

- [ ] **Step 2: Confirm build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt
git commit -m "feat(app): add AppContext wiring singletons"
```

---

## Task 4: `ThumbnailRenderer` service

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRenderer.kt`
- Create: `src/test/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRendererTest.kt`

- [ ] **Step 1: Write the failing test**

`src/test/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRendererTest.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage

class ThumbnailRendererTest {

    private fun solid(c: Color, w: Int = 50, h: Int = 50) = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also {
        val g = it.createGraphics(); g.color = c; g.fillRect(0, 0, w, h); g.dispose()
    }

    @Test
    fun `same token at same size returns cached instance`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png")
        cache.put(asset, solid(Color.RED))
        val tok = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        val renderer = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(8)))
        val first = renderer.tokenThumbnail(tok, 64)
        val second = renderer.tokenThumbnail(tok, 64)
        assertSame(first, second)
    }

    @Test
    fun `same token at different size returns distinct instances of correct size`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png")
        cache.put(asset, solid(Color.RED))
        val tok = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        val renderer = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(8)))
        val small = renderer.tokenThumbnail(tok, 64)
        val big = renderer.tokenThumbnail(tok, 128)
        assertEquals(64, small.width); assertEquals(64, small.height)
        assertEquals(128, big.width); assertEquals(128, big.height)
    }

    @Test
    fun `invalidate forces re-render`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png")
        cache.put(asset, solid(Color.RED))
        val tok = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        val renderer = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(8)))
        val first = renderer.tokenThumbnail(tok, 64)
        renderer.invalidateToken(tok.id)
        val second = renderer.tokenThumbnail(tok, 64)
        // Different cached instance after invalidate
        assert(first !== second) { "expected new instance after invalidate" }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.ThumbnailRendererTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `ThumbnailRenderer`**

`src/main/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRenderer.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.render.ThumbnailRendererTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRenderer.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRendererTest.kt
git commit -m "feat(render): add ThumbnailRenderer with per-token+size cache"
```

---

## Task 5: Main window scaffold (empty panels, mockup layout)

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/app/Main.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`
- Create: `src/main/resources/i18n/messages.properties` (extend with UI keys)

- [ ] **Step 1: Extend the i18n bundle with UI strings**

Replace `src/main/resources/i18n/messages.properties` with:

```
app.title=Neuroshima Hex Army Editor
panel.tokens=Tokens collection
panel.layers=Current token layers
panel.properties=Layer properties
panel.assets=Assets library
menu.file=File
menu.file.save=Save
menu.file.saveAs=Save As...
menu.edit=Edit
menu.edit.undo=Undo
menu.edit.redo=Redo
menu.file.print=Print...
button.add.unit=+ Unit
button.add.modifier=+ Modifier
button.refresh=Refresh content
button.show.overlay=Show overlay
status.tokens={0} tokens
status.lastSaved=last saved: {0}
status.loadedFile=loaded file: {0}
error.missing.assets=Missing assets:
error.schema.version=Unsupported file version: {0}
prop.offsetX=offset X
prop.offsetY=offset Y
prop.rotation=rotation
prop.scale=scale
prop.opacity=opacity
prop.hue=hue
prop.saturation=saturation
prop.brightness=brightness
```

Update `src/main/resources/i18n/messages_pl.properties` similarly with Polish strings (use ASCII; full diacritics can be added later via Unicode escapes).

- [ ] **Step 2: Implement `MainFrame` scaffolding**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui

import net.rafkos.neuroshima.editor.app.AppContext
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.WindowConstants

class MainFrame(private val ctx: AppContext) : JFrame() {

    val toolPalette: JPanel = JPanel().apply { preferredSize = Dimension(48, 0) }
    val tokensPanel: JPanel = JPanel().apply { preferredSize = Dimension(220, 0) }
    val layersPanel: JPanel = JPanel().apply { preferredSize = Dimension(220, 200) }
    val propertiesPanel: JPanel = JPanel().apply { preferredSize = Dimension(220, 200) }
    val canvasPanel: JPanel = JPanel()
    val assetsPanel: JPanel = JPanel().apply { preferredSize = Dimension(0, 220) }
    val statusBar: JPanel = JPanel().apply { preferredSize = Dimension(0, 24) }

    init {
        title = ctx.locale.t("app.title")
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        layout = BorderLayout()

        val rightStack = JSplitPane(JSplitPane.VERTICAL_SPLIT, layersPanel, propertiesPanel).apply {
            resizeWeight = 0.5
            isContinuousLayout = true
        }
        val centerWithRight = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvasPanel, rightStack).apply {
            resizeWeight = 0.75
            isContinuousLayout = true
        }
        val mainWithLeft = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tokensPanel, centerWithRight).apply {
            resizeWeight = 0.15
            isContinuousLayout = true
        }
        val top = JPanel(BorderLayout()).apply {
            add(toolPalette, BorderLayout.WEST)
            add(mainWithLeft, BorderLayout.CENTER)
        }
        val full = JSplitPane(JSplitPane.VERTICAL_SPLIT, top, assetsPanel).apply {
            resizeWeight = 0.7
            isContinuousLayout = true
        }

        add(full, BorderLayout.CENTER)
        add(statusBar, BorderLayout.SOUTH)
        preferredSize = Dimension(1280, 800)
        pack()
        setLocationRelativeTo(null)
    }
}
```

- [ ] **Step 3: Rewrite `Main.kt` to launch the frame**

Replace `src/main/kotlin/net/rafkos/neuroshima/editor/app/Main.kt`:

```kotlin
package net.rafkos.neuroshima.editor.app

import net.rafkos.neuroshima.editor.ui.MainFrame
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.SwingUtilities

fun main() {
    val appDir = System.getProperty("app.dir")?.let { Paths.get(it) }
        ?: Paths.get(System.getProperty("user.dir"))
    val bundledRoot: Path = appDir.resolve("assets")
    val userRoot: Path = Paths.get(System.getProperty("user.home"))
        .resolve(".neuroshima-editor").resolve("content")
    val prefsFile: Path = Paths.get(System.getProperty("user.home"))
        .resolve(".neuroshima-editor").resolve("prefs.json")

    val ctx = AppContext(bundledRoot = bundledRoot, userRoot = userRoot, prefsFile = prefsFile)
    SwingUtilities.invokeLater {
        val frame = MainFrame(ctx)
        frame.isVisible = true
        Runtime.getRuntime().addShutdownHook(Thread { ctx.savePrefs() })
    }
}
```

- [ ] **Step 4: Manual smoke**

Run: `./gradlew run`
Expected: a window with the empty panel layout opens. Close it (window will not close due to `DO_NOTHING_ON_CLOSE`; kill the gradle process with Ctrl+C). Dialog wiring lands in Task 22.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/app/Main.kt \
        src/main/resources/i18n
git commit -m "feat(ui): add MainFrame scaffold with mockup-style split panes"
```

---

## Task 6: `ToolPalettePanel`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/ToolPalettePanel.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Implement the panel**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/ToolPalettePanel.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.ui.ToolId
import net.rafkos.neuroshima.editor.ui.ViewState
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.ButtonGroup
import javax.swing.JPanel
import javax.swing.JToggleButton
import javax.swing.SwingConstants

class ToolPalettePanel(private val viewState: ViewState) : JPanel() {
    init {
        layout = GridLayout(0, 1, 0, 2)
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        val group = ButtonGroup()
        for (id in ToolId.values()) {
            val button = JToggleButton(id.shortLabel(), id == viewState.activeTool).apply {
                horizontalAlignment = SwingConstants.CENTER
                addActionListener { viewState.setActiveTool(id) }
            }
            group.add(button)
            add(button)
        }
        viewState.addListener {
            for (i in 0 until componentCount) {
                val btn = getComponent(i) as JToggleButton
                btn.isSelected = ToolId.values()[i] == viewState.activeTool
            }
        }
    }

    private fun ToolId.shortLabel(): String = when (this) {
        ToolId.SELECT -> "Sel"
        ToolId.MOVE -> "Mov"
        ToolId.ROTATE -> "Rot"
        ToolId.SCALE -> "Scl"
        ToolId.OPACITY -> "Opa"
        ToolId.COLORIZE -> "Col"
    }
}
```

- [ ] **Step 2: Replace the placeholder in `MainFrame`**

In `MainFrame.kt`, replace
```kotlin
val toolPalette: JPanel = JPanel().apply { preferredSize = Dimension(48, 0) }
```
with
```kotlin
val toolPalette: JPanel = ToolPalettePanel(ctx.viewState).apply { preferredSize = Dimension(56, 0) }
```
and add `import net.rafkos.neuroshima.editor.ui.panels.ToolPalettePanel`.

- [ ] **Step 3: Manual smoke**

Run: `./gradlew run`
Expected: Six radio-style buttons stacked on the left; clicking any one selects it exclusively.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/ToolPalettePanel.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): add ToolPalettePanel wired to ViewState"
```

---

## Task 7: `TokensCollectionPanel`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/TokensCollectionPanel.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Implement the panel**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/TokensCollectionPanel.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.AddTokenCommand
import net.rafkos.neuroshima.editor.command.RemoveTokenCommand
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.render.ThumbnailRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider

class TokensCollectionPanel(
    private val ctx: AppContext,
    private val thumbnails: ThumbnailRenderer,
) : JPanel() {

    private val grid = JPanel(GridLayout(0, 2, 4, 4))
    private val slider = JSlider(48, 192, ctx.viewState.collectionThumbSize)

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.tokens"))
        add(JScrollPane(grid), BorderLayout.CENTER)

        val south = JPanel(BorderLayout())
        south.add(slider, BorderLayout.NORTH)
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        buttons.add(JButton(ctx.locale.t("button.add.unit")).apply {
            addActionListener {
                ctx.history.execute(ctx.bag, AddTokenCommand(TokenKind.UNIT, ctx.bag.tokens.size))
            }
        })
        buttons.add(JButton(ctx.locale.t("button.add.modifier")).apply {
            addActionListener {
                ctx.history.execute(ctx.bag, AddTokenCommand(TokenKind.MODIFIER, ctx.bag.tokens.size))
            }
        })
        south.add(buttons, BorderLayout.SOUTH)
        add(south, BorderLayout.SOUTH)

        slider.addChangeListener { ctx.viewState.setCollectionThumbSize(slider.value) }

        ctx.bag.addListener { rebuild() }
        ctx.viewState.addListener { rebuild() }
        rebuild()
    }

    private fun rebuild() {
        grid.removeAll()
        val size = ctx.viewState.collectionThumbSize
        for (token in ctx.bag.tokens) {
            val img = thumbnails.tokenThumbnail(token, size)
            val cell = JPanel(BorderLayout()).apply {
                preferredSize = Dimension(size + 8, size + 8)
                border = BorderFactory.createLineBorder(
                    if (token.id == ctx.viewState.activeTokenId) java.awt.Color.BLUE else java.awt.Color.GRAY,
                    if (token.id == ctx.viewState.activeTokenId) 2 else 1,
                )
            }
            val lbl = JLabel(ImageIcon(img))
            lbl.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button == MouseEvent.BUTTON1) {
                        ctx.viewState.setActiveToken(token.id)
                    } else if (e.button == MouseEvent.BUTTON3) {
                        val ok = JOptionPane.showConfirmDialog(
                            this@TokensCollectionPanel,
                            "Delete token?",
                            "Confirm",
                            JOptionPane.YES_NO_OPTION,
                        )
                        if (ok == JOptionPane.YES_OPTION) {
                            ctx.history.execute(ctx.bag, RemoveTokenCommand(token.id))
                        }
                    }
                }
            })
            cell.add(lbl, BorderLayout.CENTER)
            grid.add(cell)
        }
        grid.revalidate()
        grid.repaint()
    }
}
```

- [ ] **Step 2: Replace the placeholder in `MainFrame`**

In `MainFrame.kt`, replace the `tokensPanel` placeholder with:

```kotlin
val tokensPanel: JPanel = TokensCollectionPanel(ctx, ThumbnailRenderer(ctx.tokenRenderer)).apply {
    preferredSize = Dimension(240, 0)
}
```

And add imports for `TokensCollectionPanel` and `ThumbnailRenderer`. Pass the same `ThumbnailRenderer` instance to other panels in subsequent tasks (hoist it into `AppContext` if multiple panels share it — adjust there).

- [ ] **Step 3: Hoist `ThumbnailRenderer` into `AppContext`**

In `AppContext.kt`, add:

```kotlin
val thumbnails: ThumbnailRenderer = ThumbnailRenderer(tokenRenderer)
```

Update `bag.addListener` block in `init` and in `replaceBag` so that on every `ModelEvent.LayerAdded/LayerRemoved/LayerReordered/LayerPropsChanged` for a token, `thumbnails.invalidateToken(...)` is called. Concretely, register a single listener in `init` and in `replaceBag`:

```kotlin
private fun installInvalidationListener(b: TokenBag) {
    b.addListener { event ->
        when (event) {
            is net.rafkos.neuroshima.editor.model.ModelEvent.LayerAdded -> thumbnails.invalidateToken(event.tokenId)
            is net.rafkos.neuroshima.editor.model.ModelEvent.LayerRemoved -> thumbnails.invalidateToken(event.tokenId)
            is net.rafkos.neuroshima.editor.model.ModelEvent.LayerReordered -> thumbnails.invalidateToken(event.tokenId)
            is net.rafkos.neuroshima.editor.model.ModelEvent.LayerPropsChanged -> thumbnails.invalidateToken(event.tokenId)
            else -> Unit
        }
    }
}
```

Call `installInvalidationListener(bag)` in `init` and at the end of `replaceBag`. Update `TokensCollectionPanel` constructor to receive `ctx.thumbnails` instead of constructing its own.

- [ ] **Step 4: Manual smoke**

Run: `./gradlew run`
Expected: empty grid + "+ Unit" / "+ Modifier" buttons + slider. Click "+ Unit" three times — three thumbnails appear. Right-click a thumbnail → confirm dialog → delete works.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/TokensCollectionPanel.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt
git commit -m "feat(ui): add TokensCollectionPanel with add/remove and slider"
```

---

## Task 8: `LayersPanel`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/LayersPanel.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Implement the panel**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/LayersPanel.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.RemoveLayerCommand
import net.rafkos.neuroshima.editor.command.ReorderLayerCommand
import net.rafkos.neuroshima.editor.model.Token
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.SwingConstants

class LayersPanel(private val ctx: AppContext) : JPanel() {

    private val list = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val slider = JSlider(48, 192, ctx.viewState.layersThumbSize)

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.layers"))
        add(JScrollPane(list), BorderLayout.CENTER)
        add(slider, BorderLayout.SOUTH)
        slider.addChangeListener { ctx.viewState.setLayersThumbSize(slider.value) }

        ctx.bag.addListener { rebuild() }
        ctx.viewState.addListener { rebuild() }
        rebuild()
    }

    private fun rebuild() {
        list.removeAll()
        val activeId = ctx.viewState.activeTokenId
        val token: Token? = activeId?.let { ctx.bag.findToken(it) }
        val size = ctx.viewState.layersThumbSize
        if (token != null) {
            // Top-of-list = top-of-stack: reverse the layer order.
            val layersTopFirst = token.layers.asReversed()
            for (layer in layersTopFirst) {
                val img = ctx.tokenRenderer.run {
                    // Render just this single-layer "slice" for the thumbnail row.
                    val single = Token(token.id, token.kind).apply { addLayer(layer) }
                    ctx.thumbnails.tokenThumbnail(single, size)
                }
                val row = JPanel(BorderLayout())
                val selected = layer.id in ctx.viewState.selectedLayers
                row.border = BorderFactory.createLineBorder(if (selected) Color.BLUE else Color.GRAY, if (selected) 2 else 1)
                row.maximumSize = Dimension(Int.MAX_VALUE, size + 8)
                row.add(JLabel(ImageIcon(img)), BorderLayout.WEST)

                val buttons = JPanel(FlowLayout(FlowLayout.RIGHT))
                val up = JButton("▲").apply {
                    addActionListener {
                        val srcIdx = token.layers.indexOfFirst { it.id == layer.id }
                        if (srcIdx < token.layers.size - 1) {
                            ctx.history.execute(ctx.bag, ReorderLayerCommand(token.id, layer.id, srcIdx + 1))
                        }
                    }
                }
                val down = JButton("▼").apply {
                    addActionListener {
                        val srcIdx = token.layers.indexOfFirst { it.id == layer.id }
                        if (srcIdx > 0) {
                            ctx.history.execute(ctx.bag, ReorderLayerCommand(token.id, layer.id, srcIdx - 1))
                        }
                    }
                }
                val remove = JButton("X").apply {
                    addActionListener {
                        val ok = JOptionPane.showConfirmDialog(
                            this@LayersPanel,
                            "Remove layer?",
                            "Confirm",
                            JOptionPane.YES_NO_OPTION,
                        )
                        if (ok == JOptionPane.YES_OPTION) {
                            ctx.history.execute(ctx.bag, RemoveLayerCommand(token.id, layer.id))
                        }
                    }
                }
                buttons.add(up); buttons.add(down); buttons.add(remove)
                row.add(buttons, BorderLayout.EAST)
                row.addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.button != MouseEvent.BUTTON1) return
                        if (e.isControlDown) {
                            if (layer.id in ctx.viewState.selectedLayers) ctx.viewState.deselectLayer(layer.id)
                            else ctx.viewState.selectLayer(layer.id)
                        } else {
                            ctx.viewState.replaceSelection(listOf(layer.id))
                        }
                    }
                })
                list.add(row)
            }
        } else {
            val empty = JLabel("No token selected", SwingConstants.CENTER)
            list.add(empty)
        }
        list.add(Box.createVerticalGlue())
        list.revalidate(); list.repaint()
    }
}
```

- [ ] **Step 2: Replace `layersPanel` placeholder in `MainFrame`**

```kotlin
val layersPanel: JPanel = LayersPanel(ctx).apply { preferredSize = Dimension(260, 240) }
```

- [ ] **Step 3: Manual smoke**

Run: `./gradlew run`. Add a unit, select it; the layers list is empty until layers are added (Task 9 — Assets panel). For now: confirm list shows "No token selected" until a token is selected.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/LayersPanel.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): add LayersPanel with reorder and remove"
```

---

## Task 9: `AssetsLibraryPanel`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/AssetsLibraryPanel.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Implement the panel**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/AssetsLibraryPanel.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.assets.AssetTreeNode
import net.rafkos.neuroshima.editor.command.AddLayerCommand
import net.rafkos.neuroshima.editor.model.AssetPath
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JSplitPane
import javax.swing.JTree
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

private class FolderUserObject(val node: AssetTreeNode) {
    override fun toString(): String = node.name.ifEmpty { "content" }
}

class AssetsLibraryPanel(private val ctx: AppContext) : JPanel() {

    private val previewGrid = JPanel(GridLayout(0, 4, 4, 4))
    private val tree = JTree(DefaultTreeModel(DefaultMutableTreeNode(FolderUserObject(AssetTreeNode("")))))
    private val slider = JSlider(48, 192, ctx.viewState.assetsThumbSize)

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.assets"))
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JScrollPane(tree), JScrollPane(previewGrid))
        split.resizeWeight = 0.3
        add(split, BorderLayout.CENTER)

        val south = JPanel(BorderLayout())
        south.add(JButton(ctx.locale.t("button.refresh")).apply {
            addActionListener { ctx.library.refreshUser(); rebuildTree(); selectRoot() }
        }, BorderLayout.WEST)
        south.add(slider, BorderLayout.CENTER)
        add(south, BorderLayout.SOUTH)

        slider.addChangeListener { ctx.viewState.setAssetsThumbSize(slider.value); refreshPreview() }
        tree.addTreeSelectionListener(object : TreeSelectionListener {
            override fun valueChanged(e: TreeSelectionEvent?) { refreshPreview() }
        })
        ctx.viewState.addListener { refreshPreview() }

        rebuildTree()
        selectRoot()
    }

    private fun rebuildTree() {
        val root = DefaultMutableTreeNode(FolderUserObject(ctx.library.tree()))
        buildSubtree(root, (root.userObject as FolderUserObject).node)
        tree.model = DefaultTreeModel(root)
    }

    private fun buildSubtree(parent: DefaultMutableTreeNode, node: AssetTreeNode) {
        for (child in node.childFolders.sortedBy { it.name }) {
            val childNode = DefaultMutableTreeNode(FolderUserObject(child))
            parent.add(childNode)
            buildSubtree(childNode, child)
        }
    }

    private fun selectRoot() {
        val root = tree.model.root as DefaultMutableTreeNode
        tree.selectionPath = TreePath(root.path)
    }

    private fun selectedFolder(): AssetTreeNode? {
        val path = tree.selectionPath ?: return null
        val last = path.lastPathComponent as DefaultMutableTreeNode
        return (last.userObject as? FolderUserObject)?.node
    }

    private fun refreshPreview() {
        previewGrid.removeAll()
        val node = selectedFolder() ?: return
        val size = ctx.viewState.assetsThumbSize
        for (asset in node.assets) {
            val file = ctx.library.resolveFile(asset) ?: continue
            val source = file.toFile().inputStream().use { ImageIO.read(it) } ?: continue
            val scaled = source.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH)
            val lbl = JLabel(ImageIcon(scaled), JLabel.CENTER).apply {
                preferredSize = Dimension(size + 4, size + 4)
                toolTipText = asset.uri
            }
            lbl.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2 && e.button == MouseEvent.BUTTON1) addLayer(asset)
                }
            })
            previewGrid.add(lbl)
        }
        previewGrid.revalidate(); previewGrid.repaint()
    }

    private fun addLayer(asset: AssetPath) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        // Ensure image is in cache (lazy for assets added at runtime)
        ctx.library.resolveFile(asset)?.let { p ->
            val img = p.toFile().inputStream().use { ImageIO.read(it) }
            if (img != null) ctx.imageCache.put(asset, img)
        }
        ctx.history.execute(ctx.bag, AddLayerCommand(tokenId, asset))
    }
}
```

- [ ] **Step 2: Replace `assetsPanel` placeholder in `MainFrame`**

```kotlin
val assetsPanel: JPanel = AssetsLibraryPanel(ctx).apply { preferredSize = Dimension(0, 240) }
```

- [ ] **Step 3: Manual smoke**

Run: `./gradlew run`. Place at least one PNG in `<user.home>/.neuroshima-editor/content/`, click "Refresh content", then double-click the file thumbnail → a layer should appear in `LayersPanel` once a token is selected.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/AssetsLibraryPanel.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): add AssetsLibraryPanel with tree, preview, refresh, slider"
```

---

## Task 10: `LayerPropertiesPanel`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/LayerPropertiesPanel.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Implement the panel**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/LayerPropertiesPanel.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.SetLayerPropertyCommand
import net.rafkos.neuroshima.editor.model.Layer
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.event.ChangeListener

class LayerPropertiesPanel(private val ctx: AppContext) : JPanel() {

    private val content = JPanel(GridLayout(0, 2, 4, 2))
    private var listenersInstalled: Boolean = false

    init {
        layout = BorderLayout()
        border = BorderFactory.createTitledBorder(ctx.locale.t("panel.properties"))
        add(content, BorderLayout.NORTH)
        ctx.bag.addListener { rebuild() }
        ctx.viewState.addListener { rebuild() }
        rebuild()
    }

    private fun activeLayer(): Pair<java.util.UUID, Layer>? {
        if (ctx.viewState.selectedLayers.size != 1) return null
        val tokenId = ctx.viewState.activeTokenId ?: return null
        val layerId = ctx.viewState.selectedLayers.first()
        val token = ctx.bag.findToken(tokenId) ?: return null
        val layer = token.findLayer(layerId) ?: return null
        return tokenId to layer
    }

    private fun rebuild() {
        content.removeAll()
        val (tokenId, layer) = activeLayer() ?: run {
            content.add(JLabel("No single layer selected"))
            content.add(JLabel(""))
            content.revalidate(); content.repaint()
            return
        }
        val rows = listOf(
            LayerProperty.OFFSET_X to layer.props.offsetX.toDouble(),
            LayerProperty.OFFSET_Y to layer.props.offsetY.toDouble(),
            LayerProperty.ROTATION to layer.props.rotation.toDouble(),
            LayerProperty.SCALE to layer.props.scale.toDouble(),
            LayerProperty.OPACITY to layer.props.opacity.toDouble(),
            LayerProperty.HUE to layer.props.hue.toDouble(),
            LayerProperty.SATURATION to layer.props.saturation.toDouble(),
            LayerProperty.BRIGHTNESS to layer.props.brightness.toDouble(),
        )
        for ((prop, value) in rows) {
            content.add(JLabel(prop.labelKey().let { ctx.locale.t(it) }))
            val (min, max, step) = bounds(prop)
            val model = SpinnerNumberModel(value, min, max, step)
            val spinner = JSpinner(model)
            spinner.addChangeListener(ChangeListener {
                val newValue = (spinner.value as Number).toDouble()
                ctx.history.execute(ctx.bag, SetLayerPropertyCommand(
                    tokenId = tokenId,
                    layerId = layer.id,
                    property = prop,
                    oldValue = value,
                    newValue = newValue,
                ))
            })
            content.add(spinner)
        }
        content.revalidate(); content.repaint()
    }

    private fun bounds(p: LayerProperty): Triple<Double, Double, Double> = when (p) {
        LayerProperty.OFFSET_X, LayerProperty.OFFSET_Y -> Triple(-1000.0, 1000.0, 1.0)
        LayerProperty.ROTATION -> Triple(-360.0, 360.0, 1.0)
        LayerProperty.SCALE -> Triple(0.01, 10.0, 0.05)
        LayerProperty.OPACITY -> Triple(0.0, 1.0, 0.05)
        LayerProperty.HUE -> Triple(0.0, 1.0, 0.01)
        LayerProperty.SATURATION -> Triple(0.0, 2.0, 0.05)
        LayerProperty.BRIGHTNESS -> Triple(0.0, 2.0, 0.05)
    }

    private fun LayerProperty.labelKey(): String = when (this) {
        LayerProperty.OFFSET_X -> "prop.offsetX"
        LayerProperty.OFFSET_Y -> "prop.offsetY"
        LayerProperty.ROTATION -> "prop.rotation"
        LayerProperty.SCALE -> "prop.scale"
        LayerProperty.OPACITY -> "prop.opacity"
        LayerProperty.HUE -> "prop.hue"
        LayerProperty.SATURATION -> "prop.saturation"
        LayerProperty.BRIGHTNESS -> "prop.brightness"
    }
}
```

- [ ] **Step 2: Replace `propertiesPanel` in `MainFrame`**

```kotlin
val propertiesPanel: JPanel = LayerPropertiesPanel(ctx).apply { preferredSize = Dimension(260, 280) }
```

- [ ] **Step 3: Manual smoke**

Run: `./gradlew run`. Create a token, add a layer via Assets, click the layer row in Layers panel — Properties panel should show 8 spinners. Adjusting any spinner should update the model (re-render in Task 11 will make it visible on canvas).

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/LayerPropertiesPanel.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): add LayerPropertiesPanel with 8-property spinners"
```

---

## Task 11: `TokenCanvasPanel` (paint pipeline + overlay)

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Implement the canvas**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.canvas

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.render.AffineBuilder
import net.rafkos.neuroshima.editor.render.LayerRenderer
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JPanel

const val LOGICAL_SIZE_PX: Int = 490

class TokenCanvasPanel(private val ctx: AppContext) : JPanel() {

    private val overlay: BufferedImage? = loadOverlay()

    init {
        preferredSize = Dimension(LOGICAL_SIZE_PX, LOGICAL_SIZE_PX)
        background = Color(60, 60, 60)
        ctx.bag.addListener { repaint() }
        ctx.viewState.addListener { repaint() }
    }

    private fun loadOverlay(): BufferedImage? {
        val resource = TokenCanvasPanel::class.java.classLoader.getResource("overlay/HEX_template_lines.png")
            ?: return null
        return resource.openStream().use { ImageIO.read(it) }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val zoom = ctx.viewState.zoom
        val centerX = width / 2.0
        val centerY = height / 2.0
        // World transform (zoom around panel center). Translate before scale.
        g2.translate(centerX, centerY)
        g2.scale(zoom.toDouble(), zoom.toDouble())
        g2.translate(-LOGICAL_SIZE_PX / 2.0, -LOGICAL_SIZE_PX / 2.0)
        // Token shape backdrop (light fill so user sees bounds)
        g2.color = Color(220, 220, 220)
        g2.fillRect(0, 0, LOGICAL_SIZE_PX, LOGICAL_SIZE_PX)
        // Layers
        val tokenId = ctx.viewState.activeTokenId
        val token = tokenId?.let { ctx.bag.findToken(it) }
        if (token != null) {
            for (layer in token.layers) {
                val source = ctx.imageCache.get(layer.assetPath) ?: continue
                val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
                val processed = ctx.processedCache.get(key)
                    ?: LayerRenderer.applyPixelOps(source, layer.props).also { ctx.processedCache.put(key, it) }
                val xform = AffineBuilder.build(
                    props = layer.props,
                    canvasCenterX = LOGICAL_SIZE_PX / 2.0,
                    canvasCenterY = LOGICAL_SIZE_PX / 2.0,
                    imageWidth = processed.width,
                    imageHeight = processed.height,
                )
                g2.drawImage(processed, xform, null)
            }
        }
        if (ctx.viewState.showOverlay && overlay != null) {
            val ox = (LOGICAL_SIZE_PX - overlay.width) / 2
            val oy = (LOGICAL_SIZE_PX - overlay.height) / 2
            g2.drawImage(overlay, ox, oy, null)
        }
    }
}
```

- [ ] **Step 2: Replace `canvasPanel` in `MainFrame` and add overlay toggle**

Replace the `canvasPanel` placeholder with two fields — the inner `TokenCanvasPanel` (so later tasks can attach mouse/key handlers to it directly) and the wrapping `JPanel`:

```kotlin
val canvasComponent: TokenCanvasPanel = TokenCanvasPanel(ctx)
val canvasPanel: JPanel = JPanel(BorderLayout()).apply {
    add(canvasComponent, BorderLayout.CENTER)
    val toggle = JCheckBox(ctx.locale.t("button.show.overlay")).apply {
        addActionListener { ctx.viewState.setShowOverlay(isSelected) }
    }
    add(toggle, BorderLayout.SOUTH)
}
```

Add imports for `TokenCanvasPanel` and `JCheckBox`. Task 12 (zoom) and Task 13 (tool controller) reference `canvasComponent` directly — no need to dig into the wrapper.

- [ ] **Step 3: Place the overlay PNG on the classpath**

Copy `local_resources/overlay/HEX_template_lines.png` (you supply it) into the classpath at `src/main/resources/overlay/HEX_template_lines.png`. Until you have the real artwork, create a 490×490 transparent placeholder via the dev console (or skip — `loadOverlay()` returns null gracefully).

- [ ] **Step 4: Manual smoke**

Run: `./gradlew run`. Add a unit token, add a layer via Assets — image should render centered on canvas. Toggle the "Show overlay" checkbox — overlay should appear/disappear.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): add TokenCanvasPanel with paint pipeline and overlay toggle"
```

---

## Task 12: `ZoomController` — mouse-wheel zoom + middle-drag pan + Ctrl+0 reset

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt`

- [ ] **Step 1: Wire scroll + middle-drag handlers**

Insert into `TokenCanvasPanel`'s `init` block (after existing listeners):

```kotlin
// Zoom on wheel
addMouseWheelListener { e ->
    val factor = if (e.wheelRotation < 0) 1.1f else 1f / 1.1f
    ctx.viewState.setZoom(ctx.viewState.zoom * factor)
}
// Pan on middle-drag (translate via state — for the first cut, track an offset field locally)
val panOrigin = intArrayOf(0, 0)
val panOffset = intArrayOf(0, 0)
addMouseListener(object : java.awt.event.MouseAdapter() {
    override fun mousePressed(e: java.awt.event.MouseEvent) {
        if (e.button == java.awt.event.MouseEvent.BUTTON2) {
            panOrigin[0] = e.x; panOrigin[1] = e.y
        }
    }
})
addMouseMotionListener(object : java.awt.event.MouseAdapter() {
    override fun mouseDragged(e: java.awt.event.MouseEvent) {
        if (java.awt.event.MouseEvent.getModifiersExText(e.modifiersEx).contains("Button2")) {
            panOffset[0] += e.x - panOrigin[0]
            panOffset[1] += e.y - panOrigin[1]
            panOrigin[0] = e.x; panOrigin[1] = e.y
            this@TokenCanvasPanel.panX = panOffset[0]
            this@TokenCanvasPanel.panY = panOffset[1]
            repaint()
        }
    }
})
```

Declare the pan fields in the class:

```kotlin
var panX: Int = 0
var panY: Int = 0
```

Update `paintComponent` to apply pan before zoom:

```kotlin
g2.translate(centerX + panX, centerY + panY)
g2.scale(zoom.toDouble(), zoom.toDouble())
g2.translate(-LOGICAL_SIZE_PX / 2.0, -LOGICAL_SIZE_PX / 2.0)
```

Add Ctrl+0 reset via `KeyStroke` bindings in `MainFrame` (uses the `canvasComponent` field added in Task 11 Step 2):

```kotlin
val resetKey = javax.swing.KeyStroke.getKeyStroke("control 0")
canvasPanel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(resetKey, "resetZoom")
canvasPanel.actionMap.put("resetZoom", object : javax.swing.AbstractAction() {
    override fun actionPerformed(e: java.awt.event.ActionEvent) {
        ctx.viewState.setZoom(1f)
        canvasComponent.panX = 0; canvasComponent.panY = 0
        canvasComponent.repaint()
    }
})
```

- [ ] **Step 2: Manual smoke**

Run: `./gradlew run`. Scroll wheel: canvas zooms. Middle-mouse drag: canvas pans. Ctrl+0: reset.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): wheel zoom, middle-drag pan, Ctrl+0 reset"
```

---

## Task 13: `Tool` interface + `ToolController`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/Tool.kt`
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/ToolController.kt`

- [ ] **Step 1: Define `Tool` interface**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/Tool.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import java.awt.Cursor
import java.awt.event.MouseEvent

interface Tool {
    fun cursor(): Cursor = Cursor.getDefaultCursor()
    fun onMousePressed(ctx: AppContext, e: MouseEvent) {}
    fun onMouseDragged(ctx: AppContext, e: MouseEvent) {}
    fun onMouseReleased(ctx: AppContext, e: MouseEvent) {}
}
```

- [ ] **Step 2: Implement `ToolController`**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/ToolController.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.ui.ToolId
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

class ToolController(private val ctx: AppContext, private val attachTo: Component) {

    private val tools: Map<ToolId, Tool> = mapOf(
        ToolId.SELECT to SelectTool(),
        ToolId.MOVE to MoveTool(),
        ToolId.ROTATE to RotateTool(),
        ToolId.SCALE to ScaleTool(),
        ToolId.OPACITY to OpacityTool(),
        ToolId.COLORIZE to ColorizeTool(),
    )

    init {
        attachTo.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { current().onMousePressed(ctx, e) }
            override fun mouseReleased(e: MouseEvent) { current().onMouseReleased(ctx, e) }
        })
        attachTo.addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) { current().onMouseDragged(ctx, e) }
        })
        ctx.viewState.addListener { attachTo.cursor = current().cursor() }
    }

    private fun current(): Tool = tools.getValue(ctx.viewState.activeTool)
}
```

- [ ] **Step 3: Stub the 6 tool classes (empty bodies for now)**

Each in its own file under `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/`:

- `SelectTool.kt`, `MoveTool.kt`, `RotateTool.kt`, `ScaleTool.kt`, `OpacityTool.kt`, `ColorizeTool.kt`.

Example (`SelectTool.kt`):

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

class SelectTool : Tool
```

Create the other five identically (replacing the class name). Real bodies follow in Tasks 14-19.

- [ ] **Step 4: Wire `ToolController` to the canvas in `MainFrame`**

After constructing the canvas:

```kotlin
ToolController(ctx, canvasComponent)
```

- [ ] **Step 5: Confirm build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): add Tool interface, ToolController, six stub tools"
```

---

## Task 14: `MoveTool`

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/MoveTool.kt`

- [ ] **Step 1: Implement `MoveTool`**

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.util.UUID

class MoveTool : Tool {

    private data class StartState(val px: Int, val py: Int, val tokenId: UUID, val originals: List<MultiLayerPropertyCommand.Target> /* X then Y interleaved? */)

    // We store two separate snapshots because X and Y are separate properties.
    private var startX: Int = 0
    private var startY: Int = 0
    private var startTokenId: UUID? = null
    private var startTargetsX: List<MultiLayerPropertyCommand.Target> = emptyList()
    private var startTargetsY: List<MultiLayerPropertyCommand.Target> = emptyList()

    override fun cursor(): Cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)

    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        startX = e.x; startY = e.y; startTokenId = tokenId
        startTargetsX = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            MultiLayerPropertyCommand.Target(tokenId, id, layer.props.offsetX.toDouble())
        }
        startTargetsY = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            MultiLayerPropertyCommand.Target(tokenId, id, layer.props.offsetY.toDouble())
        }
    }

    override fun onMouseDragged(ctx: AppContext, e: MouseEvent) {
        val tokenId = startTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val dx = ((e.x - startX) / ctx.viewState.zoom).toInt()
        val dy = ((e.y - startY) / ctx.viewState.zoom).toInt()
        val targetsXNow = startTargetsX.map { t ->
            MultiLayerPropertyCommand.Target(t.tokenId, t.layerId, t.oldValue) // unchanged old values for the merged command
        }
        val targetsYNow = startTargetsY.map { t ->
            MultiLayerPropertyCommand.Target(t.tokenId, t.layerId, t.oldValue)
        }
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(
            property = LayerProperty.OFFSET_X,
            newValue = startTargetsX.first().oldValue + dx,
            targets = targetsXNow,
        ))
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(
            property = LayerProperty.OFFSET_Y,
            newValue = startTargetsY.first().oldValue + dy,
            targets = targetsYNow,
        ))
    }

    override fun onMouseReleased(ctx: AppContext, e: MouseEvent) {
        startTokenId = null
    }
}
```

(Per-layer relative move would be preferable for true multi-select; the above moves all selected by the same delta — matches the spec's "drag affects all selected layers" semantics. Refinement to per-layer deltas can land in a follow-up if needed.)

- [ ] **Step 2: Manual smoke**

Run: `./gradlew run`. Add a token, add a layer, switch to Move tool, drag on canvas — layer moves. Release, drag again — moves more. Undo (Task 20) will collapse the drag into a single entry via the CommandHistory merge window.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/MoveTool.kt
git commit -m "feat(ui): implement MoveTool"
```

---

## Task 15: `RotateTool`

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/RotateTool.kt`

- [ ] **Step 1: Implement `RotateTool`**

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import java.awt.Cursor
import java.awt.event.MouseEvent

class RotateTool : Tool {
    private var startX: Int = 0
    private var startTargets: List<MultiLayerPropertyCommand.Target> = emptyList()

    override fun cursor(): Cursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)

    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        startX = e.x
        startTargets = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            MultiLayerPropertyCommand.Target(tokenId, id, layer.props.rotation.toDouble())
        }
    }

    override fun onMouseDragged(ctx: AppContext, e: MouseEvent) {
        if (startTargets.isEmpty()) return
        val degrees = (e.x - startX).toDouble() // 1 degree per pixel
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(
            property = LayerProperty.ROTATION,
            newValue = startTargets.first().oldValue + degrees,
            targets = startTargets,
        ))
    }

    override fun onMouseReleased(ctx: AppContext, e: MouseEvent) {
        startTargets = emptyList()
    }
}
```

- [ ] **Step 2: Manual smoke**

Run app, select Rotate tool, drag left/right — layer rotates.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/RotateTool.kt
git commit -m "feat(ui): implement RotateTool"
```

---

## Task 16: `ScaleTool`

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/ScaleTool.kt`

- [ ] **Step 1: Implement `ScaleTool`**

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import java.awt.Cursor
import java.awt.event.MouseEvent

class ScaleTool : Tool {
    private var startX: Int = 0
    private var startTargets: List<MultiLayerPropertyCommand.Target> = emptyList()

    override fun cursor(): Cursor = Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)

    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        startX = e.x
        startTargets = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            MultiLayerPropertyCommand.Target(tokenId, id, layer.props.scale.toDouble())
        }
    }

    override fun onMouseDragged(ctx: AppContext, e: MouseEvent) {
        if (startTargets.isEmpty()) return
        val factor = 1.0 + (e.x - startX) / 200.0 // 200 px = +1.0 scale
        val target = (startTargets.first().oldValue * factor).coerceIn(0.01, 10.0)
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(
            property = LayerProperty.SCALE,
            newValue = target,
            targets = startTargets,
        ))
    }

    override fun onMouseReleased(ctx: AppContext, e: MouseEvent) { startTargets = emptyList() }
}
```

- [ ] **Step 2: Manual smoke**

Run, select Scale tool, drag right — layer grows. Drag left — shrinks.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/ScaleTool.kt
git commit -m "feat(ui): implement ScaleTool"
```

---

## Task 17: `OpacityTool`

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/OpacityTool.kt`

- [ ] **Step 1: Implement `OpacityTool`**

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import java.awt.event.MouseEvent

class OpacityTool : Tool {
    private var startX: Int = 0
    private var startTargets: List<MultiLayerPropertyCommand.Target> = emptyList()

    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        startX = e.x
        startTargets = selected.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            MultiLayerPropertyCommand.Target(tokenId, id, layer.props.opacity.toDouble())
        }
    }

    override fun onMouseDragged(ctx: AppContext, e: MouseEvent) {
        if (startTargets.isEmpty()) return
        val delta = (e.x - startX) / 200.0 // 200 px = full range
        val target = (startTargets.first().oldValue + delta).coerceIn(0.0, 1.0)
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(
            property = LayerProperty.OPACITY,
            newValue = target,
            targets = startTargets,
        ))
    }

    override fun onMouseReleased(ctx: AppContext, e: MouseEvent) { startTargets = emptyList() }
}
```

- [ ] **Step 2: Manual smoke**

Run, select Opacity tool, drag — layer fades in/out.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/OpacityTool.kt
git commit -m "feat(ui): implement OpacityTool"
```

---

## Task 18: `ColorizeTool`

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/ColorizeTool.kt`

The tool opens a color-picker dialog on press, then maps the picked color's HSB into hue/saturation/brightness commands.

- [ ] **Step 1: Implement `ColorizeTool`**

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.LayerProperty
import net.rafkos.neuroshima.editor.command.MultiLayerPropertyCommand
import java.awt.Color
import java.awt.event.MouseEvent
import javax.swing.JColorChooser

class ColorizeTool : Tool {
    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        val tokenId = ctx.viewState.activeTokenId ?: return
        val token = ctx.bag.findToken(tokenId) ?: return
        val selected = ctx.viewState.selectedLayers.ifEmpty { token.layers.map { it.id }.toSet() }
        if (selected.isEmpty()) return
        val picked = JColorChooser.showDialog(e.component, "Colorize", Color.WHITE) ?: return
        val hsb = Color.RGBtoHSB(picked.red, picked.green, picked.blue, FloatArray(3))
        apply(ctx, tokenId, selected, LayerProperty.HUE, hsb[0].toDouble()) { it.hue.toDouble() }
        apply(ctx, tokenId, selected, LayerProperty.SATURATION, hsb[1].toDouble()) { it.saturation.toDouble() }
        apply(ctx, tokenId, selected, LayerProperty.BRIGHTNESS, hsb[2].toDouble()) { it.brightness.toDouble() }
    }

    private fun apply(
        ctx: AppContext,
        tokenId: java.util.UUID,
        ids: Set<java.util.UUID>,
        property: LayerProperty,
        newValue: Double,
        oldOf: (net.rafkos.neuroshima.editor.model.LayerProperties) -> Double,
    ) {
        val token = ctx.bag.findToken(tokenId) ?: return
        val targets = ids.mapNotNull { id ->
            val layer = token.findLayer(id) ?: return@mapNotNull null
            MultiLayerPropertyCommand.Target(tokenId, id, oldOf(layer.props))
        }
        ctx.history.execute(ctx.bag, MultiLayerPropertyCommand(property, newValue, targets))
    }
}
```

- [ ] **Step 2: Manual smoke**

Run, select Colorize tool, click canvas — color picker opens; pick red — selected layers shift toward red HSB.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/ColorizeTool.kt
git commit -m "feat(ui): implement ColorizeTool with JColorChooser"
```

---

## Task 19: `SelectTool` (Ctrl+click multi, Esc clear)

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/SelectTool.kt`

The Select tool itself is mostly a marker — selection on the canvas requires per-layer hit-testing, which is outside the spec for the first cut (layer selection happens in the Layers panel). The tool just clears selection on a left-click in empty canvas area and provides the Esc-to-clear binding hook.

- [ ] **Step 1: Implement `SelectTool`**

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import java.awt.event.MouseEvent

class SelectTool : Tool {
    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        if (!e.isControlDown && e.button == MouseEvent.BUTTON1) ctx.viewState.clearSelection()
    }
}
```

- [ ] **Step 2: Wire `Esc` to clear selection (global)**

In `MainFrame`, add:

```kotlin
val esc = javax.swing.KeyStroke.getKeyStroke("ESCAPE")
canvasPanel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(esc, "clearSelection")
canvasPanel.actionMap.put("clearSelection", object : javax.swing.AbstractAction() {
    override fun actionPerformed(e: java.awt.event.ActionEvent) { ctx.viewState.clearSelection() }
})
```

- [ ] **Step 3: Manual smoke**

Run, select two layers via Ctrl+click in Layers panel; press Esc — selection clears.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/SelectTool.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): implement SelectTool and ESC-clear binding"
```

---

## Task 20: File menu (Save / Save As / Undo / Redo / Print)

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MenuBuilder.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Implement `MenuBuilder`**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/MenuBuilder.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.persistence.JsonBagStore
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.KeyStroke

class MenuBuilder(private val ctx: AppContext, private val frame: MainFrame) {

    fun build(): JMenuBar {
        val bar = JMenuBar()
        bar.add(fileMenu())
        bar.add(editMenu())
        return bar
    }

    private fun fileMenu(): JMenu {
        val m = JMenu(ctx.locale.t("menu.file"))
        m.add(JMenuItem(ctx.locale.t("menu.file.save")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK)
            addActionListener { save() }
        })
        m.add(JMenuItem(ctx.locale.t("menu.file.saveAs")).apply { addActionListener { saveAs() } })
        m.addSeparator()
        m.add(JMenuItem(ctx.locale.t("menu.file.print")).apply {
            isEnabled = false
            toolTipText = "Out of scope for first release"
        })
        return m
    }

    private fun editMenu(): JMenu {
        val m = JMenu(ctx.locale.t("menu.edit"))
        m.add(JMenuItem(ctx.locale.t("menu.edit.undo")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK)
            addActionListener { ctx.history.undo(ctx.bag) }
        })
        m.add(JMenuItem(ctx.locale.t("menu.edit.redo")).apply {
            accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_Y, java.awt.event.InputEvent.CTRL_DOWN_MASK)
            addActionListener { ctx.history.redo(ctx.bag) }
        })
        return m
    }

    private fun save() {
        val file = ctx.currentFile ?: return saveAs()
        JsonBagStore(assetResolver = ctx.library::assetExists).save(ctx.bag, file)
        ctx.markClean()
        ctx.history.clear()
    }

    private fun saveAs() {
        val chooser = JFileChooser()
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return
        val raw: File = chooser.selectedFile
        val path = if (raw.extension.equals("box", ignoreCase = true)) raw.toPath()
            else File("${raw.absolutePath}.box").toPath()
        JsonBagStore(assetResolver = ctx.library::assetExists).save(ctx.bag, path)
        ctx.markClean()
        ctx.history.clear()
        frame.title = "${ctx.locale.t("app.title")} — ${path.fileName}"
    }
}
```

- [ ] **Step 2: Install the menu in `MainFrame`**

At the bottom of `MainFrame.init`:

```kotlin
jMenuBar = MenuBuilder(ctx, this).build()
```

- [ ] **Step 3: Manual smoke**

Run, add a token, File → Save As → pick path. Add another token, Ctrl+Z — first added token survives the undo, second is removed.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/MenuBuilder.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): add File and Edit menus with key bindings"
```

---

## Task 21: Open file flow with `MissingAssetsDialog`

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/dialogs/MissingAssetsDialog.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MenuBuilder.kt`

- [ ] **Step 1: Implement `MissingAssetsDialog`**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/dialogs/MissingAssetsDialog.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.dialogs

import net.rafkos.neuroshima.editor.persistence.MissingAssetsException
import java.awt.Component
import javax.swing.JOptionPane

object MissingAssetsDialog {
    fun show(parent: Component, header: String, ex: MissingAssetsException) {
        val body = StringBuilder(header).append("\n\n")
        for (m in ex.missing) body.append(m.uri).append('\n')
        JOptionPane.showMessageDialog(parent, body.toString(), "Missing assets", JOptionPane.ERROR_MESSAGE)
    }
}
```

- [ ] **Step 2: Add "Open" menu item to `MenuBuilder.fileMenu()`**

Insert before `addSeparator()`:

```kotlin
m.add(JMenuItem("Open...").apply {
    accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_DOWN_MASK)
    addActionListener { open() }
})
```

- [ ] **Step 3: Implement `open()` in `MenuBuilder`**

Add the method:

```kotlin
private fun open() {
    val chooser = JFileChooser()
    if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
    val path = chooser.selectedFile.toPath()
    val opener = net.rafkos.neuroshima.editor.persistence.BagOpener(ctx.library, ctx.imageCache)
    try {
        val loaded = kotlinx.coroutines.runBlocking { opener.open(path) }
        ctx.replaceBag(loaded, path)
        frame.title = "${ctx.locale.t("app.title")} — ${path.fileName}"
    } catch (ex: net.rafkos.neuroshima.editor.persistence.MissingAssetsException) {
        net.rafkos.neuroshima.editor.ui.dialogs.MissingAssetsDialog.show(
            frame, ctx.locale.t("error.missing.assets"), ex,
        )
    } catch (ex: net.rafkos.neuroshima.editor.persistence.SchemaVersionException) {
        JOptionPane.showMessageDialog(
            frame,
            ctx.locale.t("error.schema.version", ex.found),
            "Open failed",
            JOptionPane.ERROR_MESSAGE,
        )
    }
}
```

- [ ] **Step 4: Manual smoke**

Run, save a bag, manually delete a referenced asset from `~/.neuroshima-editor/content/`, File → Open → that bag — dialog lists the missing path.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/dialogs/MissingAssetsDialog.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MenuBuilder.kt
git commit -m "feat(ui): add Open menu, MissingAssetsDialog, schema-version dialog"
```

---

## Task 22: `SaveBeforeCloseDialog` + window-close guard

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/dialogs/SaveBeforeCloseDialog.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Implement `SaveBeforeCloseDialog`**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/dialogs/SaveBeforeCloseDialog.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.dialogs

import java.awt.Component
import javax.swing.JOptionPane

object SaveBeforeCloseDialog {
    enum class Result { SAVE, DISCARD, CANCEL }

    fun ask(parent: Component): Result {
        val choice = JOptionPane.showOptionDialog(
            parent,
            "You have unsaved changes. Save before closing?",
            "Unsaved changes",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            arrayOf("Save", "Discard", "Cancel"),
            "Save",
        )
        return when (choice) {
            0 -> Result.SAVE
            1 -> Result.DISCARD
            else -> Result.CANCEL
        }
    }
}
```

- [ ] **Step 2: Expose a public `save()` method on `MenuBuilder`**

In `MenuBuilder.kt`, change `private fun save()` to `fun save(): Boolean` and have it return `true` on success, `false` on cancel/failure. Update the body to:

```kotlin
fun save(): Boolean {
    val file = ctx.currentFile ?: return saveAs()
    JsonBagStore(assetResolver = ctx.library::assetExists).save(ctx.bag, file)
    ctx.markClean()
    ctx.history.clear()
    return true
}
```

Also change `saveAs()` to `fun saveAs(): Boolean` returning `true` on accept, `false` on cancel.

- [ ] **Step 3: Hold the `MenuBuilder` instance on `MainFrame` and wire window-close guard**

In `MainFrame.init`, capture the menu builder before installing it:

```kotlin
val menuBuilder = MenuBuilder(ctx, this)
jMenuBar = menuBuilder.build()
```

Then add the window listener:

```kotlin
addWindowListener(object : java.awt.event.WindowAdapter() {
    override fun windowClosing(e: java.awt.event.WindowEvent) {
        if (!ctx.dirty) { ctx.savePrefs(); dispose(); System.exit(0); return }
        when (net.rafkos.neuroshima.editor.ui.dialogs.SaveBeforeCloseDialog.ask(this@MainFrame)) {
            net.rafkos.neuroshima.editor.ui.dialogs.SaveBeforeCloseDialog.Result.SAVE -> {
                if (menuBuilder.save()) { ctx.savePrefs(); dispose(); System.exit(0) }
                // else: user cancelled the file chooser → stay open
            }
            net.rafkos.neuroshima.editor.ui.dialogs.SaveBeforeCloseDialog.Result.DISCARD -> {
                ctx.savePrefs(); dispose(); System.exit(0)
            }
            net.rafkos.neuroshima.editor.ui.dialogs.SaveBeforeCloseDialog.Result.CANCEL -> {
                // stay open
            }
        }
    }
})
```

- [ ] **Step 4: Manual smoke**

Run, make a change, click the window close button — dialog appears with Save / Discard / Cancel.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/dialogs/SaveBeforeCloseDialog.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): save-before-close dialog on window close"
```

---

## Task 23: Status bar

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/StatusBar.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Implement `StatusBar`**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/StatusBar.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui

import net.rafkos.neuroshima.editor.app.AppContext
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel

class StatusBar(private val ctx: AppContext) : JPanel(FlowLayout(FlowLayout.LEFT, 12, 2)) {
    private val fileLabel = JLabel()
    private val countLabel = JLabel()
    private val savedLabel = JLabel()

    init {
        add(fileLabel); add(countLabel); add(savedLabel)
        ctx.bag.addListener { refresh() }
        refresh()
    }

    private fun refresh() {
        fileLabel.text = ctx.locale.t("status.loadedFile", ctx.currentFile?.fileName?.toString() ?: "—")
        countLabel.text = ctx.locale.t("status.tokens", ctx.bag.tokens.size)
        savedLabel.text = if (ctx.dirty) "*" else ""
    }
}
```

- [ ] **Step 2: Replace status bar placeholder in `MainFrame`**

```kotlin
val statusBar: JPanel = StatusBar(ctx)
```

- [ ] **Step 3: Manual smoke**

Run, add tokens — status bar updates token count. Save — `*` clears.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/StatusBar.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "feat(ui): add status bar with file, token count, dirty marker"
```

---

## Task 24: Undo auto-selects the affected token

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt`

- [ ] **Step 1: Subscribe to bag events to auto-select the affected token**

In `AppContext.installInvalidationListener` (defined in Plan B Task 7 Step 3), extend the listener to also call `viewState.setActiveToken(event.tokenId)` for any layer-level event when `viewState.activeTokenId != event.tokenId`:

```kotlin
private fun installInvalidationListener(b: TokenBag) {
    b.addListener { event ->
        val touchedToken: java.util.UUID? = when (event) {
            is net.rafkos.neuroshima.editor.model.ModelEvent.LayerAdded -> event.tokenId
            is net.rafkos.neuroshima.editor.model.ModelEvent.LayerRemoved -> event.tokenId
            is net.rafkos.neuroshima.editor.model.ModelEvent.LayerReordered -> event.tokenId
            is net.rafkos.neuroshima.editor.model.ModelEvent.LayerPropsChanged -> event.tokenId
            else -> null
        }
        if (touchedToken != null) {
            thumbnails.invalidateToken(touchedToken)
            if (viewState.activeTokenId != touchedToken) viewState.setActiveToken(touchedToken)
        }
    }
}
```

- [ ] **Step 2: Manual smoke**

Run, create token A and token B. Select B. Press Ctrl+Z to undo last action on A — active token switches to A.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt
git commit -m "feat(app): undo auto-selects the affected token"
```

---

## Task 25: Finalize `PackageBoundaryTest` (R7 + R8 + UI rule)

**Files:**
- Modify: `src/test/kotlin/net/rafkos/neuroshima/editor/architecture/PackageBoundaryTest.kt`

- [ ] **Step 1: Add the remaining rules**

Replace the `private val rules` list with:

```kotlin
private val rules: List<Rule> = listOf(
    Rule(
        name = "R1: model is Swing/AWT-free (java.awt.geom allowed)",
        source = "$BASE.model",
        forbidden = listOf("javax.swing", "java.awt"),
        allowedImports = listOf("java.awt.geom"),
    ),
    Rule(
        name = "R2: command depends on model only",
        source = "$BASE.command",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui"),
    ),
    Rule(
        name = "R3: persistence has no Swing/AWT/ui/command (assets allowed)",
        source = "$BASE.persistence",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui", "$BASE.command"),
    ),
    Rule(
        name = "R4: assets has no Swing, no ui (java.awt.image allowed)",
        source = "$BASE.assets",
        forbidden = listOf("javax.swing", "$BASE.ui", "$BASE.command"),
        allowedImports = listOf("java.awt.image"),
    ),
    Rule(
        name = "R5: render has no Swing widgets and no ui (java.awt allowed)",
        source = "$BASE.render",
        forbidden = listOf("javax.swing", "$BASE.ui", "$BASE.command"),
        allowedImports = listOf("java.awt"),
    ),
    Rule(
        name = "R6: i18n is leaf",
        source = "$BASE.i18n",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui", "$BASE.model", "$BASE.command"),
    ),
    Rule(
        name = "R7: prefs is leaf",
        source = "$BASE.prefs",
        forbidden = listOf("javax.swing", "java.awt", "$BASE.ui", "$BASE.model"),
    ),
    Rule(
        name = "R8: ui.tools may not depend on persistence",
        source = "$BASE.ui.tools",
        forbidden = listOf("$BASE.persistence"),
    ),
)
```

- [ ] **Step 2: Run test**

Run: `./gradlew test --tests "net.rafkos.neuroshima.editor.architecture.PackageBoundaryTest"`
Expected: PASS for all 8 rules.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/net/rafkos/neuroshima/editor/architecture/PackageBoundaryTest.kt
git commit -m "test(architecture): activate R7 + R8 + finalize boundaries"
```

---

## Task 26: Bundle `local_resources/` directory

**Files:**
- Create: `local_resources/assets/.keep`
- Create: `local_resources/overlay/.keep`
- Create: `local_resources/icon.ico` (binary; user supplies)

- [ ] **Step 1: Create the directory skeleton**

```bash
mkdir -p local_resources/assets/backgrounds local_resources/assets/units \
         local_resources/assets/modifiers local_resources/assets/icons \
         local_resources/overlay
touch local_resources/assets/backgrounds/.keep \
      local_resources/assets/units/.keep \
      local_resources/assets/modifiers/.keep \
      local_resources/assets/icons/.keep \
      local_resources/overlay/.keep
```

If the user provides them, also copy in real artwork: bundled PNGs under `local_resources/assets/<category>/`, the overlay PNG at `local_resources/overlay/HEX_template_lines.png`, and `local_resources/icon.ico`.

- [ ] **Step 2: Commit**

```bash
git add local_resources/
git commit -m "chore: scaffold local_resources directory tree"
```

---

## Task 27: Gradle release tasks (jpackage)

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Append release-task block to `build.gradle.kts`**

Append to the end of `build.gradle.kts`:

```kotlin
// ── release / jpackage ────────────────────────────────────────────────────────

import java.nio.file.Files
import java.nio.file.StandardCopyOption

val appName: String = rootProject.name
val appVersion: String = version.toString().removeSuffix("-SNAPSHOT")
val currentOs: String = System.getProperty("os.name").lowercase()

val outputDir          = layout.projectDirectory.dir("output")
val localResourcesDir  = layout.projectDirectory.dir("local_resources")
val lastRunTmpDir      = layout.projectDirectory.dir("last_run_tmp")
val jpackageInputDir   = layout.buildDirectory.dir("jpackage/input")
val jpackageContentDir = layout.buildDirectory.dir("jpackage/content")
val jpackageOutputDir  = layout.buildDirectory.dir("jpackage/output")

val prepareLastRunTmp by tasks.registering(Copy::class) {
    group = "application"
    description = "Copy local_resources into last_run_tmp (preserves runtime-generated files)"
    from(localResourcesDir)
    into(lastRunTmpDir)
}

tasks.named<JavaExec>("run") {
    dependsOn(prepareLastRunTmp)
    workingDir = lastRunTmpDir.asFile
    systemProperty("app.dir", lastRunTmpDir.asFile.absolutePath)
}

val stageJpackageInput by tasks.registering(Copy::class) {
    group = "release"
    dependsOn(tasks.named("build"))
    from(configurations.named("runtimeClasspath"))
    from(tasks.named<Jar>("jar").map { it.outputs.files })
    into(jpackageInputDir)
}

val stageJpackageContent by tasks.registering(Copy::class) {
    group = "release"
    from(localResourcesDir)
    into(jpackageContentDir)
}

val ensureOutputDir by tasks.registering {
    group = "release"
    doLast { outputDir.asFile.mkdirs() }
}

fun jpackageCmd(type: String, destDir: File): List<String> {
    val args = mutableListOf(
        "jpackage",
        "--input",       jpackageInputDir.get().asFile.absolutePath,
        "--main-jar",    tasks.named<Jar>("jar").get().archiveFileName.get(),
        "--main-class",  application.mainClass.get(),
        "--name",        appName,
        "--app-version", appVersion,
        "--type",        type,
        "--dest",        destDir.absolutePath,
        "--java-options", "-Dapp.dir=\$APPDIR",
    )
    if (currentOs.contains("windows")) {
        val ico = jpackageContentDir.get().asFile.resolve("icon.ico")
        if (ico.exists()) { args += "--icon"; args += ico.absolutePath }
    }
    val contentItems = jpackageContentDir.get().asFile.listFiles()
    if (!contentItems.isNullOrEmpty()) {
        args += "--app-content"
        args += contentItems.joinToString(",") { it.absolutePath }
    }
    return args
}

val jpackageWindowsAppImage by tasks.registering(Exec::class) {
    group = "release"
    onlyIf { currentOs.contains("windows") }
    dependsOn(stageJpackageInput, stageJpackageContent)
    val destDir = jpackageOutputDir.map { it.dir("windows") }
    doFirst {
        destDir.get().asFile.let { it.deleteRecursively(); it.mkdirs() }
        commandLine(jpackageCmd("app-image", destDir.get().asFile))
    }
}

val zipWindowsRelease by tasks.registering(Zip::class) {
    group = "release"
    onlyIf { currentOs.contains("windows") }
    dependsOn(jpackageWindowsAppImage, ensureOutputDir)
    from(jpackageOutputDir.map { it.dir("windows") })
    archiveFileName.set("${appName}_${appVersion}_windows_x64.zip")
    destinationDirectory.set(outputDir)
}

val jpackageLinuxDeb by tasks.registering(Exec::class) {
    group = "release"
    onlyIf { currentOs.contains("linux") }
    dependsOn(stageJpackageInput, stageJpackageContent, ensureOutputDir)
    val destDir = jpackageOutputDir.map { it.dir("linux-deb") }
    doFirst {
        destDir.get().asFile.let { it.deleteRecursively(); it.mkdirs() }
        commandLine(jpackageCmd("deb", destDir.get().asFile))
    }
    doLast {
        val src = destDir.get().asFile.listFiles { f -> f.extension == "deb" }?.firstOrNull()
        val dst = outputDir.asFile.resolve("${appName}_${appVersion}_linux_x64.deb")
        if (src != null) Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

val jpackageLinuxRpm by tasks.registering(Exec::class) {
    group = "release"
    onlyIf { currentOs.contains("linux") }
    dependsOn(stageJpackageInput, stageJpackageContent, ensureOutputDir)
    val destDir = jpackageOutputDir.map { it.dir("linux-rpm") }
    doFirst {
        destDir.get().asFile.let { it.deleteRecursively(); it.mkdirs() }
        commandLine(jpackageCmd("rpm", destDir.get().asFile))
    }
    doLast {
        val src = destDir.get().asFile.listFiles { f -> f.extension == "rpm" }?.firstOrNull()
        val dst = outputDir.asFile.resolve("${appName}_${appVersion}_linux_x64.rpm")
        if (src != null) Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

val jpackageMacOsDmg by tasks.registering(Exec::class) {
    group = "release"
    onlyIf { currentOs.contains("mac") }
    dependsOn(stageJpackageInput, stageJpackageContent, ensureOutputDir)
    val destDir = jpackageOutputDir.map { it.dir("macos") }
    doFirst {
        destDir.get().asFile.let { it.deleteRecursively(); it.mkdirs() }
        commandLine(jpackageCmd("dmg", destDir.get().asFile))
    }
    doLast {
        val src = destDir.get().asFile.listFiles { f -> f.extension == "dmg" }?.firstOrNull()
        val dst = outputDir.asFile.resolve("${appName}_${appVersion}_macos_x64.dmg")
        if (src != null) Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

tasks.named("build") { mustRunAfter("clean") }

val release_all by tasks.registering {
    group = "release"
    description = "Clean + build + package current platform → ./output"
    dependsOn(
        tasks.named("clean"),
        tasks.named("build"),
        ensureOutputDir,
        zipWindowsRelease,
        jpackageLinuxDeb,
        jpackageLinuxRpm,
        jpackageMacOsDmg,
    )
}
```

- [ ] **Step 2: Smoke build per current OS**

Run: `./gradlew release_all`
Expected (Linux example): `BUILD SUCCESSFUL`, with `output/pifpafers_0.1.0_linux_x64.deb` and `.rpm` present.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "chore: add jpackage release tasks for win/linux/macos"
```

---

## Task 28: End-to-end smoke run + manual checklist

This task has no code. Walk through the app once to confirm everything is wired.

- [ ] **Step 1: Place a few sample PNGs**

Drop 2-3 small PNG files in `<user.home>/.neuroshima-editor/content/sample/` so the Assets panel has something to show.

- [ ] **Step 2: Smoke walkthrough**

Run: `./gradlew run`

Verify each item below in order:
- [ ] App window opens with all five panels populated.
- [ ] Tool palette has 6 toggle buttons; only one is active at a time.
- [ ] `+ Unit` adds a hex token; `+ Modifier` adds a round-shape token.
- [ ] Selecting a token in Tokens collection highlights it and shows it on canvas.
- [ ] Assets tree shows the bundled folder structure plus user-overlay content.
- [ ] Double-clicking an asset thumbnail adds it as the top layer of the active token.
- [ ] Layers panel shows that layer; selecting it makes the Properties panel show 8 spinners.
- [ ] Editing a spinner moves the layer on canvas; Ctrl+Z reverts.
- [ ] Mouse wheel zooms canvas; middle-drag pans; Ctrl+0 resets.
- [ ] Move tool drags the selected layer; Rotate tool rotates; Scale tool scales; Opacity tool fades; Colorize tool opens a color picker.
- [ ] Show overlay checkbox toggles the bundled overlay PNG.
- [ ] File → Save As writes a `.box`; reopen via File → Open restores state.
- [ ] Deleting an asset and re-opening the same `.box` raises the MissingAssetsDialog listing the path.
- [ ] Status bar shows file name, token count, dirty marker.
- [ ] Closing the window with unsaved changes triggers SaveBeforeCloseDialog.
- [ ] All sliders (collection, layers, assets) resize their thumbnails.
- [ ] Esc clears selection.

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 4: Commit final notes (if anything was tweaked during walkthrough)**

```bash
git status
# If anything is dirty:
git add -A
git commit -m "chore: tweaks from end-to-end smoke walkthrough"
```

---

## Completion check

After Task 28 you have:

- Full Swing UI per the source mockup.
- All commands flowing through `CommandHistory` with the 500 ms merge window.
- jpackage release artifacts per OS in `output/`.
- Architecture test enforcing all 8 boundary rules.
- A hand-validated end-to-end run.

Beyond this scope (separate later specs): print / PDF / mask-image export, in-app locale switcher, nearest-neighbor zoom sampling toggle.
