package net.rafkos.neuroshima.editor.model

data class PublishSettings(
    val dpi: Int = 300,
    val pageFormat: PageFormat = PageFormat.A4,
    val invertBackSide: Boolean = false,
    val renderOverlay: Boolean = true,
    val marginMm: Double = 25.0,
)

enum class PageFormat(val widthMm: Double, val heightMm: Double) {
    A3(297.0, 420.0),
    A4(210.0, 297.0),
    A5(148.0, 210.0),
}
