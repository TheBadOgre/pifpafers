# Preview Pipeline, Selection Tool, and Canvas Performance — Design

Date: 2026-05-17
Companion to:
- `2026-05-15-neuroshima-hex-army-editor-design.md`
- `2026-05-15-army-editor-ui-plan-visual-spec.md`

## Purpose

Fix four UI defects reported against the current army editor build:

1. Token thumbnails in the **Tokens Collection** panel render incorrectly: layers are placed at native logical-pixel offsets inside a small thumbnail square, so most of the image leaks off the cell or shows fragments.
2. The currently selected token has no high-quality preview path; what the user sees in the grid does not match the canvas.
3. Layer thumbnails in the **Current Token Layers** panel render the whole token (all layers) instead of only that one layer's image.
4. The **Select** tool does not pick layers on canvas click and never draws a selection marker.
5. Canvas redraws feel slow because per-pixel HSB rewriting in `LayerRenderer.applyPixelOps` runs on the EDT on the first cache miss, and several view-state changes trigger unnecessary work.

## Goals / Non-Goals

**Goals**
- Token thumbnails fit-scale the 490 px logical token area into the thumbnail square, so all layers appear in proportion regardless of slider size.
- Selected token's tile shows a canvas-quality snapshot (overlay omitted), produced off-EDT, debounced 300 ms after the last model edit.
- Layer thumbnails show **only the single layer**, not the composite token.
- Select tool performs an alpha-aware hit test (topmost layer wins) and dashed-outline marker is drawn around each selected layer; dash color is the per-segment inverse of the canvas pixels beneath it.
- Reduce canvas paint cost by replacing per-pixel HSB with `RescaleOp` / `LookupOp` / `BandCombineOp` and by caching the composited layer image.

**Non-Goals**
- No new model fields.
- No persistence-schema bump.
- No change to the command catalog.
- No new architecture rules; existing `PackageBoundaryTest` R1–R8 remain unchanged.
- No replacement of the existing `ThumbnailRenderer` LRU strategy.

## Architecture Overview

```
                         model edits
                              │
                              ▼
   ┌──────────────────────────────────────────────────────┐
   │ TokenBag listeners                                   │
   │  ├─ ProcessedLayerCache (props in key → auto-miss)   │
   │  ├─ ThumbnailRenderer.invalidate(tokenId)            │
   │  ├─ PreviewService.invalidate(key)                   │
   │  └─ TokenCanvasPanel.repaint() (coalesced by Swing)  │
   └──────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼────────────────┐
              ▼               ▼                ▼
       Tokens grid       Layers list      Canvas paint
       (lightweight)     (lightweight)    (composited cache + marker)
              │
              ▼ (only when selected)
       PreviewService → bg render → EDT swap
```

New helper class: `ui/preview/PreviewService.kt`. Single-thread `ScheduledExecutorService` with a 300 ms debounce. Per-key version counter; late results from stale versions are discarded.

## Components

### 1. `render/TokenRenderer` — fit-to-logical

Current behavior: draws layers at native 490 px offsets into a `sizePx × sizePx` square. Layers with offsets greater than `sizePx / 2` fall outside.

New behavior:

```kotlin
fun render(token: Token, sizePx: Int): BufferedImage {
    val out = BufferedImage(sizePx, sizePx, TYPE_INT_ARGB)
    val g = out.createGraphics()
    val scale = sizePx.toDouble() / LOGICAL_SIZE_PX  // 490
    g.scale(scale, scale)                            // fit logical → thumbnail
    val center = LOGICAL_SIZE_PX / 2.0
    for (layer in token.layers) { ... AffineBuilder.build(..., canvasCenter = center, ...) ... }
}
```

Single change: prepend a uniform `scale` transform so all callers (token grid, layer list snapshot via single-layer token) see correctly fitted thumbnails.

### 2. `render/ThumbnailRenderer` — add `layerThumbnail`

Add a per-layer entry point that does **not** collide with token-thumbnail cache keys:

```kotlin
data class TokenKey(val tokenId: UUID, val sizePx: Int)
data class LayerKey(val tokenId: UUID, val layerId: UUID, val sizePx: Int)

fun tokenThumbnail(token: Token, sizePx: Int): BufferedImage
fun layerThumbnail(token: Token, layer: Layer, sizePx: Int): BufferedImage
fun invalidateToken(tokenId: UUID)   // drops both Token and Layer keys for this token
```

