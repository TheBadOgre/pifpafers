# Neuroshima Hex Army Editor — Design

## Purpose

Desktop tool for designing custom Neuroshima Hex tokens. Tokens are composed by stacking pre-existing image assets (no drawing, no text editing inside the tool). One save file (`.box`) holds one army: hex unit tokens plus the round modifier tokens used with it. Future export step (out of scope here) will arrange tokens onto A4 pages and feed an external playtest tool.

## Goals / Non-Goals

**Goals**
- Compose hex (490 px edge) and modifier (490 px diameter) tokens from layered image assets.
- Per-layer transform (offset, rotation, scale), per-layer color (opacity, hue, saturation, brightness).
- Persist a bag of tokens as JSON; round-trip without loss.
- Bundled asset library plus a writable user overlay folder.
- Global undo/redo across all mutations.
- Localized UI (English default, Polish), locale picked from OS.
- Cross-platform packaging (Windows zipped app-image, Linux deb + rpm, macOS dmg) via jpackage.

**Non-Goals**
- Print / PDF / mask-image export (separate later spec).
- Pixel-level drawing, text rendering, vector authoring.
- Multi-token simultaneous editing.
- Network sync, cloud storage, multi-user.
- Game rules validation (HQ counts, faction composition).

## Domain Model

```
TokenBag
 ├─ name: String
 ├─ schemaVersion: Int
 └─ tokens: List<Token>           // order = collection-panel order

Token
 ├─ id: UUID                       // stable identity for command targets
 ├─ kind: UNIT | MODIFIER          // hex vs round, affects canvas shape and the add button source
 └─ layers: List<Layer>            // index 0 = bottom, last = top

Layer
 ├─ id: UUID
 ├─ assetPath: AssetPath           // sealed: BundledPath or UserPath
 └─ props: LayerProperties

LayerProperties
 ├─ offsetX: Int                   // px, relative to token center
 ├─ offsetY: Int
 ├─ rotation: Float                // degrees, normalized 0..360
 ├─ scale: Float                   // 1.0 = native
 ├─ opacity: Float                 // 0..1
 ├─ hue: Float                     // 0..1 shift
 ├─ saturation: Float              // 0..1 (1 = unchanged)
 └─ brightness: Float              // 0..1 (1 = unchanged)
```

Model classes are mutable POKOs. Each mutator method emits a `ModelChanged(targetId)` event through a lightweight observer. `AssetPath` is a sealed type — `BundledPath` vs `UserPath` — so the two roots can never be conflated.

## Persistence (.box JSON)

Single JSON document, pretty-printed via `kotlinx.serialization`, diff-friendly under git.

```json
{
  "schemaVersion": 1,
  "name": "My Army",
  "tokens": [
    {
      "id": "9b1...",
      "kind": "UNIT",
      "layers": [
        {
          "id": "4c2...",
          "asset": "bundled://backgrounds/hex_orange.png",
          "props": {
            "offsetX": 0, "offsetY": 0,
            "rotation": 0.0, "scale": 1.0,
            "opacity": 1.0,
            "hue": 0.0, "saturation": 1.0, "brightness": 1.0
          }
        }
      ]
    }
  ]
}
```

Rules:
- `schemaVersion` integer. Loader rejects unknown versions; future migrations branch on it.
- `asset` is a URI: `bundled://<relative path>` or `user://<relative path>`.
- UUIDs persisted.
- Save = atomic write (write `*.tmp`, then rename).
- Load = parse → validate `schemaVersion` → resolve every `asset` URI to a real file → if any missing, throw `MissingAssetsException(list)`, show single dialog listing all missing paths, abort. Per the source UI plan: hard fail on missing assets.
- On successful load: eagerly decode every referenced image into the LRU cache off-EDT (progress bar).
- File extension: `.box`.

## Asset Library

Two roots merged into one virtual tree:

```
bundled://   →  <app>/assets/                                  (read-only, ships with app)
user://      →  <user_home>/.neuroshima-editor/content/        (writable, created on first launch)
```

- Tree shown in Assets panel = union of both, grouped by relative subpath. Each leaf carries an `AssetPath`.
- **Conflict rule**: when the same relative path exists in both roots, bundled wins; user copy is hidden and a warning is logged. This keeps bags portable: same relative path means the same image across machines that ship the same bundle.
- Tree is built lazily, expanded on demand.
- **Refresh content** button rescans user root (bundled is immutable per session). Bundled scan happens once at startup.
- **Recognized formats**: PNG only (alpha required). Other files in the folder are ignored and logged.

