# Preview Pipeline, Selection Tool, and Canvas Performance — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix token-grid thumbnail fragments, layer-thumbnail collision, broken Select tool with dashed marker, and slow per-pixel HSB on canvas, per the 2026-05-17 spec.

**Architecture:** Bottom-up TDD. Renderer fit-scaling and vectorized pixel ops first; a sealed cache key in `ThumbnailRenderer`; a small `CanvasMapper` so tools and the marker share one screen↔logical math; a new `ui/preview/PreviewService` for 300 ms-debounced background snapshots; `SelectTool` rewritten for alpha-aware hit testing; `TokenCanvasPanel` adds a composite cache + oriented dashed marker. Every behavioral change is preceded by a failing unit test.

**Tech Stack:** Kotlin 2.3 / JVM 21, Swing, java.awt.image (`RescaleOp`, `LookupOp`, `BandCombineOp`), kotlinx-coroutines (only existing usage), JUnit 5, kotlin-test-junit5, mockito-kotlin.

**Spec:** `docs/superpowers/specs/2026-05-17-preview-pipeline-and-selection-design.md`

---

## File Structure

**New files**
- `src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/CanvasMapper.kt` — screen↔logical math, single source of truth.
- `src/main/kotlin/net/rafkos/neuroshima/editor/ui/preview/PreviewService.kt` — debounced background snapshot dispatch.
- `src/test/kotlin/net/rafkos/neuroshima/editor/ui/canvas/CanvasMapperTest.kt`
- `src/test/kotlin/net/rafkos/neuroshima/editor/ui/preview/PreviewServiceTest.kt`
- `src/test/kotlin/net/rafkos/neuroshima/editor/ui/tools/SelectToolTest.kt`

**Modified files**
- `src/main/kotlin/net/rafkos/neuroshima/editor/render/TokenRenderer.kt` — fit-scale 490 → sizePx.
- `src/main/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRenderer.kt` — sealed Key + `layerThumbnail`.
- `src/main/kotlin/net/rafkos/neuroshima/editor/render/LayerRenderer.kt` — RescaleOp / BandCombineOp / LookupOp pipeline; per-pixel fallback for saturation if needed.
- `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/SelectTool.kt` — alpha-aware hit-test, ctrl/replace semantics.
- `src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt` — composite cache, button-tracked pan, oriented dashed marker, expose mapper.
- `src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/TokensCollectionPanel.kt` — HQ snapshot subscription for selected cell.
- `src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/LayersPanel.kt` — switch to `layerThumbnail`.
- `src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt` — own `PreviewService`, expose `canvasMapper`, `shutdown()`.
- `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt` — set `canvasMapper`, call `ctx.shutdown()` on close.
- `src/test/kotlin/net/rafkos/neuroshima/editor/render/TokenRendererTest.kt` — add fit-scale assertion.
- `src/test/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRendererTest.kt` — `layerThumbnail` semantics + cache-key isolation.
- `src/test/kotlin/net/rafkos/neuroshima/editor/render/LayerRendererTest.kt` — parity vs legacy per-pixel.

---

## Task 1: TokenRenderer fit-scale

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/render/TokenRenderer.kt`
- Test: `src/test/kotlin/net/rafkos/neuroshima/editor/render/TokenRendererTest.kt`

- [ ] **Step 1: Write failing test**

Append to `TokenRendererTest.kt`:

```kotlin
@Test
fun `fits 490 logical token area into thumbnail square - offset layer lands proportionally`() {
    val cache = ImageCache(16)
    val asset = AssetPath.Bundled("dot.png")
    cache.put(asset, solid(Color.RED, w = 20, h = 20))
    // Layer offset +200 logical px on x axis. At sizePx=98 the fit-scale = 98/490 = 0.2.
    // Expected on-screen offset from center: 200 * 0.2 = 40 px. Image size 20 * 0.2 = 4 px.
    // So red pixel should appear near (49+40, 49) = (89, 49), not near native (49+200,49).
    val token = Token.createUnit().apply {
        addLayer(Layer.create(asset, LayerProperties(offsetX = 200, offsetY = 0)))
    }
    val out = TokenRenderer(cache, ProcessedLayerCache(16)).render(token, sizePx = 98)
    val argbHit = out.getRGB(89, 49)
    val alphaHit = (argbHit ushr 24) and 0xff
    assert(alphaHit > 200) { "expected red dot near (89,49), alpha=$alphaHit" }
    val argbCenter = out.getRGB(49, 49)
    val alphaCenter = (argbCenter ushr 24) and 0xff
    assertEquals(0, alphaCenter, "center should be transparent; got alpha=$alphaCenter")
}
```

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.render.TokenRendererTest.fits 490 logical token area into thumbnail square - offset layer lands proportionally'`
Expected: FAIL — assertion at `(89, 49)` is transparent because current renderer puts the dot at `(49+200, 49) = (249, 49)` (off-canvas).

- [ ] **Step 3: Implement fit-scale**

Replace `TokenRenderer.kt` with:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.Token
import java.awt.RenderingHints
import java.awt.image.BufferedImage

const val LOGICAL_TOKEN_SIZE_PX: Int = 490

class TokenRenderer(
    private val imageCache: ImageCache,
    private val processedCache: ProcessedLayerCache,
) {
    fun render(token: Token, sizePx: Int): BufferedImage {
        val out = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            val fit = sizePx.toDouble() / LOGICAL_TOKEN_SIZE_PX
            g.scale(fit, fit)
            val center = LOGICAL_TOKEN_SIZE_PX / 2.0
            for (layer in token.layers) {
                val source = imageCache.get(layer.assetPath) ?: continue
                val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
                val processed = processedCache.get(key)
                    ?: LayerRenderer.applyPixelOps(source, layer.props).also {
                        processedCache.put(key, it)
                    }
                val xform = AffineBuilder.build(
                    props = layer.props,
                    canvasCenterX = center,
                    canvasCenterY = center,
                    imageWidth = processed.width,
                    imageHeight = processed.height,
                )
                g.drawImage(processed, xform, null)
            }
        } finally {
            g.dispose()
        }
        return out
    }
}
```

- [ ] **Step 4: Run test, expect pass**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.render.TokenRendererTest'`
Expected: all four tests PASS (three existing + new fit-scale test).

