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
        val ctx = AppContext(
            tmp.resolve("bundled").also { it.toFile().mkdirs() },
            tmp.resolve("user").also { it.toFile().mkdirs() },
            tmp.resolve("prefs.json"),
        )
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
