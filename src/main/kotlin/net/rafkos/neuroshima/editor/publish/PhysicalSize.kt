package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.render.ModifierTokenShape
import net.rafkos.neuroshima.editor.render.TokenShape
import net.rafkos.neuroshima.editor.render.UnitTokenShape

object PhysicalSize {
    const val HEX_EDGE_MM: Double = 18.0
    const val HEX_DIAGONAL_MM: Double = 36.0
    val HEX_FLAT_TO_FLAT_MM: Double = Math.sqrt(3.0) * HEX_EDGE_MM
    const val CIRCLE_DIAMETER_MM: Double = 18.0

    const val MARGIN_MM: Double = 5.0

    fun mmToPx(mm: Double, dpi: Int): Int = Math.round(mm * dpi / 25.4).toInt()
    fun mmToPt(mm: Double): Double = mm * 2.8346456692913

    val HEX_TIGHT_WIDTH_MM: Double = HEX_DIAGONAL_MM
    val HEX_TIGHT_HEIGHT_MM: Double = HEX_FLAT_TO_FLAT_MM

    val BLEED_PX: Int = TokenShape.BLEED_PX

    val HEX_BLEED_WIDTH_MM: Double =
        HEX_TIGHT_WIDTH_MM * (UnitTokenShape.boundingW + 2.0 * BLEED_PX) / UnitTokenShape.boundingW
    val HEX_BLEED_HEIGHT_MM: Double =
        HEX_TIGHT_HEIGHT_MM * (UnitTokenShape.boundingH + 2.0 * BLEED_PX) / UnitTokenShape.boundingH

    val CIRCLE_BLEED_DIAMETER_MM: Double =
        CIRCLE_DIAMETER_MM * (ModifierTokenShape.boundingW + 2.0 * BLEED_PX) / ModifierTokenShape.boundingW
}