`layerThumbnail` builds a transient one-layer `Token` (same kind, fresh UUID **internal to the call**) and reuses `TokenRenderer.render`. The internal token id is not exposed and never used as a cache key — the cache key is the real `(tokenId, layerId, sizePx)` triple.

Cache map keyed by a sealed `Key` (TokenKey | LayerKey). Existing `SoftReference<BufferedImage>` storage stays.

### 3. `ui/preview/PreviewService` — debounced HQ snapshots

```kotlin
sealed interface PreviewKey {
    data class TokenSnapshot(val tokenId: UUID) : PreviewKey
}

class PreviewService(
    private val bag: TokenBag,
    private val tokenRenderer: TokenRenderer,
    debounceMillis: Long = 300L,
) {
    fun subscribe(key: PreviewKey, sizePx: Int, onReady: (BufferedImage) -> Unit)
    fun unsubscribe(key: PreviewKey)
    fun invalidate(key: PreviewKey)
    fun shutdown()
}
```

Internals:
- Single `Executors.newSingleThreadScheduledExecutor()`.
- Per-`PreviewKey` `version: AtomicLong`. `invalidate` bumps the version and schedules a render at `now + debounceMillis`, cancelling any pending task for that key.
- Render task: read `version` at start, render snapshot (calling `tokenRenderer.render(token, sizePxOversample)` then `Image.getScaledInstance` down to display size for AA — or render directly at display size), check `version` unchanged at end, then `SwingUtilities.invokeLater { onReady(image) }`. Stale → drop.
- Shutdown drains the executor; called by `AppContext.shutdown()` if it exists, otherwise wired into `MainFrame.windowClosing`.

For this iteration we expose only `TokenSnapshot`. Layer-level HQ snapshots can be added later without API change.

### 4. `ui/panels/TokensCollectionPanel`

Each cell renders two states:

| State | Source |
|-------|--------|
| Non-selected | `thumbnails.tokenThumbnail(token, sizePx)` — synchronous, cached. Lightweight; uses fit-scaled `TokenRenderer`. |
| Selected | Starts with lightweight result; subscribes to `PreviewService` with `PreviewKey.TokenSnapshot(token.id)`. When callback fires, swap the `ImageIcon`. |

Selection change semantics:
- On `setActiveToken(newId)`: unsubscribe previous selected cell, subscribe new selected cell, trigger immediate `invalidate` so the snapshot refreshes promptly.
- On bag edits that touch the active token: `AppContext` listener calls `PreviewService.invalidate(TokenSnapshot(activeId))`.

### 5. `ui/panels/LayersPanel`

Replace `ctx.thumbnails.tokenThumbnail(Token(token.id, token.kind).apply { addLayer(layer) }, size)` with `ctx.thumbnails.layerThumbnail(token, layer, size)`.

No other change. Layer rows stay lightweight; we do not produce HQ snapshots per layer in this iteration.

### 6. `ui/tools/SelectTool` — alpha-aware hit test

```kotlin
override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
    if (e.button != MouseEvent.BUTTON1) return
    val token = ctx.bag.findToken(ctx.viewState.activeTokenId ?: return) ?: return
    val logical = ctx.canvasMapper.screenToLogical(e.point)   // see Section 7
    val hit = token.layers.asReversed().firstOrNull { layer ->
        val img = ctx.imageCache.get(layer.assetPath) ?: return@firstOrNull false
        val xform = AffineBuilder.build(layer.props, LOGICAL_CENTER, LOGICAL_CENTER, img.width, img.height)
        val inv = runCatching { xform.createInverse() }.getOrNull() ?: return@firstOrNull false
        val srcPt = Point2D.Double().also { inv.transform(logical, it) }
        val sx = srcPt.x.toInt(); val sy = srcPt.y.toInt()
        if (sx !in 0 until img.width || sy !in 0 until img.height) return@firstOrNull false
        val alpha = (img.getRGB(sx, sy) ushr 24) and 0xff
        alpha > ALPHA_HIT_THRESHOLD   // = 8
    }
    if (e.isControlDown) {
        if (hit != null) {
            if (hit.id in ctx.viewState.selectedLayers) ctx.viewState.deselectLayer(hit.id)
            else ctx.viewState.selectLayer(hit.id)
        }
    } else {
        if (hit == null) ctx.viewState.clearSelection()
        else ctx.viewState.replaceSelection(listOf(hit.id))
    }
}
```

