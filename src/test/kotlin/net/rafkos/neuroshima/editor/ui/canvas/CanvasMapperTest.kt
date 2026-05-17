package net.rafkos.neuroshima.editor.ui.canvas

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.geom.Point2D

class CanvasMapperTest {

    private fun mapper(width: Int, height: Int, zoom: Float, panX: Int, panY: Int): CanvasMapper {
        return CanvasMapper(
            widthSupplier = { width },
            heightSupplier = { height },
            zoomSupplier = { zoom },
            panXSupplier = { panX },
            panYSupplier = { panY },
        )
    }

    @Test
    fun `screenToLogical inverse of logicalToScreen across zoom and pan`() {
        val m = mapper(800, 600, zoom = 1.5f, panX = 30, panY = -20)
        val logical = Point2D.Double(120.0, 250.0)
        val screen = m.logicalToScreen(logical)
        val back = m.screenToLogical(Point(screen.x.toInt(), screen.y.toInt()))
        assertEquals(logical.x, back.x, 1.0)
        assertEquals(logical.y, back.y, 1.0)
    }

    @Test
    fun `center of canvas at zoom 1 no pan maps to logical center`() {
        val m = mapper(1044, 902, zoom = 1f, panX = 0, panY = 0)
        val p = m.screenToLogical(Point(522, 451))
        assertEquals(522.0, p.x, 0.001)
        assertEquals(451.0, p.y, 0.001)
    }
}