- [ ] **Step 5: Run integration test to confirm it still tolerates new geometry**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.integration.RoundTripRenderTest'`
Expected: PASS. The dot is rendered smaller now; sample point `(25, 50)` is still in the yellow background.

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/render/TokenRenderer.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/render/TokenRendererTest.kt
git commit -m "fix(render): fit-scale 490 logical token area into thumbnail size"
```

---

## Task 2: ThumbnailRenderer sealed key + layerThumbnail

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRenderer.kt`
- Test: `src/test/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRendererTest.kt`

- [ ] **Step 1: Write failing test**

Replace contents of `ThumbnailRendererTest.kt` (keep existing tests, append new ones):

```kotlin
@Test
fun `layerThumbnail returns single-layer render distinct from full token thumbnail`() {
    val cache = ImageCache(16)
    val red = AssetPath.Bundled("red.png")
    val blue = AssetPath.Bundled("blue.png")
    cache.put(red, solid(Color.RED))
    cache.put(blue, solid(Color.BLUE))
    val token = Token.createUnit().apply {
        addLayer(Layer.create(red))
        addLayer(Layer.create(blue)) // top
    }
    val r = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(16)))

    val full = r.tokenThumbnail(token, 98)
    val redOnly = r.layerThumbnail(token, token.layers[0], 98)
    val blueOnly = r.layerThumbnail(token, token.layers[1], 98)

    assertEquals(255, (full.getRGB(49, 49) and 0xff), "full thumbnail top layer is blue")
    assertEquals(255, ((redOnly.getRGB(49, 49) ushr 16) and 0xff),
        "red-only thumbnail center is red")
    assertEquals(255, (blueOnly.getRGB(49, 49) and 0xff),
        "blue-only thumbnail center is blue")
}

@Test
fun `invalidateToken drops both token and layer cache entries`() {
    val cache = ImageCache(16)
    val asset = AssetPath.Bundled("a.png")
    cache.put(asset, solid(Color.RED))
    val token = Token.createUnit().apply { addLayer(Layer.create(asset)) }
    val r = ThumbnailRenderer(TokenRenderer(cache, ProcessedLayerCache(16)))

    val tFirst = r.tokenThumbnail(token, 64)
    val lFirst = r.layerThumbnail(token, token.layers[0], 64)
    assertSame(tFirst, r.tokenThumbnail(token, 64))                 // cached
    assertSame(lFirst, r.layerThumbnail(token, token.layers[0], 64)) // cached

    r.invalidateToken(token.id)
    assertNotSame(tFirst, r.tokenThumbnail(token, 64))
    assertNotSame(lFirst, r.layerThumbnail(token, token.layers[0], 64))
}
```

Helper `solid(Color, w, h)` and any missing imports — copy from `TokenRendererTest.kt`.

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.render.ThumbnailRendererTest'`
Expected: FAIL — method `layerThumbnail` is unresolved.

- [ ] **Step 3: Implement sealed key + layerThumbnail**

Replace `ThumbnailRenderer.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import java.awt.image.BufferedImage
import java.lang.ref.SoftReference
import java.util.UUID

class ThumbnailRenderer(private val tokenRenderer: TokenRenderer) {

    private sealed interface Key {
        val tokenId: UUID
        val sizePx: Int
        data class TokenKey(override val tokenId: UUID, override val sizePx: Int) : Key
        data class LayerKey(override val tokenId: UUID, val layerId: UUID, override val sizePx: Int) : Key
    }

    private val cache: MutableMap<Key, SoftReference<BufferedImage>> = mutableMapOf()

    @Synchronized
    fun tokenThumbnail(token: Token, sizePx: Int): BufferedImage {
        val key = Key.TokenKey(token.id, sizePx)
        cache[key]?.get()?.let { return it }
        val img = tokenRenderer.render(token, sizePx)
        cache[key] = SoftReference(img)
        return img
    }

    @Synchronized
    fun layerThumbnail(token: Token, layer: Layer, sizePx: Int): BufferedImage {
        val key = Key.LayerKey(token.id, layer.id, sizePx)
        cache[key]?.get()?.let { return it }
        val one = Token(UUID.randomUUID(), token.kind).apply { addLayer(layer) }
        val img = tokenRenderer.render(one, sizePx)
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

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.render.ThumbnailRendererTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRenderer.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/render/ThumbnailRendererTest.kt
git commit -m "feat(render): layer-scoped thumbnails via sealed cache key"
```

---

## Task 3: LayersPanel uses layerThumbnail

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/LayersPanel.kt`

- [ ] **Step 1: Edit `LayersPanel.rebuild()` to call the new API**

Replace this block in `LayersPanel.kt` (the `for (layer in layersTopFirst)` body's first statement):

```kotlin
val img = ctx.thumbnails.tokenThumbnail(
    Token(token.id, token.kind).apply { addLayer(layer) },
    size,
)
```

with:

```kotlin
val img = ctx.thumbnails.layerThumbnail(token, layer, size)
```

Remove the unused `import net.rafkos.neuroshima.editor.model.Token` if it becomes unused after the edit.

- [ ] **Step 2: Build to confirm no compilation errors**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Full test pass**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/LayersPanel.kt
git commit -m "fix(ui): layers panel uses layerThumbnail not full-token render"
```

---