**Library → canvas interactions**
- Double-click asset → adds a new `Layer` on top of the active token, centered, default props.
- Drag asset onto canvas → same effect (drop position ignored; matches the source UI plan).
- Selecting an asset in the tree highlights it; preview tooltip shows path + image dimensions.

No file-system watcher; refresh is explicit.

## Commands and History

The plan's "Action" interface = `Command`:

```kotlin
interface Command {
    val label: String
    fun execute(model: AppModel)
    fun undo(model: AppModel)
    fun mergeWith(next: Command): Command?   // null = don't merge
}
```

Catalog:
- `AddTokenCommand(kind, atIndex)`
- `RemoveTokenCommand(tokenId)`
- `AddLayerCommand(tokenId, assetPath, atIndex)`
- `RemoveLayerCommand(tokenId, layerId)`
- `DuplicateLayerCommand(tokenId, layerId)`
- `ReorderLayerCommand(tokenId, layerId, newIndex)`
- `SetLayerPropertyCommand(tokenId, layerId, prop, oldValue, newValue)` — one per property field; `mergeWith` collapses consecutive same-`prop` edits on the same `layerId`.
- `MultiLayerPropertyCommand(targets, prop, deltaOrAbsolute)` — issued by tool drags that affect multiple selected layers.

**History**
- `CommandHistory` holds `done` and `undone` stacks. Executing a new command clears `undone`.
- Merge window: 500 ms. If `now - lastCommandTimestamp < 500ms` and `last.mergeWith(new) != null`, replace top of `done` with the merged result. Prevents thousand-entry slider-drag spam.
- Drag-end (mouse-up on slider/canvas) finalizes the merge boundary.
- History is cleared on Save and on Load.
- Undo that targets a token other than the currently active one auto-selects that token first.
- Bindings: Ctrl+Z (undo), Ctrl+Y (redo). Menu items disabled when the stack is empty.

## Rendering

**Canvas (`TokenCanvasPanel : JPanel`)**
- Overrides `paintComponent(g: Graphics2D)`.
- Logical size 490 px (hex edge or circle diameter). Zoom factor maps logical px → screen px.
- Paint pipeline (each repaint):
  1. Fill background (neutral gray).
  2. For each `Layer` bottom→top:
     - Fetch `BufferedImage` from `ImageCache.get(assetPath)`.
     - When HSB ≠ identity OR opacity ≠ 1, apply ops once into a transient working image: `RescaleOp` for brightness / saturation / opacity-as-alpha; `LookupOp` for hue shift (HSB-conversion LUT precomputed).
     - Concatenate `AffineTransform`: translate to center + offset, rotate, scale, translate by `-imgW/2, -imgH/2`.
     - `g.drawImage(working, xform, null)`.
  3. If `showOverlay`, draw the bundled `HEX_template_lines.png` centered.
  4. If a tool that shows selection chrome is active, draw per-layer bounding box / rotation handle.
- HSB ops cached per `(assetPath, props.hash())` — pan/zoom that doesn't change props doesn't recompute pixels.

**Canvas zoom and pan**
- Mouse wheel = zoom, anchored at cursor (zoom-to-cursor). Step ≈ 1.1×. Bounds 0.25× – 8×.
- Ctrl+0 (or a fit button) resets to 1:1 / fit-to-panel.
- Middle-mouse drag = pan (does not conflict with tool drags).
- Zoom and pan are view state; they live outside the model, are not undoable, and are not saved.
- High-zoom sampling: bicubic by default.

**Size sliders (per-panel thumbnails)**
- Three independent sliders: Tokens collection, Current token layers, Assets library. Each scales its own thumbnail size, range 48–192 px.
- Slider value is view state, not in the model, not undoable. Persisted in user preferences (`prefs` package), restored next launch.
- Thumbnails re-rendered when the slider settles (debounce 100 ms). LRU cache keyed by `(targetId, sizePx, contentHash)`.
- One `ThumbnailRenderer` service serves all three panels; size is a parameter.

**Token thumbnails (collection panel)**
- Default 96×96 px (overridden by slider). Re-rendered off-EDT, swapped in via `SwingWorker.publish`. Invalidated whenever any of the token's layers/props change.

**Image cache**
- `ImageCache`: `Map<AssetPath, SoftReference<BufferedImage>>`, LRU-bounded (≈ 256 entries).
- Preloaded on bag open per the eager strategy.
- Decoding always off-EDT.

## UI Layout

**Top bar**
- File menu: Save (Ctrl+S), Save As, Undo (Ctrl+Z), Redo (Ctrl+Y), Print (stub).
- Window title shows `<bag name>` plus a `*` dirty marker.

**Left strip: Tool palette**
- Six radio buttons: select, move, rotate, scale, opacity, colorize.
- One active at a time. Active tool installs the canvas mouse handler and cursor.

