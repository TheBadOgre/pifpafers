package net.rafkos.neuroshima.editor.ui.publish

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel
import javax.swing.Scrollable

class PagePreviewPanel : JPanel(), Scrollable {

    private var pages: List<BufferedImage> = emptyList()
    private val thumbWidth = 240
    private val margin = 16
    private val labelGap = 4

    init {
        background = Color(80, 80, 80)
    }

    fun setPages(thumbnails: List<BufferedImage>) {
        pages = thumbnails
        revalidate(); repaint()
    }

    private data class Slot(val x: Int, val y: Int, val imgH: Int)

    private fun computeSlots(availW: Int): List<Slot> {
        val slots = mutableListOf<Slot>()
        var x = margin
        var y = margin
        var rowH = 0
        val labelH = getFontMetrics(font).height
        for (img in pages) {
            val h = (img.height.toDouble() * thumbWidth / img.width).toInt()
            if (x > margin && x + thumbWidth + margin > availW) {
                x = margin
                y += rowH + labelGap + labelH + margin
                rowH = 0
            }
            slots += Slot(x, y, h)
            x += thumbWidth + margin
            rowH = maxOf(rowH, h)
        }
        return slots
    }

    override fun getPreferredSize(): Dimension {
        val availW = width.takeIf { it > 0 } ?: (parent?.width ?: 900)
        val labelH = getFontMetrics(font).height
        val slots = computeSlots(availW)
        val totalH = if (slots.isEmpty()) 200
            else slots.maxOf { it.y + it.imgH } + labelGap + labelH + margin
        return Dimension(availW, totalH)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            val fm = g2.fontMetrics
            val slots = computeSlots(width.takeIf { it > 0 } ?: 900)
            g2.color = Color.LIGHT_GRAY
            for ((i, slot) in slots.withIndex()) {
                g2.drawImage(pages[i], slot.x, slot.y, thumbWidth, slot.imgH, null)
                val label = "${i + 1}"
                val lx = slot.x + (thumbWidth - fm.stringWidth(label)) / 2
                val ly = slot.y + slot.imgH + labelGap + fm.ascent
                g2.drawString(label, lx, ly)
            }
        } finally {
            g2.dispose()
        }
    }

    override fun getScrollableTracksViewportWidth() = true
    override fun getScrollableTracksViewportHeight() = false
    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(r: Rectangle, orientation: Int, direction: Int) = 20
    override fun getScrollableBlockIncrement(r: Rectangle, orientation: Int, direction: Int) = 150
}
