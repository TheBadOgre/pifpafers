# Neuroshima Hex Army Editor — Agent Guide

Compact, self-contained orientation. There is **no `docs/` directory** — this file plus the code is the source of truth. **Update this file whenever an architectural rule, key invariant, package, command, model field, or significant component changes.**

## Purpose

Desktop Swing tool for composing **Neuroshima Hex** custom tokens by stacking pre-existing PNG image assets. One `.box` file holds one army = unit tokens (hexagonal shape) + modifier tokens (round shape). Supports **print/PDF export** via the Publish dialog (Ctrl+P).

No drawing, no text rendering, no game-rules validation. Just layered image composition with per-layer transform (offset/rotation/scale) and per-layer color (opacity/hue/saturation/brightness/colorize).

## Tech Stack

- **Kotlin 2.3.10 / JVM 21**, single Gradle module, `application` plugin.
- Swing/AWT UI (`Graphics2D`, `BufferedImage`, `AffineTransform`, `RescaleOp`).
- `kotlinx.serialization` (JSON persistence), `kotlinx.coroutines`, `log4j2`.
- `org.apache.pdfbox:pdfbox:3.0.3` — PDF export (raster-embed via LosslessFactory).
- Test: `junit-jupiter`, `kotlin-test-junit5`, `mockito-core`, `mockito-kotlin`.
- Packaging: `jpackage` (Win zip, Linux deb/rpm, macOS dmg).
- Entry point: `net.rafkos.neuroshima.editor.app.MainKt`.

## Architecture (MVC + Command)

**Hard rule: UI components only display state. All mutations go through `Command` objects executed via `CommandHistory`. Never mutate the model from a UI event handler.**

```
ui/ ──► command/ ──► model/        (events bubble back through observers)
ui/ ──► render/  ──► assets/
ui/ ──► persistence/, prefs/, i18n/
```

### Package boundaries (enforced by `architecture/PackageBoundaryTest`)

`BASE = net.rafkos.neuroshima.editor`

| Rule | Package       | Forbidden imports                                   | Allowed exception |
|------|---------------|-----------------------------------------------------|-------------------|
| R1   | `model`       | `javax.swing`, `java.awt`                           | `java.awt.geom`   |
| R2   | `command`     | `javax.swing`, `java.awt`, `ui`                     | —                 |
| R3   | `persistence` | `javax.swing`, `java.awt`, `ui`, `command`          | —                 |
| R4   | `assets`      | `javax.swing`, `ui`, `command`                      | `java.awt.image`  |
| R5   | `render`      | `javax.swing`, `ui`, `command`                      | `java.awt`        |
| R6   | `i18n`        | `javax.swing`, `java.awt`, `ui`, `model`, `command` | —                 |
| R7   | `prefs`       | `javax.swing`, `java.awt`, `ui`, `model`            | —                 |
| R8   | `ui.tools`    | `persistence`                                       | —                 |
| R9   | `publish`     | `javax.swing`, `ui`, `command`                      | —                 |

`util/`, `app/`, `render/overlay`, and `ui` subpackages other than `tools` are unconstrained. **Crossing boundaries → update both `PackageBoundaryTest` rules and this table.**

### Package map