**Left panel: Tokens collection**
- Scrollable grid of token thumbnails. Single list; UNIT vs MODIFIER is visible from shape.
- Buttons at bottom: `+ Unit`, `+ Modifier`. Each creates a new empty token (no layers) of that kind, appended to the bag, and selects it.
- Remove appears on hover or via context menu; confirmation dialog.
- Size slider at bottom.

**Center: Canvas**
- Token shape outline drawn.
- Helper overlay toggle (`☑ show overlay`) below canvas.

**Right top: Current token layers**
- Vertical list, top of list = top layer in z-order.
- Each row: thumbnail + multi-select state (Ctrl+click adds, Esc clears).
- Right-edge per-row buttons: remove layer, drag handle (reorder by drag).
- Size slider at bottom.

**Right bottom: Layer properties**
- Visible only when exactly one layer is selected (per plan).
- Fields: offsetX, offsetY, rotation, opacity, scale, hue, saturation, brightness.
- Each field = spinner or slider + number. Edit fires `SetLayerPropertyCommand` (debounced / merged).

**Bottom: Assets library**
- Left: folder tree (bundled + user merged).
- Right: grid of asset previews for the selected folder.
- Refresh content button.
- Size slider.

**Status bar**
- `loaded file: <name>`, `tokens: <count>`, `last saved: <timestamp>`.

**Dirty-state guard**
- Closing or loading another bag with unsaved changes triggers a "Save before closing?" dialog (per plan).

## Internationalization

- `LocaleService` singleton. Loads `ResourceBundle` from `i18n/messages.properties` (default = English) plus `messages_pl.properties`.
- Locale picked at startup from `Locale.getDefault()`. Falls back to English if no bundle matches.
- Every user-visible string accessed via `i18n("key.path")`. No string literals in UI code.
- Folder/asset names are file paths and are not translated.
- Error messages (e.g. `MissingAssetsException`) are localized via the same bundle.

## Project Setup and Packaging

Mirrors the conventions of the `ojkipojki` project (sibling tool).

**Build**
- `kotlin("jvm") 2.3.10`, `kotlin("plugin.serialization")`, `application` plugin.
- `mainClass = "net.rafkos.neuroshima.editor.MainKt"`.
- `jvmToolchain(21)`.
- Dependencies:
  - `org.jetbrains.kotlinx:kotlinx-serialization-json`
  - `org.apache.logging.log4j:log4j-api` + `:log4j-core`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-core`
  - test: `junit-jupiter`, `kotlin-test-junit5`, `mockito-core`, `mockito-kotlin`, `junit-platform-launcher` (runtime only)

**Directory convention**
```
src/main/resources/                 # classpath; loaded via ClassLoader
 ├─ i18n/messages.properties
 ├─ i18n/messages_pl.properties
 └─ log4j2.xml

local_resources/                    # committed; bundled content (user-visible files at runtime)
 ├─ assets/                          # → maps to bundled:// root
 │   ├─ backgrounds/
 │   ├─ units/
 │   ├─ modifiers/
 │   └─ icons/
 ├─ overlay/HEX_template_lines.png
 └─ icon.ico
