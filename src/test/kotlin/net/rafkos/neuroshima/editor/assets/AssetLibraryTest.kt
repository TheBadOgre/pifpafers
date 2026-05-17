package net.rafkos.neuroshima.editor.assets

import net.rafkos.neuroshima.editor.model.AssetPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AssetLibraryTest {

    private fun touch(p: Path) {
        Files.createDirectories(p.parent)
        Files.writeString(p, "x")
    }

    @Test
    fun `lists assets from bundled root only`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        touch(bundled.resolve("units/red.png"))
        touch(bundled.resolve("modifiers/dmg.png"))
        val lib = AssetLibrary(bundledRoot = bundled, userRoot = tmp.resolve("user"))
        lib.scan()
        val all = lib.allAssets().map { it.uri }.toSet()
        assertEquals(setOf("bundled://units/red.png", "bundled://modifiers/dmg.png"), all)
    }

    @Test
    fun `merges bundled and user - bundled wins on conflict`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        touch(bundled.resolve("icons/a.png"))
        touch(user.resolve("icons/b.png"))
        touch(bundled.resolve("shared.png"))
        touch(user.resolve("shared.png"))
        val lib = AssetLibrary(bundled, user)
        lib.scan()
        val all = lib.allAssets().map { it.uri }.toSet()
        assertEquals(
            setOf("bundled://icons/a.png", "user://icons/b.png", "bundled://shared.png"),
            all,
        )
    }

    @Test
    fun `ignores non-png files`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        touch(bundled.resolve("ok.png"))
        touch(bundled.resolve("notes.txt"))
        val lib = AssetLibrary(bundled, tmp.resolve("user"))
        lib.scan()
        assertEquals(listOf("bundled://ok.png"), lib.allAssets().map { it.uri })
    }

    @Test
    fun `resolveFile returns concrete path for bundled and user`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        val user = tmp.resolve("user")
        val bFile = bundled.resolve("a.png"); touch(bFile)
        val uFile = user.resolve("b.png"); touch(uFile)
        val lib = AssetLibrary(bundled, user)
        lib.scan()
        assertEquals(bFile, lib.resolveFile(AssetPath.Bundled("a.png")))
        assertEquals(uFile, lib.resolveFile(AssetPath.User("b.png")))
        assertNull(lib.resolveFile(AssetPath.Bundled("nope.png")))
    }

    @Test
    fun `tree groups assets by folder`(@TempDir tmp: Path) {
        val bundled = tmp.resolve("bundled")
        touch(bundled.resolve("units/red.png"))
        touch(bundled.resolve("units/blue.png"))
        touch(bundled.resolve("modifiers/dmg.png"))
        val lib = AssetLibrary(bundled, tmp.resolve("user"))
        lib.scan()
        val root = lib.tree()
        assertEquals(setOf("modifiers", "units"), root.childFolders.map { it.name }.toSet())
        val units = root.childFolders.first { it.name == "units" }
        assertEquals(setOf("red.png", "blue.png"), units.assets.map { it.relativePath.substringAfterLast('/') }.toSet())
    }
}