## Task 4: LayerRenderer vectorize pixel ops

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/render/LayerRenderer.kt`
- Test: `src/test/kotlin/net/rafkos/neuroshima/editor/render/LayerRendererTest.kt`

- [ ] **Step 1: Write failing parity test**

Append to `LayerRendererTest.kt`:

```kotlin
@Test
fun `vectorized ops match legacy per-pixel within 2 of 255 across hsb-opacity permutations`() {
    val src = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until 16) for (x in 0 until 16) {
        val r = (x * 16); val g = (y * 16); val b = ((x + y) * 8) and 0xff
        src.setRGB(x, y, (0xff shl 24) or (r shl 16) or (g shl 8) or b)
    }
    val permutations = listOf(
        LayerProperties(opacity = 0.5f),
        LayerProperties(brightness = 0.7f),
        LayerProperties(saturation = 0.3f),
        LayerProperties(hue = 0.25f),
        LayerProperties(opacity = 0.8f, brightness = 0.9f, saturation = 0.6f, hue = 0.1f),
    )
    for (p in permutations) {
        val ref = LayerRendererLegacy.applyPixelOps(src, p)
        val out = LayerRenderer.applyPixelOps(src, p)
        var maxDelta = 0
        for (y in 0 until 16) for (x in 0 until 16) {
            val a = ref.getRGB(x, y); val b = out.getRGB(x, y)
            for (shift in intArrayOf(0, 8, 16, 24)) {
                val d = kotlin.math.abs(((a ushr shift) and 0xff) - ((b ushr shift) and 0xff))
                if (d > maxDelta) maxDelta = d
            }
        }
        assert(maxDelta <= 2) { "max delta $maxDelta for $p" }
    }
}
```

Also create reference helper in the same test directory:

`src/test/kotlin/net/rafkos/neuroshima/editor/render/LayerRendererLegacy.kt`

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.Color
import java.awt.image.BufferedImage

object LayerRendererLegacy {
    fun applyPixelOps(source: BufferedImage, props: LayerProperties): BufferedImage {
        val identity = props.opacity == 1f && props.hue == 0f &&
            props.saturation == 1f && props.brightness == 1f
        if (identity) return source
        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val hsb = FloatArray(3)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val argb = source.getRGB(x, y)
            val a = (argb ushr 24) and 0xff
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            Color.RGBtoHSB(r, g, b, hsb)
            val newHue = ((hsb[0] + props.hue) % 1f + 1f) % 1f
            val newSat = (hsb[1] * props.saturation).coerceIn(0f, 1f)
            val newBri = (hsb[2] * props.brightness).coerceIn(0f, 1f)
            val rgb = Color.HSBtoRGB(newHue, newSat, newBri) and 0x00ffffff
            val newAlpha = (a * props.opacity).toInt().coerceIn(0, 255)
            out.setRGB(x, y, (newAlpha shl 24) or rgb)
        }
        return out
    }
}
```

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.render.LayerRendererTest.vectorized ops match legacy per-pixel within 2 of 255 across hsb-opacity permutations'`
Expected: PASS only by coincidence if you didn't change LayerRenderer; this test will keep working if you reorder operations. The point of this test is to be the **safety net** when you replace internals in Step 3. Re-run it after Step 3.

- [ ] **Step 3: Implement vectorized pipeline**

Replace `LayerRenderer.kt`:

```kotlin
package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.Color
import java.awt.image.BandCombineOp
import java.awt.image.BufferedImage
import java.awt.image.RescaleOp

object LayerRenderer {

    fun applyPixelOps(source: BufferedImage, props: LayerProperties): BufferedImage {
        val identity = props.opacity == 1f && props.hue == 0f &&
            props.saturation == 1f && props.brightness == 1f
        if (identity) return source

        var img = ensureArgb(source)
        if (props.hue != 0f) img = hueShift(img, props.hue)
        if (props.saturation != 1f) img = applySaturationPerPixel(img, props.saturation)
        if (props.brightness != 1f) {
            val scales = floatArrayOf(props.brightness, props.brightness, props.brightness, 1f)
            val offsets = floatArrayOf(0f, 0f, 0f, 0f)
            img = RescaleOp(scales, offsets, null).filter(img, null)
        }
        if (props.opacity != 1f) {
            val scales = floatArrayOf(1f, 1f, 1f, props.opacity)
            val offsets = floatArrayOf(0f, 0f, 0f, 0f)
            img = RescaleOp(scales, offsets, null).filter(img, null)
        }
        return img
    }

    private fun ensureArgb(source: BufferedImage): BufferedImage {
        if (source.type == BufferedImage.TYPE_INT_ARGB) return source
        val copy = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val g = copy.createGraphics()
        try { g.drawImage(source, 0, 0, null) } finally { g.dispose() }
        return copy
    }

    // Per-pixel for now. RescaleOp can't do saturation in RGB space; BandCombineOp on
    // ARGB rasters proved type-finicky on some BufferedImage configs. Keep correctness;
    // optimize later if profiling shows it's a hot spot.
    private fun applySaturationPerPixel(src: BufferedImage, saturation: Float): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val hsb = FloatArray(3)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val argb = src.getRGB(x, y)
            val a = (argb ushr 24) and 0xff
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            Color.RGBtoHSB(r, g, b, hsb)
            val newSat = (hsb[1] * saturation).coerceIn(0f, 1f)
            val rgb = Color.HSBtoRGB(hsb[0], newSat, hsb[2]) and 0x00ffffff
            out.setRGB(x, y, (a shl 24) or rgb)
        }
        return out
    }

    private fun hueShift(src: BufferedImage, hueShift: Float): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
        val hsb = FloatArray(3)
        for (y in 0 until src.height) for (x in 0 until src.width) {
            val argb = src.getRGB(x, y)
            val a = (argb ushr 24) and 0xff
            val r = (argb ushr 16) and 0xff
            val g = (argb ushr 8) and 0xff
            val b = argb and 0xff
            Color.RGBtoHSB(r, g, b, hsb)
            val newHue = ((hsb[0] + hueShift) % 1f + 1f) % 1f
            val rgb = Color.HSBtoRGB(newHue, hsb[1], hsb[2]) and 0x00ffffff
            out.setRGB(x, y, (a shl 24) or rgb)
        }
        return out
    }
}
```