```
app/         Main, AppContext (DI/wiring + bag/history/imageCache/processedCache/previewService/canvasMapper/clipboard), AppDirs, LayerClipboard
model/       Token, Layer, LayerProperties, LayerSnapshot, TokenBag, AssetPath (sealed: Bundled|User), ModelEvent
persistence/ JsonBagStore (.box save/load, atomic write), BagOpener, BagDto, MissingAssetsException
assets/      AssetLibrary (bundled+user merged tree), ImageCache (LinkedHashMap LRU + SoftReference, bound 256),
             ImagePreloader, AssetTreeNode
command/     Command interface, CommandHistory, concrete commands (see list below)
render/      LayerRenderer (RescaleOp + per-pixel HSB for non-colorize; delegates colorize),
             AffineBuilder, TokenRenderer (fit-scale canvas → sizePx; sameSides folds BACK→FRONT),
             ThumbnailRenderer (TokenKey | LayerKey cache), ProcessedLayerCache (bound 256),
             render/color/    OkLab (sRGB↔linear↔OKLab/OKLCh), ColorizePipeline (perceptual recolor)
             render/overlay/  OverlayPainter etc.
publish/     PhysicalSize (mm↔px helpers), MaskIdCodec (encode/decode mask colour),
             PagePlan (data class: pageIndex, isBackPage, widthPx, heightPx, placements),
             PageLayoutPlanner (row-based greedy + honeycomb offset), PageRenderer (high-DPI per-token),
             MaskRenderer (maskId→unique colour, black BG), PageRasterizer (white-BG compositor),
             PngExporter (front/back/mask PNGs, atomic write), PdfExporter (PDFBox, one page per PagePlan),
             ExportResult (written/skipped/failed counts)
ui/          MainFrame, MenuBuilder, StatusBar (dirty marker `*` lives here, not in title), ViewState, WrapLayout
ui/canvas/   TokenCanvasPanel (paint pipeline + composite cache), CanvasMapper (screen↔logical)
ui/panels/   TokensCollectionPanel, LayersPanel, LayerPropertiesPanel, AssetsLibraryPanel, ToolPalettePanel
ui/preview/  PreviewService (debounced 300 ms HQ snapshot for selected token)
ui/publish/  PublishingDialog (modal, Settings/Export JMenuBar), PagePreviewPanel
ui/tools/    Tool interface, ToolController, Select/Move/Rotate/Scale/Colorize tools; OpacityTool (object, dialog-based, not a canvas Tool)
ui/dialogs/  MissingAssetsDialog, SaveBeforeCloseDialog, ColorizeDialog, OpacityDialog
ui/icon/     Icons
i18n/        LocaleService (ResourceBundle, English default + Polish, OS-picked)
prefs/       UserPreferences (thumb sizes + last paths), PrefsStore
             UserPreferences fields: collectionThumbSize, layersThumbSize, assetsThumbSize,
             lastFile (auto-loaded on startup), lastBoxDir (Open/Save/SaveAs), lastImagesDir (PNG export), lastPdfDir (PDF export)
util/        Logging, geometry helpers
```

## Domain Model

```
TokenBag(name: String, schemaVersion: Int, tokens: MutableList<Token>, printSettings: PublishSettings)
  Token(id: UUID, kind: TokenKind { UNIT, MODIFIER }, maskId: Int, sameSides: Boolean) // two sides per token
    front: MutableList<Layer>                                    // accessed via layers(FRONT)
    back:  MutableList<Layer>                                    // accessed via layers(BACK)
      Layer(id: UUID, assetPath: AssetPath, props: LayerProperties)
        LayerProperties(
          offsetX: Int, offsetY: Int,        // px, relative to token center
          rotation: Float,                    // degrees 0..360
          scale: Float,                       // 1.0 = native
          opacity: Float,                     // 0..1
          hue: Float,                         // 0..1 shift
          saturation: Float,                  // 0..1 (1 = unchanged)
          brightness: Float,                  // 0..1 (1 = unchanged)
          colorize: Boolean = false           // when true, hue/sat applied as tint not shift
        )                                                        // unchanged
```

- All ids are UUIDs and **persisted** for stable command targets.
- `TokenSide { FRONT, BACK }` — every layer-targeting `TokenBag` method, `ModelEvent`, and `Command` carries an explicit `side`. Default side after token creation/activation is `FRONT`.
- Mutators emit `ModelEvent` (`NameChanged`, `TokenAdded`, `TokenRemoved`, `TokensReordered`, `LayerAdded`, `LayerRemoved`, `LayerReordered`, `LayerPropsChanged`, `MaskIdAssigned`, `SameSidesChanged`, `PrintSettingsChanged`) through a lightweight observer; renderers/thumbnails/preview/canvas listen. Layer events carry a `side: TokenSide` field.
- `Token.maskId` — unique non-negative integer per token, stable across undo/redo (assigned by `AddTokenCommand`, copied-fresh by `DuplicateTokenCommand`). Encoded by `MaskIdCodec` as `0xFFFFFF - maskId` for mask images (0 → white).
- `Token.sameSides` — when `true`, `TokenRenderer` (and `PageRenderer`) render FRONT layers for both sides. `SetSameSidesCommand` toggles it and auto-flips active side to FRONT. The LayersPanel shows locked state (buttons disabled) when viewing BACK with sameSides=true.
- `TokenBag.printSettings: PublishSettings` — DPI, page format, invertBackSide, renderOverlay. Mutated via `updatePrintSettings()`; fires `PrintSettingsChanged` but does **not** mark the bag dirty (PublishingDialog owns this without pushing to history).
- `AssetPath` is sealed (`AssetPath.Bundled` / `AssetPath.User`) — the two roots never conflate.

