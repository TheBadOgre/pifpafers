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
            lastFile = "C:/Users/me/army.box",
            lastBoxDir = "C:/Users/me",
            lastImagesDir = "C:/Users/me/exports",
            lastPdfDir = "C:/Users/me/pdf",
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
        assertEquals(null, p.lastFile)
        assertEquals(null, p.lastBoxDir)
        assertEquals(null, p.lastImagesDir)
        assertEquals(null, p.lastPdfDir)
    }

    @Test
    fun `unknown keys in json are ignored`(@TempDir tmp: Path) {
        val file = tmp.resolve("prefs.json")
        file.toFile().writeText("""{"collectionThumbSize":128,"lastOpenedBag":"/old/army.box","unknownKey":42}""")
        val store = PrefsStore(file)
        val loaded = store.load()
        assertEquals(128, loaded.collectionThumbSize)
        assertEquals(null, loaded.lastFile)
    }
}
