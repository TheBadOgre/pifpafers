package net.rafkos.neuroshima.editor.persistence

import net.rafkos.neuroshima.editor.model.AssetPath
import net.rafkos.neuroshima.editor.model.Layer
import net.rafkos.neuroshima.editor.model.LayerProperties
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenBag
import net.rafkos.neuroshima.editor.model.TokenKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class JsonBagStoreTest {

    @Test
    fun `round-trip preserves name, kinds, layers, and props`(@TempDir tmp: Path) {
        val bag = TokenBag().apply { name = "My Army" }
        val unit = Token.createUnit()
        unit.addLayer(Layer.create(AssetPath.Bundled("bg/red.png")))
        unit.addLayer(
            Layer.create(
                AssetPath.User("custom/icon.png"),
                LayerProperties(offsetX = 5, rotation = 90f, scale = 0.5f, opacity = 0.75f),
            )
        )
        bag.addToken(unit)
        bag.addToken(Token.createModifier())

        val file = tmp.resolve("army.box")
        // resolver returns true for any path in this test (no asset validation here)
        val store = JsonBagStore(assetResolver = { true })
        store.save(bag, file)
        val loaded = store.load(file)

        assertEquals(bag.name, loaded.name)
        assertEquals(bag.tokens.size, loaded.tokens.size)
        assertEquals(TokenKind.UNIT, loaded.tokens[0].kind)
        assertEquals(TokenKind.MODIFIER, loaded.tokens[1].kind)
        assertEquals(2, loaded.tokens[0].layers.size)
        assertEquals(AssetPath.Bundled("bg/red.png"), loaded.tokens[0].layers[0].assetPath)
        assertEquals(AssetPath.User("custom/icon.png"), loaded.tokens[0].layers[1].assetPath)
        assertEquals(5, loaded.tokens[0].layers[1].props.offsetX)
        assertEquals(90f, loaded.tokens[0].layers[1].props.rotation)
        assertEquals(0.5f, loaded.tokens[0].layers[1].props.scale)
        assertEquals(0.75f, loaded.tokens[0].layers[1].props.opacity)
    }

    @Test
    fun `load rejects unknown schema version`(@TempDir tmp: java.nio.file.Path) {
        val file = tmp.resolve("future.box")
        java.nio.file.Files.writeString(
            file,
            """{"schemaVersion": 999, "name": "x", "tokens": []}"""
        )
        val store = JsonBagStore(assetResolver = { true })
        val ex = org.junit.jupiter.api.Assertions.assertThrows(
            SchemaVersionException::class.java
        ) { store.load(file) }
        org.junit.jupiter.api.Assertions.assertEquals(999, ex.found)
    }

    @Test
    fun `load reports every missing asset`(@TempDir tmp: java.nio.file.Path) {
        val bag = TokenBag()
        val t = Token.createUnit()
        t.addLayer(Layer.create(AssetPath.Bundled("missing/a.png")))
        t.addLayer(Layer.create(AssetPath.User("missing/b.png")))
        t.addLayer(Layer.create(AssetPath.Bundled("ok/c.png")))
        bag.addToken(t)
        val file = tmp.resolve("army.box")
        JsonBagStore(assetResolver = { true }).save(bag, file)

        val strictResolver: (AssetPath) -> Boolean = { ap -> ap.relativePath == "ok/c.png" }
        val store = JsonBagStore(assetResolver = strictResolver)
        val ex = org.junit.jupiter.api.Assertions.assertThrows(
            MissingAssetsException::class.java
        ) { store.load(file) }
        org.junit.jupiter.api.Assertions.assertEquals(
            listOf("bundled://missing/a.png", "user://missing/b.png"),
            ex.missing.map { it.uri },
        )
    }

    @Test
    fun `save writes via temp file then rename`(@TempDir tmp: java.nio.file.Path) {
        val file = tmp.resolve("army.box")
        val store = JsonBagStore(assetResolver = { true })
        store.save(TokenBag().apply { name = "n" }, file)
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(file))
        org.junit.jupiter.api.Assertions.assertFalse(
            java.nio.file.Files.exists(file.resolveSibling("army.box.tmp"))
        )
    }
}
