package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.model.PageFormat
import net.rafkos.neuroshima.editor.model.PublishSettings
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenKind
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageLayoutPlannerTest {
    private fun newToken(kind: TokenKind, maskId: Int): Token =
        Token(UUID.randomUUID(), kind, maskId = maskId)

    @Test
    fun `empty bag produces empty plan list`() {
        val plans = PageLayoutPlanner(PublishSettings(), emptyList()).plan()
        assertTrue(plans.isEmpty())
    }

    @Test
    fun `single hex produces front and back page`() {
        val t = newToken(TokenKind.UNIT, 0)
        val plans = PageLayoutPlanner(PublishSettings(), listOf(t)).plan()
        assertEquals(2, plans.size)
        assertEquals(0, plans[0].pageIndex)
        assertEquals(0, plans[1].pageIndex)
        assertEquals(false, plans[0].isBackPage)
        assertEquals(true, plans[1].isBackPage)
        assertEquals(1, plans[0].placements.size)
        assertEquals(1, plans[1].placements.size)
    }

    @Test
    fun `invertBackSide mirrors x across page center`() {
        val t = newToken(TokenKind.UNIT, 0)
        val s = PublishSettings(invertBackSide = true)
        val plans = PageLayoutPlanner(s, listOf(t)).plan()
        val front = plans[0]
        val back = plans[1]
        val fx = front.placements[0].centerXPx
        val bx = back.placements[0].centerXPx
        val pageCenter = front.widthPx / 2.0
        assertEquals(2.0 * pageCenter - fx, bx, 0.5)
    }

    @Test
    fun `mix of hex and circle puts hex rows first`() {
        val tokens = listOf(
            newToken(TokenKind.UNIT, 0),
            newToken(TokenKind.UNIT, 1),
            newToken(TokenKind.MODIFIER, 2),
            newToken(TokenKind.MODIFIER, 3),
        )
        val plans = PageLayoutPlanner(PublishSettings(), tokens).plan()
        val front = plans[0]
        assertEquals(TokenKind.UNIT, front.placements[0].token.kind)
        assertEquals(TokenKind.UNIT, front.placements[1].token.kind)
        assertEquals(TokenKind.MODIFIER, front.placements[2].token.kind)
        assertEquals(TokenKind.MODIFIER, front.placements[3].token.kind)
        val maxHexY = front.placements.filter { it.token.kind == TokenKind.UNIT }.maxOf { it.centerYPx }
        val minCircleY = front.placements.filter { it.token.kind == TokenKind.MODIFIER }.minOf { it.centerYPx }
        assertTrue(maxHexY < minCircleY)
    }

    @Test
    fun `overflow produces multi-page output`() {
        val tokens = (0 until 60).map { newToken(TokenKind.UNIT, it) }
        val plans = PageLayoutPlanner(PublishSettings(pageFormat = PageFormat.A5), tokens).plan()
        assertTrue(plans.size >= 4)
        assertEquals(0, plans.size % 2)
        for (i in 0 until plans.size step 2) {
            assertEquals(plans[i].pageIndex, plans[i + 1].pageIndex)
            assertEquals(false, plans[i].isBackPage)
            assertEquals(true, plans[i + 1].isBackPage)
        }
    }
}