Note on perf realism: the wins are (a) `RescaleOp` for opacity/brightness which now use native filters, (b) cache short-circuit for identity, (c) `ProcessedLayerCache` already prevents recomputation across paints. Hue and saturation stay per-pixel for correctness — the loop runs at most once per `(assetPath, props)`. If profiling later flags hue as a bottleneck, swap in a `LookupOp` with a quantized hue LUT.

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.render.LayerRendererTest'`
Expected: all tests PASS, including new parity test (max delta ≤ 2).

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/render/LayerRenderer.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/render/LayerRendererTest.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/render/LayerRendererLegacy.kt
git commit -m "perf(render): RescaleOp for opacity+brightness, identity short-circuit, legacy parity test"
```

---

## Task 5: CanvasMapper

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/CanvasMapper.kt`
- Test: `src/test/kotlin/net/rafkos/neuroshima/editor/ui/canvas/CanvasMapperTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package net.rafkos.neuroshima.editor.ui.canvas

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.geom.Point2D

class CanvasMapperTest {

    private fun mapper(width: Int, height: Int, zoom: Float, panX: Int, panY: Int): CanvasMapper {
        return CanvasMapper(
            widthSupplier = { width },
            heightSupplier = { height },
            zoomSupplier = { zoom },
            panXSupplier = { panX },
            panYSupplier = { panY },
        )
    }

    @Test
    fun `screenToLogical inverse of logicalToScreen across zoom and pan`() {
        val m = mapper(800, 600, zoom = 1.5f, panX = 30, panY = -20)
        val logical = Point2D.Double(120.0, 250.0)
        val screen = m.logicalToScreen(logical)
        val back = m.screenToLogical(Point(screen.x.toInt(), screen.y.toInt()))
        assertEquals(logical.x, back.x, 1.0)
        assertEquals(logical.y, back.y, 1.0)
    }

    @Test
    fun `center of canvas at zoom 1 no pan maps to logical 245 245`() {
        val m = mapper(490, 490, zoom = 1f, panX = 0, panY = 0)
        val p = m.screenToLogical(Point(245, 245))
        assertEquals(245.0, p.x, 0.001)
        assertEquals(245.0, p.y, 0.001)
    }
}
```

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.ui.canvas.CanvasMapperTest'`
Expected: FAIL — `CanvasMapper` unresolved.

- [ ] **Step 3: Implement CanvasMapper**

```kotlin
package net.rafkos.neuroshima.editor.ui.canvas

import java.awt.Point
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D

const val LOGICAL_CENTER: Double = 245.0   // = LOGICAL_TOKEN_SIZE_PX / 2.0

class CanvasMapper(
    private val widthSupplier: () -> Int,
    private val heightSupplier: () -> Int,
    private val zoomSupplier: () -> Float,
    private val panXSupplier: () -> Int,
    private val panYSupplier: () -> Int,
) {
    fun logicalToScreen(p: Point2D): Point2D.Double {
        val out = Point2D.Double()
        screenFromLogicalTransform().transform(p, out)
        return out
    }

    fun screenToLogical(p: Point): Point2D.Double {
        val out = Point2D.Double()
        screenFromLogicalTransform().createInverse().transform(Point2D.Double(p.x.toDouble(), p.y.toDouble()), out)
        return out
    }

    private fun screenFromLogicalTransform(): AffineTransform {
        val t = AffineTransform()
        t.translate(widthSupplier() / 2.0 + panXSupplier(), heightSupplier() / 2.0 + panYSupplier())
        t.scale(zoomSupplier().toDouble(), zoomSupplier().toDouble())
        t.translate(-LOGICAL_CENTER, -LOGICAL_CENTER)
        return t
    }
}
```

- [ ] **Step 4: Run test, expect pass**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.ui.canvas.CanvasMapperTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/CanvasMapper.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/ui/canvas/CanvasMapperTest.kt
git commit -m "feat(ui): CanvasMapper screen<->logical transform"
```

---

## Task 6: PreviewService

**Files:**
- Create: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/preview/PreviewService.kt`
- Test: `src/test/kotlin/net/rafkos/neuroshima/editor/ui/preview/PreviewServiceTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package net.rafkos.neuroshima.editor.ui.preview

import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import net.rafkos.neuroshima.editor.render.TokenRenderer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PreviewServiceTest {

    private fun solid(c: Color, w: Int = 16, h: Int = 16): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics(); g.color = c; g.fillRect(0, 0, w, h); g.dispose()
        return img
    }

    @Test
    fun `coalesces repeated invalidations into one render within debounce window`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png"); cache.put(asset, solid(Color.RED))
        val bag = TokenBag()
        val token = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        bag.addToken(token)
        val service = PreviewService(bag, TokenRenderer(cache, ProcessedLayerCache(8)),
            debounceMillis = 80L)

        val calls = AtomicInteger()
        val done = CountDownLatch(1)
        service.subscribe(PreviewKey.TokenSnapshot(token.id), 64) {
            calls.incrementAndGet(); done.countDown()
        }
        repeat(8) { service.invalidate(PreviewKey.TokenSnapshot(token.id)) }

        assertTrue(done.await(2, TimeUnit.SECONDS), "render never fired")
        Thread.sleep(200)
        assertEquals(1, calls.get(), "exactly one delivery for one coalesced burst")
        service.shutdown()
    }

    @Test
    fun `stale results dropped when later invalidation supersedes`() {
        val cache = ImageCache(8)
        val asset = AssetPath.Bundled("a.png"); cache.put(asset, solid(Color.RED))
        val bag = TokenBag()
        val token = Token.createUnit().apply { addLayer(Layer.create(asset)) }
        bag.addToken(token)
        val service = PreviewService(bag, TokenRenderer(cache, ProcessedLayerCache(8)),
            debounceMillis = 60L)

        val received = mutableListOf<BufferedImage>()
        val latch = CountDownLatch(1)
        service.subscribe(PreviewKey.TokenSnapshot(token.id), 64) {
            synchronized(received) { received += it }
            latch.countDown()
        }
        service.invalidate(PreviewKey.TokenSnapshot(token.id))
        Thread.sleep(20)
        service.invalidate(PreviewKey.TokenSnapshot(token.id))

        assertTrue(latch.await(2, TimeUnit.SECONDS), "no result")
        Thread.sleep(200)
        synchronized(received) { assertEquals(1, received.size) }
        service.shutdown()
    }
}
```

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.ui.preview.PreviewServiceTest'`
Expected: FAIL — class unresolved.

- [ ] **Step 3: Implement PreviewService**