## Real world assumptions
This section hold information mostly about the army after printing.

### Real world dimensions
- The hexagonal token has 6 edges of 1,85cm each when printed/physically (regular hexagon). The longest diagonal is 3,7cm.
- The circular token has a diameter of 1,85cm when printed/physically.

### Real world tips
- Each token has a bleeding/overlay when printed, this is to make sure no "white" color is visible if token was slightly misaligned during cutting.

## Persistence (`.box` JSON)

- Pretty-printed JSON via `kotlinx.serialization`, diff-friendly under git.
- `CURRENT_SCHEMA_VERSION = 3` (`JsonBagStore`). Loader rejects unknown versions. **No backwards compatibility. v1/v2 files are rejected with `SchemaVersionException`.**
- Asset URI scheme: `bundled://<rel>` or `user://<rel>`.
- File extension: `.box`. `JFileChooser` in open/save-as shows only `.box` files.
- **Atomic save**: write `.tmp` then `Files.move(..., ATOMIC_MOVE)`.
- **Hard-fail on load** if any referenced asset is missing: `BagOpener` collects every missing path, shows `MissingAssetsDialog`, aborts. No partial load.
- On successful load: eager preload of every referenced image into `ImageCache` off-EDT.
- **Save and Load both clear `CommandHistory`** (wired in `MenuBuilder` save/saveAs and `AppContext.openBag`).
- v3 adds: `maskId: Int` and `sameSides: Boolean` on each token; top-level `printSettings` object (dpi, pageFormat, invertBackSide, renderOverlay).
- Example shape:
  ```json
  { "schemaVersion": 3, "name": "My Army",
    "printSettings": { "dpi": 300, "pageFormat": "A4", "invertBackSide": false, "renderOverlay": true },
    "tokens": [{ "id": "...", "kind": "UNIT", "maskId": 0, "sameSides": false,
      "front": [{ "id": "...", "asset": "bundled://units/grunt.png",
        "props": { "offsetX": 0, "offsetY": 0, "rotation": 0.0, "scale": 1.0,
                   "opacity": 1.0, "hue": 0.0, "saturation": 1.0, "brightness": 1.0,
                   "colorize": false } }],
      "back": [] }] }
  ```

## Assets

Two roots merged into one virtual tree:
- `bundled://` → `<app>/assets/` (read-only, ships with app). Scanned once at startup.
- `user://` → on Windows: `<install_root>/content/` (next to `assets/`); other OSes: `<user_home>/.neuroshima-editor/content/`. Writable, created on first use. Path resolved by `AppDirs.userContentRoot`. **Refresh content** button rescans.
- **Conflict rule**: bundled wins; user copy hidden + warning logged. Same relative path = same image across machines.
- **PNG only** (alpha required). Other files ignored + logged.
- Double-click or drag → adds new `Layer` on top of active token, centered, default props. Drop position ignored.
- No file-system watcher; refresh is explicit.

## Commands / History

`Command` interface: `label`, `execute(model)`, `undo(model)`, `mergeWith(next): Command?`.

`CommandHistory` has `done` / `undone` ArrayDeque stacks. Executing a new command clears `undone`.

- **Merge window 500 ms** (`CommandHistory` constructor default). Consecutive same-`prop` edits on the same `layerId` collapse via `mergeWith` → prevents slider-drag history spam. Drag-end finalizes by issuing a no-merge boundary command.
- Save/Load clear history.
- Undo targeting a non-active token auto-selects that token first.
- Bindings: `Ctrl+Z` undo, `Ctrl+Y` redo. Menu items disabled when the stack is empty.

Concrete commands on disk:
`AddTokenCommand`, `RemoveTokenCommand`, `DuplicateTokenCommand`, `AddLayerCommand`, `RemoveLayerCommand`, `DuplicateLayerCommand`, `ReorderLayerCommand`, `SetLayerPropertyCommand` (one per `LayerProperty` enum value), `MultiLayerPropertyCommand` (tool drags affecting multi-selected layers), `ColorizeCommand`, `SetSameSidesCommand`, `PasteLayersCommand`.

