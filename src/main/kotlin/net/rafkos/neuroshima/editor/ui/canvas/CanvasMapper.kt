package net.rafkos.neuroshima.editor.ui.canvas

import java.awt.Point
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D

const val LOGICAL_CENTER: Double = 245.0   // = LOGICAL_TOKEN_SIZE_PX / 2.0

class CanvasMapper(
    private val widthSupplier: () -> Int,
    private val heightSupplier: () -> Int,
    private val zoomSupplier: () -> Float,
    private val panXSupplier: () -> Int,
    private val panYSupplier: () -> Int,
) {
    fun logicalToScreen(p: Point2D): Point2D.Double {
        val out = Point2D.Double()
        screenFromLogicalTransform().transform(p, out)
        return out
    }

    fun screenToLogical(p: Point): Point2D.Double {
        val out = Point2D.Double()
        screenFromLogicalTransform().createInverse().transform(
            Point2D.Double(p.x.toDouble(), p.y.toDouble()), out
        )
        return out
    }

    private fun screenFromLogicalTransform(): AffineTransform {
        val t = AffineTransform()
        t.translate(widthSupplier() / 2.0 + panXSupplier(), heightSupplier() / 2.0 + panYSupplier())
        t.scale(zoomSupplier().toDouble(), zoomSupplier().toDouble())
        t.translate(-LOGICAL_CENTER, -LOGICAL_CENTER)
        return t
    }
}