`src/main/kotlin/net/rafkos/neuroshima/editor/ui/preview/PreviewService.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.preview

import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.render.TokenRenderer
import java.awt.image.BufferedImage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

sealed interface PreviewKey {
    val tokenId: UUID
    data class TokenSnapshot(override val tokenId: UUID) : PreviewKey
}

class PreviewService(
    private val bag: TokenBag,
    private val tokenRenderer: TokenRenderer,
    private val debounceMillis: Long = 300L,
) {
    private data class Subscription(
        val sizePx: Int,
        val onReady: (BufferedImage) -> Unit,
        val version: AtomicLong = AtomicLong(0),
        @Volatile var pending: ScheduledFuture<*>? = null,
    )

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "preview-service").apply { isDaemon = true }
    }
    private val subs: MutableMap<PreviewKey, Subscription> = ConcurrentHashMap()

    fun subscribe(key: PreviewKey, sizePx: Int, onReady: (BufferedImage) -> Unit) {
        subs[key] = Subscription(sizePx, onReady)
        invalidate(key)
    }

    fun unsubscribe(key: PreviewKey) {
        val s = subs.remove(key) ?: return
        s.pending?.cancel(false)
    }

    fun invalidate(key: PreviewKey) {
        val s = subs[key] ?: return
        val myVersion = s.version.incrementAndGet()
        s.pending?.cancel(false)
        s.pending = executor.schedule({ render(key, s, myVersion) }, debounceMillis, TimeUnit.MILLISECONDS)
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun render(key: PreviewKey, s: Subscription, myVersion: Long) {
        if (s.version.get() != myVersion) return
        val token = bag.findToken(key.tokenId) ?: return
        val img = tokenRenderer.render(token, s.sizePx)
        if (s.version.get() != myVersion) return
        SwingUtilities.invokeLater { s.onReady(img) }
    }
}
```

- [ ] **Step 4: Run tests, expect pass**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.ui.preview.PreviewServiceTest'`
Expected: PASS.

Headless Swing note: `SwingUtilities.invokeLater` runs even in headless mode (it just queues onto the EDT, which always exists). The tests do not rely on an actual window.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/preview/PreviewService.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/ui/preview/PreviewServiceTest.kt
git commit -m "feat(ui): PreviewService debounced background token snapshots"
```

---

## Task 7: SelectTool alpha-aware hit-test

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/SelectTool.kt`
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt` (expose mapper as nullable lateinit setter)
- Test: `src/test/kotlin/net/rafkos/neuroshima/editor/ui/tools/SelectToolTest.kt`

- [ ] **Step 1: Add `canvasMapper` to AppContext (no wiring yet)**

In `AppContext.kt`, inside the class body (after other fields):

```kotlin
var canvasMapper: net.rafkos.neuroshima.editor.ui.canvas.CanvasMapper? = null
```

- [ ] **Step 2: Write failing SelectTool test**

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.assets.ImageCache
import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.ui.canvas.CanvasMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.swing.JPanel

class SelectToolTest {

    private fun solid(c: Color, w: Int, h: Int): BufferedImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics(); g.color = c; g.fillRect(0, 0, w, h); g.dispose()
        return img
    }

    private fun ctxWithLayer(@TempDir tmp: Path, layerImg: BufferedImage, props: LayerProperties): Pair<AppContext, Token> {
        val ctx = AppContext(tmp.resolve("bundled").also { it.toFile().mkdirs() },
                             tmp.resolve("user").also { it.toFile().mkdirs() },
                             tmp.resolve("prefs.json"))
        val asset = AssetPath.Bundled("a.png")
        ctx.imageCache.put(asset, layerImg)
        val token = Token.createUnit().apply { addLayer(Layer.create(asset, props)) }
        ctx.bag.addToken(token)
        ctx.viewState.setActiveToken(token.id)
        ctx.canvasMapper = CanvasMapper(
            widthSupplier = { 490 }, heightSupplier = { 490 },
            zoomSupplier = { 1f }, panXSupplier = { 0 }, panYSupplier = { 0 },
        )
        return ctx to token
    }

    private fun click(panel: JPanel, x: Int, y: Int, ctrl: Boolean = false): MouseEvent {
        val mods = if (ctrl) MouseEvent.CTRL_DOWN_MASK else 0
        return MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0L, mods, x, y, 1, false, MouseEvent.BUTTON1)
    }

    @Test
    fun `click on opaque pixel selects layer`(@TempDir tmp: Path) {
        val (ctx, token) = ctxWithLayer(tmp, solid(Color.RED, 100, 100), LayerProperties())
        val panel = JPanel()
        SelectTool().onMousePressed(ctx, click(panel, 245, 245))
        assertEquals(setOf(token.layers[0].id), ctx.viewState.selectedLayers)
    }

    @Test
    fun `click on transparent area clears selection`(@TempDir tmp: Path) {
        val img = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
        val (ctx, token) = ctxWithLayer(tmp, img, LayerProperties())
        ctx.viewState.replaceSelection(listOf(token.layers[0].id))
        val panel = JPanel()
        SelectTool().onMousePressed(ctx, click(panel, 245, 245))
        assertTrue(ctx.viewState.selectedLayers.isEmpty())
    }

    @Test
    fun `ctrl-click toggles selection`(@TempDir tmp: Path) {
        val (ctx, token) = ctxWithLayer(tmp, solid(Color.RED, 100, 100), LayerProperties())
        val panel = JPanel()
        SelectTool().onMousePressed(ctx, click(panel, 245, 245, ctrl = true))
        assertEquals(setOf(token.layers[0].id), ctx.viewState.selectedLayers)
        SelectTool().onMousePressed(ctx, click(panel, 245, 245, ctrl = true))
        assertTrue(ctx.viewState.selectedLayers.isEmpty())
    }
}
```

- [ ] **Step 3: Run tests, expect failure**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.ui.tools.SelectToolTest'`
Expected: FAIL — current `SelectTool` only clears selection.

- [ ] **Step 4: Rewrite SelectTool**

Replace `SelectTool.kt`:

