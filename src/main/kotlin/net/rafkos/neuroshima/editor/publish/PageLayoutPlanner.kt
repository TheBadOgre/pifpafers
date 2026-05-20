package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.model.PublishSettings
import net.rafkos.neuroshima.editor.model.Token
import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.publish.PhysicalSize.CIRCLE_BLEED_DIAMETER_MM
import net.rafkos.neuroshima.editor.publish.PhysicalSize.HEX_BLEED_HEIGHT_MM
import net.rafkos.neuroshima.editor.publish.PhysicalSize.HEX_BLEED_WIDTH_MM
import net.rafkos.neuroshima.editor.publish.PhysicalSize.MARGIN_MM
import net.rafkos.neuroshima.editor.publish.PhysicalSize.mmToPx

class PageLayoutPlanner(
    private val settings: PublishSettings,
    private val tokens: List<Token>,
) {

    fun plan(): List<PagePlan> {
        if (tokens.isEmpty()) return emptyList()

        val pageWidthPx = mmToPx(settings.pageFormat.widthMm, settings.dpi)
        val pageHeightPx = mmToPx(settings.pageFormat.heightMm, settings.dpi)
        val marginPx = mmToPx(MARGIN_MM, settings.dpi)
        val usableW = pageWidthPx - 2 * marginPx
        val usableH = pageHeightPx - 2 * marginPx

        val hexWPx = mmToPx(HEX_BLEED_WIDTH_MM, settings.dpi)
        val hexHPx = mmToPx(HEX_BLEED_HEIGHT_MM, settings.dpi)
        val circleDPx = mmToPx(CIRCLE_BLEED_DIAMETER_MM, settings.dpi)
        val hexRowPitch = (hexHPx * 0.75).toInt()

        val sorted = tokens.sortedBy { if (it.kind == TokenKind.UNIT) 0 else 1 }

        data class FrontPage(
            val placements: MutableList<PagePlan.Placement> = mutableListOf(),
        )

        val frontPages = mutableListOf<FrontPage>()
        var current = FrontPage().also { frontPages += it }
        var rowY = marginPx
        var rowKind: TokenKind? = null
        var rowItemIndex = 0
        var rowIndex = 0
        var rowOffsetX = false

        fun rowItemHeight(kind: TokenKind): Int = when (kind) {
            TokenKind.UNIT -> hexHPx
            TokenKind.MODIFIER -> circleDPx
        }

        fun rowItemPitch(kind: TokenKind): Int = when (kind) {
            TokenKind.UNIT -> hexWPx
            TokenKind.MODIFIER -> circleDPx
        }

        fun newRow(kind: TokenKind) {
            val pitch = when (kind) {
                TokenKind.UNIT -> hexRowPitch
                TokenKind.MODIFIER -> circleDPx
            }
            if (rowKind != null) rowY += pitch
            rowKind = kind
            rowItemIndex = 0
            rowIndex += 1
            rowOffsetX = (kind == TokenKind.UNIT) && (rowIndex % 2 == 0)
        }

        fun startNewPage() {
            current = FrontPage().also { frontPages += it }
            rowY = marginPx
            rowKind = null
            rowIndex = 0
            rowOffsetX = false
        }

        for (token in sorted) {
            val kind = token.kind
            val itemPitch = rowItemPitch(kind)
            val itemHeight = rowItemHeight(kind)

            if (rowKind == null || rowKind != kind) {
                if (rowY + itemHeight > marginPx + usableH) {
                    startNewPage()
                }
                newRow(kind)
            }

            val offsetX = if (rowOffsetX) itemPitch / 2 else 0
            val slotX = marginPx + offsetX + rowItemIndex * itemPitch + itemPitch / 2

            if (slotX + itemPitch / 2 > marginPx + usableW) {
                if (rowY + rowItemHeight(kind) + (rowItemHeight(kind) / 2) > marginPx + usableH) {
                    startNewPage()
                }
                newRow(kind)
            }

            val xPx = marginPx + (if (rowOffsetX) itemPitch / 2 else 0) + rowItemIndex * itemPitch + itemPitch / 2
            val yPx = rowY + itemHeight / 2

            if (yPx + itemHeight / 2 > marginPx + usableH) {
                startNewPage()
                newRow(kind)
                val xPx2 = marginPx + (if (rowOffsetX) itemPitch / 2 else 0) + rowItemIndex * itemPitch + itemPitch / 2
                val yPx2 = rowY + itemHeight / 2
                current.placements += PagePlan.Placement(token, xPx2.toDouble(), yPx2.toDouble())
            } else {
                current.placements += PagePlan.Placement(token, xPx.toDouble(), yPx.toDouble())
            }
            rowItemIndex += 1
        }

        val plans = mutableListOf<PagePlan>()
        for ((idx, fp) in frontPages.withIndex()) {
            plans += PagePlan(idx, false, pageWidthPx, pageHeightPx, fp.placements.toList())
            val backPlacements = fp.placements.map { p ->
                if (settings.invertBackSide) {
                    p.copy(centerXPx = pageWidthPx - p.centerXPx)
                } else {
                    p
                }
            }
            plans += PagePlan(idx, true, pageWidthPx, pageHeightPx, backPlacements)
        }
        return plans
    }
}
