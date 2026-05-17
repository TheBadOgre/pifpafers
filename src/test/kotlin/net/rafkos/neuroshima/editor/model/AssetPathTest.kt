package net.rafkos.neuroshima.editor.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AssetPathTest {

    @Test
    fun `BundledPath formats as bundled URI`() {
        assertEquals("bundled://units/red.png", AssetPath.Bundled("units/red.png").uri)
    }

    @Test
    fun `UserPath formats as user URI`() {
        assertEquals("user://my/icon.png", AssetPath.User("my/icon.png").uri)
    }

    @Test
    fun `parse round-trips bundled URI`() {
        val p = AssetPath.parse("bundled://units/red.png")
        assertInstanceOf(AssetPath.Bundled::class.java, p)
        assertEquals("units/red.png", p.relativePath)
    }

    @Test
    fun `parse round-trips user URI`() {
        val p = AssetPath.parse("user://x/y.png")
        assertInstanceOf(AssetPath.User::class.java, p)
        assertEquals("x/y.png", p.relativePath)
    }

    @Test
    fun `parse rejects unknown scheme`() {
        assertThrows(IllegalArgumentException::class.java) {
            AssetPath.parse("file:///abs/path.png")
        }
    }

    @Test
    fun `parse rejects malformed URI`() {
        assertThrows(IllegalArgumentException::class.java) { AssetPath.parse("nope") }
    }
}