```kotlin
package net.rafkos.neuroshima.editor.ui.tools

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.render.AffineBuilder
import net.rafkos.neuroshima.editor.render.LOGICAL_TOKEN_SIZE_PX
import net.rafkos.neuroshima.editor.ui.canvas.LOGICAL_CENTER
import java.awt.event.MouseEvent
import java.awt.geom.Point2D

private const val ALPHA_HIT_THRESHOLD: Int = 8

class SelectTool : Tool {
    override fun onMousePressed(ctx: AppContext, e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) return
        val mapper = ctx.canvasMapper ?: return
        val token = ctx.viewState.activeTokenId?.let { ctx.bag.findToken(it) } ?: return
        val logical = mapper.screenToLogical(e.point)
        val hitId = token.layers.asReversed().firstOrNull { layer ->
            val img = ctx.imageCache.get(layer.assetPath) ?: return@firstOrNull false
            val xform = AffineBuilder.build(layer.props, LOGICAL_CENTER, LOGICAL_CENTER, img.width, img.height)
            val inv = runCatching { xform.createInverse() }.getOrNull() ?: return@firstOrNull false
            val src = Point2D.Double()
            inv.transform(logical, src)
            val sx = src.x.toInt(); val sy = src.y.toInt()
            if (sx !in 0 until img.width || sy !in 0 until img.height) return@firstOrNull false
            val alpha = (img.getRGB(sx, sy) ushr 24) and 0xff
            alpha > ALPHA_HIT_THRESHOLD
        }?.id
        if (e.isControlDown) {
            if (hitId != null) {
                if (hitId in ctx.viewState.selectedLayers) ctx.viewState.deselectLayer(hitId)
                else ctx.viewState.selectLayer(hitId)
            }
        } else {
            if (hitId == null) ctx.viewState.clearSelection()
            else ctx.viewState.replaceSelection(listOf(hitId))
        }
        // suppress unused warning on LOGICAL_TOKEN_SIZE_PX
        @Suppress("UNUSED_EXPRESSION") LOGICAL_TOKEN_SIZE_PX
    }
}
```

(The `LOGICAL_TOKEN_SIZE_PX` reference exists only to keep the import chain explicit; remove it together with the import if you prefer.)

- [ ] **Step 5: Run tests, expect pass**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.ui.tools.SelectToolTest'`
Expected: PASS.

- [ ] **Step 6: Architecture test sanity-check**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.architecture.PackageBoundaryTest'`
Expected: PASS. `SelectTool` imports stay inside `ui` / `render` / `model`; no `persistence` (R8).

- [ ] **Step 7: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/tools/SelectTool.kt \
        src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt \
        src/test/kotlin/net/rafkos/neuroshima/editor/ui/tools/SelectToolTest.kt
git commit -m "feat(ui): SelectTool alpha-aware hit test + ctrl toggle semantics"
```

---

## Task 8: AppContext wires PreviewService and shutdown

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt`

- [ ] **Step 1: Wire PreviewService**

In `AppContext.kt`, after the existing field declarations and before `init { ... }`:

```kotlin
val previewService: PreviewService = PreviewService(bag, tokenRenderer)
```

Add imports:

```kotlin
import net.rafkos.neuroshima.editor.ui.preview.PreviewKey
import net.rafkos.neuroshima.editor.ui.preview.PreviewService
```

- [ ] **Step 2: Invalidate on model edits**

Inside `installInvalidationListener`, immediately after the existing `thumbnails.invalidateToken(touchedToken)` line, add:

```kotlin
previewService.invalidate(PreviewKey.TokenSnapshot(touchedToken))
```

And in `replaceBag`, after `installInvalidationListener(bag)`, add:

```kotlin
// any open subscriptions referencing dead tokens just stop firing; that's fine.
```

- [ ] **Step 3: Add shutdown()**

Append method to the class:

```kotlin
fun shutdown() {
    previewService.shutdown()
}
```

- [ ] **Step 4: Build + run all tests**

Run: `./gradlew test`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/app/AppContext.kt
git commit -m "feat(app): AppContext owns PreviewService and shutdown hook"
```

---

## Task 9: TokensCollectionPanel HQ snapshot subscription

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/TokensCollectionPanel.kt`

- [ ] **Step 1: Add subscription bookkeeping**

Replace the body of `TokensCollectionPanel` with this version (preserves existing imports plus three new ones):

```kotlin
package net.rafkos.neuroshima.editor.ui.panels

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.command.AddTokenCommand
import net.rafkos.neuroshima.editor.command.RemoveTokenCommand
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.render.ThumbnailRenderer
import net.rafkos.neuroshima.editor.ui.preview.PreviewKey
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.UUID
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
    private var currentSnapshotKey: PreviewKey.TokenSnapshot? = null
    private var currentSnapshotLabel: JLabel? = null

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
        currentSnapshotLabel = null
        val size = ctx.viewState.collectionThumbSize
        val activeId = ctx.viewState.activeTokenId
        for (token in ctx.bag.tokens) {
            val img = thumbnails.tokenThumbnail(token, size)
            val cell = JPanel(BorderLayout()).apply {
                preferredSize = Dimension(size + 8, size + 8)
                border = BorderFactory.createLineBorder(
                    if (token.id == activeId) Color.BLUE else Color.GRAY,
                    if (token.id == activeId) 2 else 1,
                )
            }
            val lbl = JLabel(ImageIcon(img))
            if (token.id == activeId) currentSnapshotLabel = lbl
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
        updateSnapshotSubscription(activeId, size)
    }

    private fun updateSnapshotSubscription(activeId: UUID?, sizePx: Int) {
        val prev = currentSnapshotKey
        if (prev != null && prev.tokenId != activeId) ctx.previewService.unsubscribe(prev)
        if (activeId == null) { currentSnapshotKey = null; return }
        val key = PreviewKey.TokenSnapshot(activeId)
        currentSnapshotKey = key
        ctx.previewService.subscribe(key, sizePx) { img ->
            currentSnapshotLabel?.icon = ImageIcon(img)
            currentSnapshotLabel?.repaint()
        }
    }
}
```

- [ ] **Step 2: Build, run all tests**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 3: Architecture test**

