package net.rafkos.neuroshima.editor.command

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.LayerSnapshot
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class PasteLayersCommandTest {

    private fun bagWithLayers(vararg assets: String): Pair<TokenBag, Token> {
        val bag = TokenBag()
        val token = Token.createUnit()
        bag.addToken(token)
        assets.forEach { token.addLayer(TokenSide.FRONT, Layer.create(AssetPath.Bundled(it))) }
        return bag to token
    }

    @Test
    fun `execute inserts snapshots at given index preserving order`() {
        val (bag, token) = bagWithLayers("a.png", "b.png", "c.png")
        // layers = [a(0), b(1), c(2)]
        val snapshots = listOf(
            LayerSnapshot(AssetPath.Bundled("x.png"), LayerProperties(rotation = 10f)),
            LayerSnapshot(AssetPath.Bundled("y.png"), LayerProperties(rotation = 20f)),
        )
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        val cmd = PasteLayersCommand(token.id, TokenSide.FRONT, snapshots, insertIndex = 2, layerIds = ids)
        cmd.execute(bag)

        val layers = token.layers(TokenSide.FRONT)
        assertEquals(5, layers.size)
        // Expected order: [a(0), b(1), x(2), y(3), c(4)]
        assertEquals("a.png", (layers[0].assetPath as AssetPath.Bundled).relativePath)
        assertEquals("b.png", (layers[1].assetPath as AssetPath.Bundled).relativePath)
        assertEquals("x.png", (layers[2].assetPath as AssetPath.Bundled).relativePath)
        assertEquals(10f, layers[2].props.rotation)
        assertEquals("y.png", (layers[3].assetPath as AssetPath.Bundled).relativePath)
        assertEquals(20f, layers[3].props.rotation)
        assertEquals("c.png", (layers[4].assetPath as AssetPath.Bundled).relativePath)
    }

    @Test
    fun `execute with insertIndex equal to layers size appends at top`() {
        val (bag, token) = bagWithLayers("a.png", "b.png")
        val snapshots = listOf(LayerSnapshot(AssetPath.Bundled("x.png"), LayerProperties()))
        val ids = listOf(UUID.randomUUID())
        val cmd = PasteLayersCommand(token.id, TokenSide.FRONT, snapshots, insertIndex = 2, layerIds = ids)
        cmd.execute(bag)

        val layers = token.layers(TokenSide.FRONT)
        assertEquals(3, layers.size)
        assertEquals("x.png", (layers[2].assetPath as AssetPath.Bundled).relativePath)
    }

    @Test
    fun `undo removes all pasted layers`() {
        val (bag, token) = bagWithLayers("a.png")
        val snapshots = listOf(
            LayerSnapshot(AssetPath.Bundled("x.png"), LayerProperties()),
            LayerSnapshot(AssetPath.Bundled("y.png"), LayerProperties()),
        )
        val ids = listOf(UUID.randomUUID(), UUID.randomUUID())
        val cmd = PasteLayersCommand(token.id, TokenSide.FRONT, snapshots, insertIndex = 1, layerIds = ids)
        cmd.execute(bag)
        assertEquals(3, token.layers(TokenSide.FRONT).size)
        cmd.undo(bag)
        assertEquals(1, token.layers(TokenSide.FRONT).size)
        assertEquals("a.png", (token.layers(TokenSide.FRONT).first().assetPath as AssetPath.Bundled).relativePath)
    }

    @Test
    fun `redo after undo reinserts layers with same IDs`() {
        val (bag, token) = bagWithLayers("a.png")
        val snapshots = listOf(LayerSnapshot(AssetPath.Bundled("x.png"), LayerProperties()))
        val ids = listOf(UUID.randomUUID())
        val cmd = PasteLayersCommand(token.id, TokenSide.FRONT, snapshots, insertIndex = 1, layerIds = ids)
        cmd.execute(bag)
        val pastedId = token.layers(TokenSide.FRONT).last().id
        cmd.undo(bag)
        cmd.execute(bag)
        assertEquals(pastedId, token.layers(TokenSide.FRONT).last().id)
    }

    @Test
    fun `pasted layers are independent copies — mutating props does not affect snapshot`() {
        val (bag, token) = bagWithLayers()
        val props = LayerProperties(rotation = 30f)
        val snapshots = listOf(LayerSnapshot(AssetPath.Bundled("x.png"), props))
        val ids = listOf(UUID.randomUUID())
        val cmd = PasteLayersCommand(token.id, TokenSide.FRONT, snapshots, insertIndex = 0, layerIds = ids)
        cmd.execute(bag)
        assertEquals(30f, token.layers(TokenSide.FRONT).first().props.rotation)
    }
}
