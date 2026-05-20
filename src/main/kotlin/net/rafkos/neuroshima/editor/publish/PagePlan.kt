package net.rafkos.neuroshima.editor.publish

import net.rafkos.neuroshima.editor.model.Token

data class PagePlan(
    val pageIndex: Int,
    val isBackPage: Boolean,
    val widthPx: Int,
    val heightPx: Int,
    val placements: List<Placement>,
) {
    data class Placement(
        val token: Token,
        val centerXPx: Double,
        val centerYPx: Double,
        val rotationDeg: Double = 0.0,
    )
}
