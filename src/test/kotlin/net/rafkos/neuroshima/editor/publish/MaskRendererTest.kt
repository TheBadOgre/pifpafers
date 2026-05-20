package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenKind
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MaskRendererTest {

    @Test
    fun `background is solid black`() {
        val plan = PagePlan(0, false, 200, 300, emptyList())
        val img = MaskRenderer().renderPage(plan, plan.widthPx, plan.heightPx)
        assertEquals(0x000000, img.getRGB(0, 0) and 0xFFFFFF)
        assertEquals(0x000000, img.getRGB(199, 299) and 0xFFFFFF)
    }

    @Test
    fun `token center pixel matches encoded RGB for that maskId`() {
        val t = Token(UUID.randomUUID(), TokenKind.UNIT, maskId = 7)
        val placement = PagePlan.Placement(t, 200.0, 200.0, 0.0)
        val plan = PagePlan(0, false, 400, 400, listOf(placement))
        val img = MaskRenderer().renderPage(plan, plan.widthPx, plan.heightPx, pxPerMm = 10.0)
        val expected = MaskIdCodec.encode(7)
        assertEquals(expected, img.getRGB(200, 200) and 0xFFFFFF)
    }

    @Test
    fun `no anti-aliased intermediate pixels`() {
        val t = Token(UUID.randomUUID(), TokenKind.MODIFIER, maskId = 3)
        val placement = PagePlan.Placement(t, 200.0, 200.0, 0.0)
        val plan = PagePlan(0, false, 400, 400, listOf(placement))
        val img = MaskRenderer().renderPage(plan, plan.widthPx, plan.heightPx, pxPerMm = 10.0)
        val tokenRgb = MaskIdCodec.encode(3)
        for (px in 0 until 400) for (py in 0 until 400) {
            val v = img.getRGB(px, py) and 0xFFFFFF
            assert(v == 0x000000 || v == tokenRgb) {
                "Intermediate pixel at ($px,$py) = ${String.format("%06X", v)}"
            }
        }
    }
}
