package net.rafkos.neuroshima.editor.render

import net.rafkos.neuroshima.editor.model.LayerProperties
import java.awt.geom.AffineTransform

object AffineBuilder {
    fun build(
        props: LayerProperties,
        canvasCenterX: Double,
        canvasCenterY: Double,
        imageWidth: Int,
        imageHeight: Int,
    ): AffineTransform {
        val t = AffineTransform()
        t.translate(canvasCenterX + props.offsetX, canvasCenterY + props.offsetY)
        t.rotate(Math.toRadians(props.rotation.toDouble()))
        t.scale(props.scale.toDouble(), props.scale.toDouble())
        t.translate(-imageWidth / 2.0, -imageHeight / 2.0)
        return t
    }
}
