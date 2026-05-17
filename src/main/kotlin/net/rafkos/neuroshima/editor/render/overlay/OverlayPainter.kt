package net.rafkos.neuroshima.editor.render.overlay

import net.rafkos.neuroshima.editor.model.TokenKind
import net.rafkos.neuroshima.editor.render.ModifierTokenShape
import net.rafkos.neuroshima.editor.render.UnitTokenShape
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Point2D

/** Paints overlay decorations (bleed strip + guide lines) onto the logical 1044×902 canvas. */
interface OverlayPainter {
    /** g2 must already be in logical canvas coordinates. Clip is caller-managed. */
    fun paint(g2: Graphics2D)

    companion object {
        fun forKind(kind: TokenKind): OverlayPainter = when (kind) {
            TokenKind.UNIT     -> UnitOverlayPainter
            TokenKind.MODIFIER -> ModifierOverlayPainter
        }
    }
}

/** Unit token overlay: red bleed strip, hex symmetry guide lines, yellow registration circles. */
object UnitOverlayPainter : OverlayPainter {
    private const val CX = 522.0
    private const val CY = 451.0

    // Yellow circle: center 155px from each hex vertex toward (CX,CY), radius=82.5
    private val yellowCenters = listOf(
        Point2D.Double(380.6, 206.3),
        Point2D.Double(663.4, 206.3),
        Point2D.Double(805.0, 451.0),
        Point2D.Double(663.4, 695.7),
        Point2D.Double(380.6, 695.7),
        Point2D.Double(239.0, 451.0),
    )
    private const val YELLOW_R = 82.5

    // Hex symmetry-axis diagonal unit vectors (upper-right to lower-left, lower-right to upper-left)
    //   axis2 direction: (193.5-850.5, 640-261) = (-657, 379), |v|≈758.5 → unit(-0.8665, 0.4998)
    //   axis3 direction: (193.5-850.5, 261-640) = (-657,-379), |v|≈758.5 → unit(-0.8665,-0.4998)
    private const val DX = 0.8665
    private const val DY = 0.4998
    private const val EXT = 1200.0

    override fun paint(g2: Graphics2D) {
        val savedComposite = g2.composite
        val savedStroke    = g2.stroke
        val savedColor     = g2.color

        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)

        // --- Red bleed strip ---
        val bleedArea = Area(UnitTokenShape.bleedShape())
        bleedArea.subtract(Area(UnitTokenShape.clipShape()))
        g2.color = Color(220, 30, 30)
        g2.fill(bleedArea)

        // --- Blue guide lines ---
        g2.color = Color(0, 80, 200)
        g2.stroke = BasicStroke(2f)
        // Vertical (hex symmetry axis through top/bottom edge midpoints)
        g2.draw(Line2D.Double(CX, CY - EXT, CX, CY + EXT))
        // Hex symmetry axis 2: upper-right ↔ lower-left
        g2.draw(Line2D.Double(CX + EXT * DX, CY - EXT * DY, CX - EXT * DX, CY + EXT * DY))
        // Hex symmetry axis 3: lower-right ↔ upper-left
        g2.draw(Line2D.Double(CX + EXT * DX, CY + EXT * DY, CX - EXT * DX, CY - EXT * DY))
        // Horizontal (additional, through left/right vertices)
        g2.draw(Line2D.Double(CX - EXT, CY, CX + EXT, CY))

        // --- Yellow registration circles ---
        g2.color = Color(240, 200, 0)
        g2.stroke = BasicStroke(1.5f)
        for (c in yellowCenters) {
            g2.draw(Ellipse2D.Double(c.x - YELLOW_R, c.y - YELLOW_R, YELLOW_R * 2, YELLOW_R * 2))
        }

        g2.composite = savedComposite
        g2.stroke    = savedStroke
        g2.color     = savedColor
    }
}

/** Modifier token overlay: red bleed strip + horizontal/vertical center lines. */
object ModifierOverlayPainter : OverlayPainter {
    private const val CX  = ModifierTokenShape.CENTER_X
    private const val CY  = ModifierTokenShape.CENTER_Y
    private const val EXT = 1200.0

    override fun paint(g2: Graphics2D) {
        val savedComposite = g2.composite
        val savedStroke    = g2.stroke
        val savedColor     = g2.color

        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)

        // --- Red bleed strip ---
        val bleedArea = Area(ModifierTokenShape.bleedShape())
        bleedArea.subtract(Area(ModifierTokenShape.clipShape()))
        g2.color = Color(220, 30, 30)
        g2.fill(bleedArea)

        // --- Blue guide lines (vertical + horizontal only) ---
        g2.color = Color(0, 80, 200)
        g2.stroke = BasicStroke(2f)
        g2.draw(Line2D.Double(CX, CY - EXT, CX, CY + EXT))
        g2.draw(Line2D.Double(CX - EXT, CY, CX + EXT, CY))

        g2.composite = savedComposite
        g2.stroke    = savedStroke
        g2.color     = savedColor
    }
}
