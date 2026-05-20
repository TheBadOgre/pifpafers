package net.rafkos.neuroshima.editor.publish

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MaskIdCodecTest {
    @Test
    fun `id 0 encodes to white`() {
        assertEquals(0xFFFFFF, MaskIdCodec.encode(0))
    }

    @Test
    fun `round-trip preserves id`() {
        for (id in listOf(0, 1, 100, 0xFFFE, 0xFFFFFE)) {
            assertEquals(id, MaskIdCodec.decode(MaskIdCodec.encode(id)))
        }
    }

    @Test
    fun `background black does not collide with any valid id encoding`() {
        for (id in listOf(0, 1, 100, 0xFFFE, 0xFFFFFE)) {
            assertNotEquals(0x000000, MaskIdCodec.encode(id))
        }
    }
}
