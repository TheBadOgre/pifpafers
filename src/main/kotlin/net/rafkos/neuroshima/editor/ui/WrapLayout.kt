package net.rafkos.neuroshima.editor.ui

import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

class WrapLayout(align: Int = LEFT, hgap: Int = 4, vgap: Int = 4) : FlowLayout(align, hgap, vgap) {

    override fun preferredLayoutSize(target: Container): Dimension = layoutSize(target, preferred = true)
    override fun minimumLayoutSize(target: Container): Dimension = layoutSize(target, preferred = false)

    private fun layoutSize(target: Container, preferred: Boolean): Dimension {
        synchronized(target.treeLock) {
            var maxWidth = target.width
            val scroll = SwingUtilities.getAncestorOfClass(JScrollPane::class.java, target)
            if (scroll != null) maxWidth = (scroll as JScrollPane).viewport.width
            if (maxWidth <= 0) maxWidth = Int.MAX_VALUE

            val insets = target.insets
            val available = maxWidth - insets.left - insets.right - hgap * 2

            var rowW = 0; var rowH = 0; var totalH = insets.top + insets.bottom + vgap * 2

            for (i in 0 until target.componentCount) {
                val m = target.getComponent(i)
                if (!m.isVisible) continue
                val d = if (preferred) m.preferredSize else m.minimumSize
                if (rowW > 0 && rowW + hgap + d.width > available) {
                    totalH += rowH + vgap
                    rowW = 0; rowH = 0
                }
                if (rowW > 0) rowW += hgap
                rowW += d.width
                rowH = maxOf(rowH, d.height)
            }
            totalH += rowH

            return Dimension(maxWidth, totalH)
        }
    }
}