Two-sided notes:
- Every layer-targeting command (`AddLayerCommand`, `RemoveLayerCommand`, `DuplicateLayerCommand`, `ReorderLayerCommand`, `SetLayerPropertyCommand`) takes a `side: TokenSide` parameter.
- `MoveLayerCommand.Target` and `MultiLayerPropertyCommand.Target` each include a `side` field so multi-select operations across the active side are scoped correctly.
- `ColorizeCommand` is per-side: it records the original and final props for layers on a single `TokenSide`.
- `RemoveTokenCommand` and `DuplicateTokenCommand` snapshot/restore both sides in full.

## Rendering

**Logical canvas dimensions** (`TokenRenderer.LOGICAL_CANVAS_W = 1044`, `LOGICAL_CANVAS_H = 902`; center `522.0, 451.0` in `CanvasMapper`). These are the print-area logical pixels; the visible token shape (hex / circle) sits centered within and the bleed/overlay extend to the full rectangle.

- Zoom 0.25×–8× (wheel zoom-to-cursor, step ≈ 1.1×). Middle-mouse pan. Zoom/pan are **view state** — not modeled, not undoable, not saved.
- **`TokenCanvasPanel.paintComponent` MUST use `graphics.create()` + `try/finally g2.dispose()`** — otherwise transforms/clips leak across sibling Swing panels. Regression-prone.
- `TokenCanvasPanel` keeps a `LOGICAL_CANVAS_W × LOGICAL_CANVAS_H` **composite cache** per active token, invalidated only by `ModelEvent`s touching the active token (`LayerAdded`/`Removed`/`Reordered`/`PropsChanged`). Zoom, pan, selection change, overlay toggle do **not** invalidate — they only `repaint()`.
- `LayerRenderer.applyPixelOps`: identity short-circuit; `RescaleOp` for opacity (and for non-colorize brightness); **per-pixel HSB loop** (`Color.RGBtoHSB`/`HSBtoRGB`) for non-colorize saturation/hue. `colorize` mode is delegated to `render/color/ColorizePipeline` — a perceptual recolor in OKLab/OKLCh: sRGB→linear (8-bit LUT) → OKLab → scale L by `brightness`, replace H, derive C via luminance-bell × soft-knee (tanh) × source-chroma material modulation → mild S-curve on L → linear → sRGB. When `colorize=true` the per-pixel pipeline owns brightness (perceptual-L scale, not sRGB RGB multiply), so `LayerRenderer` skips the post-pipeline brightness `RescaleOp`. Slider semantics: `hue` ∈ 0..1, `saturation` is the colorize amount (tool maps chooser HSB saturation × 2 → "full" recolor at chooser sat=1), `brightness` is the perceptual-L multiplier (chooser HSB brightness passed through directly). (Vectorizing the non-colorize HSB loop via `LookupOp`/`BandCombineOp` remains aspirational.)
- `ProcessedLayerCache` keyed by `(assetPath, props.hash())`, bound 256.
- `TokenRenderer.render(token, side, sizePx)` fit-scales `LOGICAL_CANVAS_W × LOGICAL_CANVAS_H` → `sizePx × sizePx` via uniform `g.scale(fit)` so thumbnails show the whole composition correctly regardless of layer offsets.
- `TokenRenderer.renderDual(token, sizePx)` — square composite used by the tokens-collection thumbnail and the active-token preview snapshot. Front is placed at top-left, back at bottom-right; scale is 80% for UNIT tokens and 55% for MODIFIER tokens (both sides). No outline decoration.
- `ThumbnailRenderer` has separate `TokenKey(tokenId, sizePx)` (→ dual composite) and `LayerKey(tokenId, side, layerId, sizePx)` cache entries (sealed `Key`); `layerThumbnail` builds a transient one-layer `Token` internally — its synthetic id is never used as a cache key. `invalidateToken` drops both.
- `PreviewService` (singleton `ScheduledExecutorService`, 300 ms debounce, per-key `AtomicLong` version counter — stale results discarded) renders an HQ snapshot only for the **selected** token tile; `SwingUtilities.invokeLater` swaps the `ImageIcon` on EDT.
- All image decoding off-EDT. `ImageCache` = `LinkedHashMap<AssetPath, SoftReference<BufferedImage>>`, LRU bound 256.
- **Selection feedback**: diagonal `//` stripe overlay (`selectionOverlayOf` — `TexturePaint` with 10×10 tile, 4px blue `0x0055ff` stripes, gaps transparent, alpha-preserving via `DstIn`) composited at `AlphaComposite.SRC_OVER, 0.7f` over selected layers. Gaps show original layer color through. Suppressed when `viewState.suppressSelectionTint == true` (e.g., while the colorize dialog is open). Not a dashed marker (legacy design — replaced).