Run: `./gradlew test --tests 'net.rafkos.neuroshima.editor.architecture.PackageBoundaryTest'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/panels/TokensCollectionPanel.kt
git commit -m "feat(ui): tokens grid swaps to HQ snapshot for active token"
```

---

## Task 10: TokenCanvasPanel — button-tracked pan, composite cache, expose mapper

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt`

- [ ] **Step 1: Replace TokenCanvasPanel with the new version**

```kotlin
package net.rafkos.neuroshima.editor.ui.canvas

import net.rafkos.neuroshima.editor.app.AppContext
import net.rafkos.neuroshima.editor.app.AppDirs
import net.rafkos.neuroshima.editor.model.ModelEvent
import net.rafkos.neuroshima.editor.render.AffineBuilder
import net.rafkos.neuroshima.editor.render.LOGICAL_TOKEN_SIZE_PX
import net.rafkos.neuroshima.editor.render.LayerRenderer
import net.rafkos.neuroshima.editor.render.ProcessedLayerCache
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.UUID
import javax.imageio.ImageIO
import javax.swing.JPanel

const val LOGICAL_SIZE_PX: Int = LOGICAL_TOKEN_SIZE_PX

class TokenCanvasPanel(private val ctx: AppContext) : JPanel() {

    private val overlay: BufferedImage? = loadOverlay()
    var panX: Int = 0
    var panY: Int = 0
    private var panning: Boolean = false
    private var panOriginX: Int = 0
    private var panOriginY: Int = 0

    val mapper: CanvasMapper = CanvasMapper(
        widthSupplier = { width },
        heightSupplier = { height },
        zoomSupplier = { ctx.viewState.zoom },
        panXSupplier = { panX },
        panYSupplier = { panY },
    )

    private var compositeCache: BufferedImage? = null
    private var compositeForTokenId: UUID? = null

    init {
        preferredSize = Dimension(LOGICAL_SIZE_PX, LOGICAL_SIZE_PX)
        background = Color(60, 60, 60)
        ctx.bag.addListener { event ->
            when (event) {
                is ModelEvent.LayerAdded,
                is ModelEvent.LayerRemoved,
                is ModelEvent.LayerReordered,
                is ModelEvent.LayerPropsChanged -> compositeCache = null
                else -> {}
            }
            repaint()
        }
        ctx.viewState.addListener {
            if (ctx.viewState.activeTokenId != compositeForTokenId) compositeCache = null
            repaint()
        }

        addMouseWheelListener { e ->
            val factor = if (e.wheelRotation < 0) 1.1f else 1f / 1.1f
            ctx.viewState.setZoom(ctx.viewState.zoom * factor)
        }
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON2) {
                    panning = true; panOriginX = e.x; panOriginY = e.y
                }
            }
            override fun mouseReleased(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON2) panning = false
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                if (!panning) return
                panX += e.x - panOriginX
                panY += e.y - panOriginY
                panOriginX = e.x; panOriginY = e.y
                repaint()
            }
        })
    }

    private fun loadOverlay(): BufferedImage? {
        val file = AppDirs.overlayRoot.resolve("overlay.png")
        if (!file.isFile) return null
        return file.inputStream().use { ImageIO.read(it) }
    }

    private fun ensureComposite(tokenId: UUID): BufferedImage {
        val cached = compositeCache
        if (cached != null && compositeForTokenId == tokenId) return cached
        val out = BufferedImage(LOGICAL_SIZE_PX, LOGICAL_SIZE_PX, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            val token = ctx.bag.findToken(tokenId)
            if (token != null) {
                for (layer in token.layers) {
                    val source = ctx.imageCache.get(layer.assetPath) ?: continue
                    val key = ProcessedLayerCache.Key(layer.assetPath, layer.props)
                    val processed = ctx.processedCache.get(key)
                        ?: LayerRenderer.applyPixelOps(source, layer.props).also { ctx.processedCache.put(key, it) }
                    val xform = AffineBuilder.build(
                        props = layer.props,
                        canvasCenterX = LOGICAL_CENTER,
                        canvasCenterY = LOGICAL_CENTER,
                        imageWidth = processed.width,
                        imageHeight = processed.height,
                    )
                    g.drawImage(processed, xform, null)
                }
            }
        } finally {
            g.dispose()
        }
        compositeCache = out
        compositeForTokenId = tokenId
        return out
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val g2 = graphics as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        val zoom = ctx.viewState.zoom
        val centerX = width / 2.0
        val centerY = height / 2.0
        g2.translate(centerX + panX, centerY + panY)
        g2.scale(zoom.toDouble(), zoom.toDouble())
        g2.translate(-LOGICAL_CENTER, -LOGICAL_CENTER)
        g2.color = Color(220, 220, 220)
        g2.fillRect(0, 0, LOGICAL_SIZE_PX, LOGICAL_SIZE_PX)
        val tokenId = ctx.viewState.activeTokenId
        if (tokenId != null) {
            g2.drawImage(ensureComposite(tokenId), 0, 0, null)
        }
        if (ctx.viewState.showOverlay && overlay != null) {
            val ox = (LOGICAL_SIZE_PX - overlay.width) / 2
            val oy = (LOGICAL_SIZE_PX - overlay.height) / 2
            g2.drawImage(overlay, ox, oy, null)
        }
    }
}
```

- [ ] **Step 2: Build to confirm**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL. The `LOGICAL_SIZE_PX` top-level constant still exists at the same fully-qualified path; existing callers compile.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt
git commit -m "perf(ui): canvas composite cache + button-tracked pan + mapper field"
```

---