Constant `ALPHA_HIT_THRESHOLD = 8` (out of 255) — ignores faint anti-aliased fringe but accepts any visibly-rendered pixel.

### 7. `ui/canvas/CanvasMapper`

Small helper extracted from `TokenCanvasPanel` so tools can convert screen ↔ logical without duplicating math:

```kotlin
class CanvasMapper(private val panel: TokenCanvasPanel) {
    fun screenToLogical(p: Point): Point2D.Double
    fun logicalToScreen(p: Point2D): Point2D.Double
}
```

Inverse of: translate(centerX + panX, centerY + panY) → scale(zoom) → translate(-LOGICAL_CENTER, -LOGICAL_CENTER).

`LOGICAL_CENTER = LOGICAL_SIZE_PX / 2.0` (245.0).

Exposed via `AppContext.canvasMapper` set after `TokenCanvasPanel` is constructed (single setter, called once during `MainFrame` wiring).

### 8. `ui/canvas/TokenCanvasPanel` — paint pipeline

Two changes:

(a) **Composite cache**: maintain a `BufferedImage compositeCache` of size `LOGICAL_SIZE_PX × LOGICAL_SIZE_PX`, refreshed only when the bag emits a model event affecting the active token. Zoom, pan, selection change, overlay toggle do **not** invalidate the cache — they only repaint. Selection marker is drawn on top of the cache each frame.

```kotlin
private var compositeCache: BufferedImage? = null
private var compositeForTokenId: UUID? = null

private fun ensureComposite(token: Token): BufferedImage { ... rebuild if invalidated ... }
```

Invalidation hooks listen to `ModelEvent.LayerAdded`, `LayerRemoved`, `LayerReordered`, `LayerPropsChanged` and to active-token changes.

(b) **Selection marker**: after drawing the composite and overlay, iterate `viewState.selectedLayers`:

```kotlin
val corners = listOf(Point2D.Double(0.0, 0.0),
                     Point2D.Double(w.toDouble(), 0.0),
                     Point2D.Double(w.toDouble(), h.toDouble()),
                     Point2D.Double(0.0, h.toDouble()))
val xform = AffineBuilder.build(layer.props, LOGICAL_CENTER, LOGICAL_CENTER, w, h)
val onLogical = corners.map { xform.transform(it, null) }
val onScreen  = onLogical.map { logicalToScreen(it) }
drawDashedQuad(g2, onScreen, sampleSource = compositeCache)
```

`drawDashedQuad` walks each of the 4 edges in ~8 px on-screen steps, alternating drawn / skipped segments. For each drawn segment it samples one pixel at the segment midpoint from the canvas backbuffer (or, equivalently, from `compositeCache` projected through the same transform stack), inverts RGB, and draws the segment with that color via `BasicStroke(1.5f)`.

(c) **Pan handler fix**: remove `getModifiersExText(...).contains("Button2")`. Track `panning: Boolean` in `mousePressed` (button 2) / `mouseReleased`.

### 9. `render/LayerRenderer` — vectorize pixel ops

Identity short-circuit stays. New non-identity path:

```kotlin
fun applyPixelOps(source: BufferedImage, props: LayerProperties): BufferedImage {
    if (isIdentity(props)) return source
    var img = source
    if (props.hue != 0f) img = hueShift(img, props.hue)     // LookupOp via precomputed table
    if (props.saturation != 1f) img = applySaturation(img, props.saturation)  // BandCombineOp
    if (props.brightness != 1f) img = RescaleOp(props.brightness, 0f, null).filter(img, null)
    if (props.opacity != 1f) img = RescaleOp(
        floatArrayOf(1f,1f,1f,props.opacity), floatArrayOf(0f,0f,0f,0f), null
    ).filter(img, null)
    return img
}
```

Hue table: precomputed at first use, keyed by quantized 16-step hue (24 entries × 256 LUT). For typical token edits hue stays at a small number of values, so the table is hit-cache-friendly.

If `BandCombineOp` for saturation proves brittle on some images (TYPE mismatch), fall back to the legacy per-pixel loop for the saturation step only.

Reference parity test: against the legacy per-pixel implementation on a small fixture, allow ≤ 2/255 per-channel delta.

