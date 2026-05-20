package net.rafkos.neuroshima.editor.persistence

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.PageFormat
import net.rafkos.neuroshima.editor.model.PublishSettings
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.model.TokenSide
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonBagStoreV3Test {

    @Test
    fun `round-trip preserves maskId sameSides printSettings`(@TempDir tmp: Path) {
        val bag = TokenBag().apply {
            name = "Test"
            updatePrintSettings(PublishSettings(dpi = 600, pageFormat = PageFormat.A3,
                invertBackSide = true, renderOverlay = false))
        }
        val t = Token(UUID.randomUUID(), TokenKind.UNIT, maskId = 42, sameSides = true)
        t.addLayer(TokenSide.FRONT, Layer(UUID.randomUUID(),
            AssetPath.parse("bundled://units/x.png"),
            LayerProperties(0, 0, 0f, 1f, 1f, 0f, 1f, 1f, false)))
        bag.addToken(t)

        val store = JsonBagStore(assetResolver = { true })
        val file = tmp.resolve("a.box")
        store.save(bag, file)

        val loaded = store.load(file)
        assertEquals(42, loaded.tokens[0].maskId)
        assertTrue(loaded.tokens[0].sameSides)
        assertEquals(600, loaded.printSettings.dpi)
        assertEquals(PageFormat.A3, loaded.printSettings.pageFormat)
        assertTrue(loaded.printSettings.invertBackSide)
        assertFalse(loaded.printSettings.renderOverlay)
    }

    @Test
    fun `v2 file is rejected with SchemaVersionException`(@TempDir tmp: Path) {
        val v2 = """{"schemaVersion":2,"name":"Old","tokens":[]}"""
        val file = tmp.resolve("old.box")
        file.writeText(v2)
        val store = JsonBagStore(assetResolver = { true })
        assertFailsWith<SchemaVersionException> { store.load(file) }
    }

    @Test
    fun `save and load preserves all maskIds in order`(@TempDir tmp: Path) {
        val bag = TokenBag()
        for (i in 0 until 5) bag.addToken(Token(UUID.randomUUID(), TokenKind.UNIT, maskId = i))
        val store = JsonBagStore(assetResolver = { true })
        val file = tmp.resolve("ids.box")
        store.save(bag, file)
        val loaded = store.load(file)
        assertEquals(listOf(0, 1, 2, 3, 4), loaded.tokens.map { it.maskId })
    }
}