## Task 11: TokenCanvasPanel selection marker

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt`

- [ ] **Step 1: Add marker render after composite, before overlay**

In `paintComponent`, immediately after `g2.drawImage(ensureComposite(tokenId), 0, 0, null)`, insert a call to `drawSelectionMarkers(g2, tokenId)`. Add the method:

```kotlin
private fun drawSelectionMarkers(g2: Graphics2D, tokenId: UUID) {
    val selected = ctx.viewState.selectedLayers
    if (selected.isEmpty()) return
    val token = ctx.bag.findToken(tokenId) ?: return
    val composite = compositeCache ?: return
    val stroke = java.awt.BasicStroke(1.5f)
    g2.stroke = stroke
    for (layer in token.layers) {
        if (layer.id !in selected) continue
        val img = ctx.imageCache.get(layer.assetPath) ?: continue
        val xform = AffineBuilder.build(layer.props, LOGICAL_CENTER, LOGICAL_CENTER, img.width, img.height)
        val corners = arrayOf(
            java.awt.geom.Point2D.Double(0.0, 0.0),
            java.awt.geom.Point2D.Double(img.width.toDouble(), 0.0),
            java.awt.geom.Point2D.Double(img.width.toDouble(), img.height.toDouble()),
            java.awt.geom.Point2D.Double(0.0, img.height.toDouble()),
        )
        val projected = corners.map { xform.transform(it, null) as java.awt.geom.Point2D.Double }
        for (i in 0 until 4) {
            drawDashedEdge(g2, projected[i], projected[(i + 1) % 4], composite)
        }
    }
}

private fun drawDashedEdge(
    g2: Graphics2D,
    a: java.awt.geom.Point2D.Double,
    b: java.awt.geom.Point2D.Double,
    sampleSource: BufferedImage,
) {
    val dx = b.x - a.x; val dy = b.y - a.y
    val len = kotlin.math.hypot(dx, dy)
    if (len < 1.0) return
    val segLogical = 8.0   // approximate; logical px because g2 is in logical space
    val steps = kotlin.math.max(2, (len / segLogical).toInt())
    val nx = dx / steps; val ny = dy / steps
    var t = 0
    while (t < steps) {
        if (t % 2 == 0) {
            val x0 = a.x + nx * t; val y0 = a.y + ny * t
            val x1 = a.x + nx * (t + 1); val y1 = a.y + ny * (t + 1)
            val mx = ((x0 + x1) / 2).toInt().coerceIn(0, sampleSource.width - 1)
            val my = ((y0 + y1) / 2).toInt().coerceIn(0, sampleSource.height - 1)
            val argb = sampleSource.getRGB(mx, my)
            val r = 255 - ((argb ushr 16) and 0xff)
            val gr = 255 - ((argb ushr 8) and 0xff)
            val bl = 255 - (argb and 0xff)
            g2.color = Color(r, gr, bl)
            g2.draw(java.awt.geom.Line2D.Double(x0, y0, x1, y1))
        }
        t++
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full test suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/canvas/TokenCanvasPanel.kt
git commit -m "feat(ui): oriented dashed selection marker with per-segment inverse color"
```

---

## Task 12: MainFrame wires mapper + shutdown

**Files:**
- Modify: `src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt`

- [ ] **Step 1: Set `ctx.canvasMapper` and call `ctx.shutdown()`**

In `MainFrame.kt`, immediately after `val canvasComponent: TokenCanvasPanel = TokenCanvasPanel(ctx)` (still in the class body), add a Kotlin `init` block that sets `ctx.canvasMapper = canvasComponent.mapper`:

Insert just before the existing `init {` block:

```kotlin
init { ctx.canvasMapper = canvasComponent.mapper }
```

Then in the **existing** `windowClosing` handler, replace each of the three terminal paths so they call `ctx.shutdown()` before `System.exit`:

```kotlin
override fun windowClosing(e: WindowEvent) {
    if (!ctx.dirty) { ctx.savePrefs(); ctx.shutdown(); dispose(); System.exit(0); return }
    when (SaveBeforeCloseDialog.ask(this@MainFrame)) {
        SaveBeforeCloseDialog.Result.SAVE -> {
            if (menuBuilder.save()) { ctx.savePrefs(); ctx.shutdown(); dispose(); System.exit(0) }
        }
        SaveBeforeCloseDialog.Result.DISCARD -> {
            ctx.savePrefs(); ctx.shutdown(); dispose(); System.exit(0)
        }
        SaveBeforeCloseDialog.Result.CANCEL -> { /* stay open */ }
    }
}
```

- [ ] **Step 2: Build + tests**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 3: Manual smoke**

Run: `./gradlew run`
Verify in app:
1. Token grid: thumbnails are fit-scaled; nothing leaks off cells.
2. Selected token cell refines visibly within ~300 ms after edits.
3. Layer rows in right panel show only that one layer's content.
4. With Select tool: click on visible layer pixel selects it; dashed marker appears, oriented to layer rotation, with inverted colors over composite pixels.
5. Ctrl-click toggles selection.
6. Click on empty / transparent area clears selection.
7. Middle-mouse drag pans cleanly; no stuck pan after release.
8. Slider drag in Layer Properties: canvas redraws smoothly; no first-edit EDT hitch.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/net/rafkos/neuroshima/editor/ui/MainFrame.kt
git commit -m "chore(ui): wire canvasMapper and call shutdown on close"
```

---

## Self-Review Notes

- **Spec coverage:** Each spec component (1 TokenRenderer fit-scale, 2 ThumbnailRenderer sealed key, 3 PreviewService, 4 TokensCollectionPanel HQ, 5 LayersPanel, 6 SelectTool, 7 CanvasMapper, 8 TokenCanvasPanel composite + marker, 9 LayerRenderer vectorize) maps to a task above (1, 2, 6, 9, 3, 7, 5, 10+11, 4 respectively); MainFrame wiring covered in 12; AppContext in 7 (mapper field) + 8 (PreviewService).
- **Placeholder scan:** No TBD / TODO. All code blocks complete.
- **Type consistency:** `LOGICAL_TOKEN_SIZE_PX` (render) and `LOGICAL_CENTER` (ui.canvas) defined exactly once. `LOGICAL_SIZE_PX` retained in `ui.canvas.TokenCanvasPanel` for back-compat with existing references; aliased to `LOGICAL_TOKEN_SIZE_PX`. `ThumbnailRenderer.Key` is private sealed; only the `tokenThumbnail`/`layerThumbnail`/`invalidateToken` API is public.
- **Architecture:** No new rules. `ui.preview` lives under `ui`, allowed to use Swing + render + model. `render.TokenRenderer` exports `LOGICAL_TOKEN_SIZE_PX` (top-level const, no Swing dep). R5 unaffected.
