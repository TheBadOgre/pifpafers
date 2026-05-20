package net.rafkos.neuroshima.editor.ui.publish

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.JPanel

class PagePreviewPanel : JPanel() {

    private var pages: List<BufferedImage> = emptyList()
    private val thumbWidth = 240

    init {
        background = Color(80, 80, 80)
        preferredSize = Dimension(900, 600)
    }

    fun setPages(thumbnails: List<BufferedImage>) {
        pages = thumbnails
        revalidate(); repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val margin = 16
            var x = margin
            var y = margin
            var rowHeight = 0
            for (img in pages) {
                val h = (img.height.toDouble() * thumbWidth / img.width).toInt()
                if (x + thumbWidth + margin > width) {
                    x = margin
                    y += rowHeight + margin
                    rowHeight = 0
                }
                g2.drawImage(img, x, y, thumbWidth, h, null)
                x += thumbWidth + margin
                rowHeight = maxOf(rowHeight, h)
            }
        } finally {
            g2.dispose()
        }
    }
}