last_run_tmp/                       # gitignored; dev `run` copies local_resources here
output/                             # gitignored; release artifacts
```

`src/main/resources/` content is on the classpath in both dev and packaged builds — `LocaleService` and log4j config load from there. `local_resources/` is for things the user must see on disk (assets folder for adding their own files, overlay image, installer icon).

- Dev `run` task: `prepareLastRunTmp` copies `local_resources` → `last_run_tmp`; `run.workingDir = last_run_tmp`. Runtime-generated files (preferences, autosaves) don't dirty the repo.
- Bundled-assets path at runtime: `-Dapp.dir=$APPDIR` (jpackage) or `user.dir` (dev).
- User-overlay path: `<user_home>/.neuroshima-editor/content`, created on first launch.

**jpackage release tasks (per platform; `onlyIf` gated)**
- `stageJpackageInput`: runtime classpath + main jar → `build/jpackage/input`.
- `stageJpackageContent`: copies `local_resources` → `build/jpackage/content`, each top-level item passed as its own `--app-content` so it lands at app root.
- `jpackageWindowsAppImage` + `zipWindowsRelease` → `output/<name>_<version>_windows_x64.zip`.
- `jpackageLinuxDeb`, `jpackageLinuxRpm` → `output/<name>_<version>_linux_x64.{deb,rpm}`.
- `jpackageMacOsDmg` → `output/<name>_<version>_macos_x64.dmg`.
- `release_all` umbrella task: clean → build → all platform tasks; only the matching OS's task actually runs.

## Module Structure

Single Gradle module. Packages by concern:

```
net.rafkos.neuroshima.editor
 ├─ app/                    // Main, AppContext wiring
 ├─ model/                  // Token, Layer, LayerProperties, TokenBag, AssetPath
 ├─ persistence/            // JsonBagStore (save/load), schema-migration hook
 ├─ assets/                 // AssetLibrary, BundledRoot, UserRoot, AssetTreeNode, ImageCache
 ├─ command/                // Command interface, CommandHistory, concrete commands
 ├─ render/                 // LayerRenderer (HSB/alpha ops), AffineBuilder, ThumbnailRenderer
 ├─ ui/
 │   ├─ canvas/             // TokenCanvasPanel, ZoomController, OverlayPainter
 │   ├─ panels/             // TokensCollectionPanel, LayersPanel, LayerPropertiesPanel,
 │   │                     //   AssetsLibraryPanel, ToolPalettePanel
 │   ├─ dialogs/            // MissingAssetsDialog, SaveBeforeCloseDialog, ConfirmDeleteDialog
 │   └─ tools/              // SelectTool, MoveTool, RotateTool, ScaleTool, OpacityTool, ColorizeTool
 ├─ i18n/                   // LocaleService, i18n() helper
 ├─ prefs/                  // UserPreferences (panel slider sizes, last opened bag)
 └─ util/                   // logging, geometry helpers
```

**Boundaries (enforced by `PackageBoundaryTest` — see Testing).**
- `model` has no Swing or AWT imports. Pure data + observers.
- `command` depends on `model` only. Concrete commands hold ids and look up via the model.
- `ui` depends on everything else. Other layers never import `ui`.
- `render` may use `java.awt` (Graphics2D, AffineTransform, RescaleOp) but not Swing widgets.

## Testing

Mirrors the structure of the `ojkipojki` test tree, including its architecture test.

**Unit tests** (junit-jupiter + mockito-kotlin)
- `model`: mutation events fire correctly; UUIDs stable; `AssetPath` sealed-type round-trip.
- `JsonBagStore`: round-trip equality; rejection of unknown `schemaVersion`; missing-asset detection lists every missing path.
- `CommandHistory`: do / undo / redo invariants; merge window collapses consecutive same-prop edits; Save and Load clear history.
- `AssetLibrary`: merge rule (bundled wins on conflict); Refresh re-scans only the user root.
- `LayerRenderer`: HSB / opacity op correctness via pixel sampling on tiny PNG fixtures.
- `ImageCache`: LRU eviction; soft-reference behavior.

**Integration tests**
- Open a sample `.box`, render each token to a `BufferedImage`, compare against committed reference PNGs (small per-pixel delta tolerance). Catches rendering regressions.

**Architecture test** (`net.rafkos.neuroshima.editor.architecture.PackageBoundaryTest`)
- Mirrors the `ojkipojki` `PackageBoundaryTest` — same `@TestFactory` / `DynamicTest` approach: scan `src/main/kotlin`, parse `package` and `import` lines, fail when any file in `source` package imports a prefix in `forbidden`.
- Rules (`BASE = "net.rafkos.neuroshima.editor"`):

```
R1: $BASE.model      forbid: javax.swing, java.awt          allow: java.awt.geom
R2: $BASE.command    forbid: javax.swing, java.awt, $BASE.ui
R3: $BASE.persistence forbid: javax.swing, java.awt, $BASE.ui, $BASE.command
R4: $BASE.assets     forbid: javax.swing, $BASE.ui, $BASE.command   allow: java.awt.image
R5: $BASE.render     forbid: javax.swing, $BASE.ui, $BASE.command   allow: java.awt
R6: $BASE.i18n       forbid: javax.swing, java.awt, $BASE.ui, $BASE.model, $BASE.command
R7: $BASE.prefs      forbid: javax.swing, java.awt, $BASE.ui, $BASE.model
R8: $BASE.ui.tools   forbid: $BASE.persistence
```

**Out of scope for this iteration**
- Swing UI tests (panel/tool interactions). Manual smoke list documented separately.

**Logging**
- log4j-core config in `local_resources/log4j2.xml`. Console + rolling file in user home.
- Levels: model/command at INFO; render/cache at DEBUG; missing-asset at ERROR.

## Open Items (deferred specs)

- Print / export: A4 auto-arrangement preview, PNG sheet export, mask-image export for the external playtest tool. User will provide detailed description later.
- Optional in-app locale switcher (currently picked from OS only).
- Nearest-neighbor sampling toggle for pixel-art assets at high zoom.