## View-State Listener Hygiene

`TokenCanvasPanel` previously calls `repaint()` on every `viewState` fire. That stays — Swing coalesces repaints — but the composite cache is **not** rebuilt by view-state changes. Only `bag` events touching the active token rebuild it.

`TokensCollectionPanel` and `LayersPanel` already call full `rebuild()` on every fire. Out of scope to fix in this design; if perf becomes a concern, narrow it in a follow-up.

## AppContext Wiring

```kotlin
class AppContext(...) {
    ...
    val previewService: PreviewService = PreviewService(bag, tokenRenderer)
    lateinit var canvasMapper: CanvasMapper   // set by MainFrame after canvas creation
}
```

`installInvalidationListener` extended:

```kotlin
b.addListener { event ->
    val touched = touchedTokenIdOf(event) ?: return@addListener
    thumbnails.invalidateToken(touched)
    previewService.invalidate(PreviewKey.TokenSnapshot(touched))
    // existing active-token auto-switch logic stays
}
```

`AppContext.shutdown()`: new method, calls `previewService.shutdown()`. Invoked from `MainFrame.windowClosing`.

## Architecture Rules

`PreviewService` lives in `ui/preview/`. It uses `javax.swing.SwingUtilities` for EDT dispatch and depends on `render` and `model`. This fits cleanly inside existing rule R5 (which forbids `javax.swing` only in `render` — `ui` is allowed everything).

No changes to `PackageBoundaryTest`.

## Testing

**Unit**
- `TokenRendererFitTest`: render a token whose only layer has `offsetX = 200` (logical px). At `sizePx = 96`, the layer image must appear in the right half of the thumbnail (logical 200 → ~40 px from center after fit-scale).
- `ThumbnailRendererLayerKeyTest`: `tokenThumbnail` and `layerThumbnail` for the same `(tokenId, sizePx)` must return distinct images and must not collide in cache; invalidation drops both.
- `LayerRendererParityTest`: vectorized ops within 2/255 of legacy per-pixel on a 16×16 fixture across {opacity, brightness, saturation, hue} permutations.
- `PreviewServiceDebounceTest`: `invalidate` called N times within debounce window → exactly one render task fires; stale results are dropped after a second `invalidate`.
- `SelectToolHitTest`: alpha threshold respected; click on transparent corner of a layer falls through to layer below; ctrl-click toggles; plain click on empty clears.
- `CanvasMapperTest`: `screenToLogical(logicalToScreen(p)) == p` round-trip across pan and zoom.

**Integration**
- `RoundTripRenderTest` extended: bag → `tokenRenderer.render(token, 96)` matches a committed reference PNG with fit-scaling applied. Existing reference PNGs must be regenerated as part of this change.

**Out of scope**
- No Swing UI tests for the marker visual; manual smoke list in the implementation plan covers it.

## Manual Smoke List

1. Open a bag with multiple tokens. Token grid shows fit-scaled thumbnails; no fragments leaking out of cells.
2. Click a token. Within ~300 ms its grid tile refines to canvas-quality (no overlay lines visible).
3. Select a layer in the Layers panel. Its row thumbnail shows that one layer's image only — verified by deleting all other layers and confirming the thumbnail is unchanged.
4. With Select tool active, click directly on a layer's visible pixels: layer becomes selected; dashed marker appears, oriented to the layer's rotation. Click on a transparent area: selection clears.
5. Ctrl-click adds/removes from selection; multiple markers can appear at once.
6. Drag a slider in Layer Properties: canvas updates smoothly; no EDT freeze on the first change of a previously-untouched layer.
7. Middle-mouse drag pans without sticking; selection marker tracks the pan correctly.

## Risk and Rollback

Each component change is independent. If `BandCombineOp` saturation breaks images, fall back to legacy per-pixel for that op only (flag in `LayerRenderer`). If `PreviewService` introduces an EDT race, disabling the HQ snapshot subscription leaves the panel showing the lightweight thumbnail — still correct, just lower fidelity.

## Open Items

- HQ snapshots for layer rows (`PreviewKey.LayerSnapshot`) — deferred until a user reports needing it.
- Replacing `TokensCollectionPanel.rebuild()` full-grid rebuild with delta updates — deferred.
- Selection marker hit-test handles (rotate / scale grips) — separate spec when those tools' on-canvas affordances are scoped.
