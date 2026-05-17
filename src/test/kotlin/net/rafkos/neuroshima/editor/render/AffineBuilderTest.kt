package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.geom.Point2D

class AffineBuilderTest {

    private fun apply(transform: java.awt.geom.AffineTransform, x: Double, y: Double): Point2D {
        val out = Point2D.Double()
        transform.transform(Point2D.Double(x, y), out)
        return out
    }

    @Test
    fun `identity props place image center at canvas center`() {
        val canvasCenter = Point2D.Double(245.0, 245.0)
        val xform = AffineBuilder.build(
            props = LayerProperties(),
            canvasCenterX = canvasCenter.x,
            canvasCenterY = canvasCenter.y,
            imageWidth = 100,
            imageHeight = 100,
        )
        val mapped = apply(xform, 50.0, 50.0) // image center
        assertEquals(245.0, mapped.x, 1e-6)
        assertEquals(245.0, mapped.y, 1e-6)
    }

    @Test
    fun `offset shifts image center`() {
        val xform = AffineBuilder.build(
            props = LayerProperties(offsetX = 10, offsetY = -20),
            canvasCenterX = 0.0, canvasCenterY = 0.0,
            imageWidth = 40, imageHeight = 40,
        )
        val mapped = apply(xform, 20.0, 20.0) // image center
        assertEquals(10.0, mapped.x, 1e-6)
        assertEquals(-20.0, mapped.y, 1e-6)
    }

    @Test
    fun `scale halves image extent`() {
        val xform = AffineBuilder.build(
            props = LayerProperties(scale = 0.5f),
            canvasCenterX = 0.0, canvasCenterY = 0.0,
            imageWidth = 100, imageHeight = 100,
        )
        val mapped = apply(xform, 100.0, 100.0) // image bottom-right (offset +50, +50 in image space)
        assertEquals(25.0, mapped.x, 1e-6)
        assertEquals(25.0, mapped.y, 1e-6)
    }

    @Test
    fun `rotation 90 degrees rotates around canvas center`() {
        val xform = AffineBuilder.build(
            props = LayerProperties(rotation = 90f),
            canvasCenterX = 0.0, canvasCenterY = 0.0,
            imageWidth = 100, imageHeight = 100,
        )
        // image (100, 50) is right-middle; after 90 deg CW (Java is CW in screen coords) -> (0, +50)
        val mapped = apply(xform, 100.0, 50.0)
        assertEquals(0.0, mapped.x, 1e-6)
        assertEquals(50.0, mapped.y, 1e-6)
    }
}