## UI Layout

Five regions:
- **Top bar**: rename token field (commits on Enter / focus-lost → rename command, undoable) · Save (`Ctrl+S`) · Save As · Publish (`Ctrl+P`, opens `PublishingDialog`).
- **Left strip**: 4 mutually-exclusive tool radio buttons — select, move, rotate, scale — plus two one-shot push buttons (below a separator): colorize and opacity, each opening a modal dialog. Active tool installs canvas mouse handler + cursor.
- **Left panel — Tokens collection**: scrollable grid; thumbnail shape mirrors `kind` (hex / circle). `+ Unit` / `+ Modifier` buttons + size slider on the same row at the bottom. Previews are 1:1 square dual-side composites (front top-left, back bottom-right); MODIFIER tokens render both sides at 55% scale, UNIT tokens at 80%.
- **Center — Canvas**: A localized "side: front / back" label sits in the top-left corner of the canvas region (above the canvas). The canvas south panel hosts the `☑ show overlay` toggle, a **☑ Same sides** checkbox (`SetSameSidesCommand`, disables the Flip button when checked, forces active side to FRONT), and a **Flip side** button. Switching side clears the layer selection. When no token is selected, only the dark-grey background paints.
- **Right top — Current token layers**: vertical list showing only the layers of the **active side**, **top of list = topmost in z-order**. Each row: layer thumbnail + up/down/duplicate/remove buttons. Multi-select (Ctrl+click / Esc to clear). Row border is a constant 2px on both selected and unselected states so the row height does not jitter when selection toggles. Side switch implicitly clears selection.
- **Right bottom — Layer properties**: visible **only when exactly one layer is selected**. Eight fields + `colorize` toggle. Each edit fires `SetLayerPropertyCommand` (merge-window collapsed). **Reset to defaults** button is wrapped in a panel with a `(top=8)` `EmptyBorder` for visual separation from the spinner grid. When >1 layer is selected, shows `label.multi.layer` message instead of fields.
- **Bottom — Assets library**: folder tree (merged bundled+user) · grid of asset previews (white background fill so transparent PNGs are visible) · **Refresh content** button (rescans `user://` only) · size slider. Hand cursor on hover.
- **Status bar**: `loaded file: <name>` · `tokens: N (U units, M modifiers)` · lafix st-saved timestamp · **dirty marker `*`** (the asterisk lives here, not in the window title).

Three independent thumbnail size sliders (tokens / layers / assets), range **48–192 px** each. View state, not undoable, persisted in `UserPreferences` via `PrefsStore`.

### Confirmed UI invariants

- **Token removal**: confirmation dialog mandatory (`JOptionPane.showOptionDialog` in `TokensCollectionPanel`). Undoable.
- **Layer removal**: confirmation dialog also present (`LayersPanel`). Undoable via `RemoveLayerCommand`. *(Note: original design called for no-confirm on layers since undo covers it — code currently confirms. If you change this, update here.)*
- **Rename** = command (undoable), not a direct mutation.
- **Save-before-close** dialog (`SaveBeforeCloseDialog`) on window-close and on opening another bag when current is dirty.
- **Colorize dialog** (`ColorizeTool`) previews live: slider changes directly mutate selected layers' props via `bag.updateLayerProps` (bypasses history). Dialog has three sliders: hue, saturation, brightness. Selection blue tint is suppressed while the dialog is open. OK reverts the previewed state, then pushes a single `ColorizeCommand` (original → final, full `LayerProperties` snapshot) onto history; Cancel/close reverts silently.
- **Opacity dialog** (`OpacityTool` object) previews live: single opacity slider (0–100%). OK pushes `MultiLayerPropertyCommand(OPACITY, targets)` onto history. `opacityValue` field name used (avoids `JDialog.getOpacity()` JVM clash). Cancel reverts silently.

## i18n

