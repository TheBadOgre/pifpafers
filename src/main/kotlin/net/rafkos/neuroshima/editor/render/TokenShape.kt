package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.TokenKind
import java.awt.BasicStroke
import java.awt.Polygon
import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Ellipse2D

/**
 * Describes the geometric boundary of a token type.
 * Used for rendering clips and printing bleed areas.
 */
interface TokenShape {
    /** Tight clip shape — all pixels outside this are invisible during normal rendering. */
    fun clipShape(): Shape
    /** Clip shape expanded outward by bleedPx — used for printing bleed area. */
    fun bleedShape(bleedPx: Int = BLEED_PX): Shape
    /** Tight bounding box of clipShape(), used for thumbnail scaling. */
    val boundingX0: Int
    val boundingY0: Int
    val boundingW: Int
    val boundingH: Int

    companion object {
        const val BLEED_PX = 70
        fun forKind(kind: TokenKind): TokenShape = when (kind) {
            TokenKind.UNIT -> UnitTokenShape
            TokenKind.MODIFIER -> ModifierTokenShape
        }
    }
}

/**
 * Flat-top hexagon. Canvas 1044×902, center (522, 451).
 * Vertices: (303,72) (741,72) (960,450) (741,830) (303,830) (84,450)
 */
object UnitTokenShape : TokenShape {
    val XS = intArrayOf(303, 741, 960, 741, 303, 84)
    val YS = intArrayOf(72,  72,  450, 830, 830, 450)

    override val boundingX0 = 84
    override val boundingY0 = 72
    override val boundingW  = 876  // 960 - 84
    override val boundingH  = 758  // 830 - 72

    override fun clipShape(): Shape = Polygon(XS.copyOf(), YS.copyOf(), 6)

    override fun bleedShape(bleedPx: Int): Shape {
        val hexArea = Area(clipShape())
        val stroke = BasicStroke(bleedPx * 2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        hexArea.add(Area(stroke.createStrokedShape(clipShape())))
        return hexArea
    }
}

/**
 * Circle centered at (522, 451), diameter 440px (= hex top-edge length).
 */
object ModifierTokenShape : TokenShape {
    const val CENTER_X = 522.0
    const val CENTER_Y = 451.0
    const val RADIUS   = 220.0

    override val boundingX0 = 302   // 522 - 220
    override val boundingY0 = 231   // 451 - 220
    override val boundingW  = 440
    override val boundingH  = 440

    override fun clipShape(): Shape =
        Ellipse2D.Double(CENTER_X - RADIUS, CENTER_Y - RADIUS, RADIUS * 2, RADIUS * 2)

    override fun bleedShape(bleedPx: Int): Shape {
        val r = RADIUS + bleedPx
        return Ellipse2D.Double(CENTER_X - r, CENTER_Y - r, r * 2, r * 2)
    }
}