- `LocaleService` loads `i18n/messages.properties` (English default) + `messages_pl.properties`. OS-locale picked at startup; falls back to English.
- **Rule: no user-visible string literals in UI code** — always `ctx.locale.t("key")`. When adding a UI string, add the key to **both** `.properties` files.
- File paths / asset names are not translated.
- `ui/dialogs/*` dialogs must take either a `LocaleService` or pre-resolved strings — never embed literals.
- Every text visible in UI must be localized.

## Testing — **High Importance**

`./gradlew test` must stay green on every PR.

- **`PackageBoundaryTest`** (architecture test, `@TestFactory` over `src/main/kotlin`): scans every `.kt`, parses `package` + `import` lines, fails on forbidden cross-package imports. **Any new package or boundary-rule change must update this test alongside the table above.**
- **Unit tests** colocated by package under `src/test/kotlin/...`:
  - `model/` — mutation events, UUID stability, `AssetPath` sealed-type round-trip.
  - `persistence/` — `JsonBagStore` round-trip, unknown-`schemaVersion` rejection, `BagOpener` missing-asset enumeration.
  - `command/` — every command's do/undo invariants, `CommandHistory` merge window, save/load history-clear.
  - `assets/` — `AssetLibrary` bundled-wins conflict rule, `ImageCache` LRU eviction + soft-reference behaviour, `ImagePreloader`.
  - `render/` — `LayerRenderer` non-colorize paths vs a legacy reference (`LayerRendererLegacy`) with ≤2/255 per-channel delta on a fixture; `render/color/` covers `OkLab` round-trip and `ColorizePipeline` invariants (transparent passthrough, luminance preservation, hue targeting, highlight attenuation); plus `AffineBuilder`, `ThumbnailRenderer` cache-key isolation, `TokenRenderer` fit-scale, `ProcessedLayerCache`; `DualSideRenderTest` verifies `renderDual` composite layout and outline decoration.
  - `ui/` — `ViewState`, `CanvasMapper` round-trip `screenToLogical(logicalToScreen(p)) == p`, `SelectTool` alpha-aware hit test (threshold = 8/255), `PreviewService` debounce + version-counter staleness drop. Tests in `ui/tools/` and `ViewState` were updated to thread `TokenSide` through.
  - `i18n/`, `prefs/` — service round-trip, key resolution.
- **Integration**: `integration/RoundTripRenderTest` renders a sample bag (both sides) and compares against a committed reference PNG (small per-pixel delta tolerance). **Regenerate the reference PNG when intentional render changes land.** `integration/PublishRoundTripTest` covers end-to-end layout planning → rasterization → PNG/PDF export (no reference image; verifies structural correctness and file creation).
- Tests in `command/`, `model/`, and `persistence/` were also updated to carry `TokenSide` through all layer-targeting operations.
- **No Swing widget interaction tests** — covered by manual smoke checks.

When adding a feature: add tests in the matching package directory under `src/test/kotlin/...`.

## Build / Run / Package

```bash
./gradlew build              # compile + test
./gradlew test               # tests only
./gradlew run                # dev run — copies local_resources/ → last_run_tmp/, uses that as workingDir
./gradlew release_all        # clean + build + jpackage; only the matching-OS jpackage task actually runs
```

- Classpath resources (`src/main/resources/`): `i18n/messages*.properties`, `log4j2.xml`, `icons/`.
- User-visible bundled files (`local_resources/`, committed): `assets/` (= `bundled://` root), `overlay/HEX_template_lines.png`, `icon.ico`.
- `last_run_tmp/`, `build/`, `output/` are git-ignored.
- jpackage stages: `stageJpackageInput` (runtime classpath + main jar) → `stageJpackageContent` (top-level items of `local_resources/` each passed as their own `--app-content`) → platform-specific `jpackage*` task → final artifact in `output/`.

## Maintenance Rule for This File

**Update this `AGENTS.md` whenever you:**
- Add/move/rename a package or significant component.
- Change a `PackageBoundaryTest` rule.
- Add/remove/rename a `Command`, model field, or `ModelEvent` kind.
- Change a persistence invariant (schema version, atomic save, missing-asset hard-fail, history-clear timing).
- Change a render constant (canvas dimensions, debounce window, cache bound, merge window) or pipeline shape.
- Change a UI invariant (confirmation behaviour, dirty marker location, single-vs-multi-select rules).
- Bump tech-stack versions or replace a major dependency.
- Migrate a hardcoded UI string to i18n (remove it from the "known violations" list).

Keep this file **compact** — surface architecture and rules, not implementation detail.
